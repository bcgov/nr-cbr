package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ca.bc.gov.nrs.cbr.configuration.ObjectStorageProperties;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * The object store, against a stand-in for the NRS gateway on a local port: the requests the SDK
 * actually sends, and what each answer means to the caller.
 */
class ObjectStorageServiceTest {

  private static final String NO_SUCH_KEY = """
      <?xml version="1.0" encoding="UTF-8"?>
      <Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message></Error>""";

  private record Received(String method, String path, Headers headers, byte[] body) {}

  private record Answer(int status, String body) {}

  private HttpServer gateway;
  private final List<Received> received = new ArrayList<>();
  private Answer answer;
  private ObjectStorageService storage;

  @BeforeEach
  void setUp() throws IOException {
    gateway = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    gateway.createContext("/", exchange -> {
      received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
          exchange.getRequestHeaders(), exchange.getRequestBody().readAllBytes()));
      byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("ETag", "\"etag\"");
      boolean empty = body.length == 0 || "HEAD".equals(exchange.getRequestMethod());
      exchange.sendResponseHeaders(answer.status(), empty ? -1 : body.length);
      if (!empty) {
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(body);
        }
      }
      exchange.close();
    });
    gateway.start();
    storage = new ObjectStorageService(new ObjectStorageProperties(
        "http://localhost:" + gateway.getAddress().getPort(), "cbr-bucket", "AKID", "SECRET"));
  }

  @AfterEach
  void tearDown() {
    storage.destroy();
    gateway.stop(0);
  }

  private void answer(int status, String body) {
    answer = new Answer(status, body);
  }

  /**
   * The body as sent. Over plain HTTP — this stand-in — the SDK signs the body chunk by chunk
   * ({@code aws-chunked}: {@code <hex length>;chunk-signature=…\r\n<bytes>\r\n}, ending with a
   * zero-length chunk); over HTTPS, the real gateway, it sends the bytes as they are.
   */
  private static byte[] unchunked(Received request) {
    String encoding = request.headers().getFirst("Content-Encoding");
    if (encoding == null || !encoding.contains("aws-chunked")) {
      return request.body();
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] body = request.body();
    int at = 0;
    while (true) {
      int lineEnd = indexOfCrlf(body, at);
      String header = new String(body, at, lineEnd - at, StandardCharsets.US_ASCII);
      int length = Integer.parseInt(header.substring(0, header.indexOf(';')), 16);
      if (length == 0) {
        return bytes.toByteArray();
      }
      bytes.write(body, lineEnd + 2, length);
      at = lineEnd + 2 + length + 2;
    }
  }

  private static int indexOfCrlf(byte[] body, int from) {
    for (int i = from; i < body.length - 1; i++) {
      if (body[i] == '\r' && body[i + 1] == '\n') {
        return i;
      }
    }
    throw new IllegalStateException("No chunk header in the body");
  }

  private Received only() {
    assertThat(received).hasSize(1);
    return received.get(0);
  }

  @Test
  @DisplayName("keys a document's file by its FILE_ID alone, under docs/")
  void fileKey() {
    assertThat(ObjectStorageService.fileKey(42)).isEqualTo("docs/42");
    // Flat: one id is never a folder holding another's.
    assertThat(ObjectStorageService.fileKey(4)).isEqualTo("docs/4");
  }

  @Test
  @DisplayName("puts the file path-style, signed, with its type and no checksum the gateway refuses")
  void puts() {
    answer(200, "");
    byte[] content = "%PDF-1.7 inspection".getBytes(StandardCharsets.UTF_8);

    storage.putObject("docs/42", "application/pdf", content);

    Received put = only();
    assertThat(put.method()).isEqualTo("PUT");
    assertThat(put.path()).isEqualTo("/cbr-bucket/docs/42");
    assertThat(unchunked(put)).isEqualTo(content);
    assertThat(put.headers().getFirst("Content-Type")).isEqualTo("application/pdf");
    assertThat(put.headers().getFirst("Authorization"))
        .startsWith("AWS4-HMAC-SHA256 Credential=AKID/");
    // SDK 2.30+'s default CRC32 checksum, sent as a trailer, is what the NRS gateway rejects.
    assertThat(put.headers().keySet()).noneMatch(name ->
        name.toLowerCase().startsWith("x-amz-checksum")
            || name.equalsIgnoreCase("x-amz-sdk-checksum-algorithm")
            || name.equalsIgnoreCase("x-amz-trailer"));
    assertThat(put.headers().getFirst("x-amz-content-sha256")).doesNotEndWith("-TRAILER");
  }

  @Test
  @DisplayName("reads the file, or nothing when the object is missing")
  void reads() {
    answer(200, "photo bytes");
    assertThat(storage.getObjectBytesIfPresent("docs/42"))
        .contains("photo bytes".getBytes(StandardCharsets.UTF_8));
    assertThat(only().path()).isEqualTo("/cbr-bucket/docs/42");

    answer(404, NO_SUCH_KEY);
    assertThat(storage.getObjectBytesIfPresent("docs/43")).isEmpty();
  }

  @Test
  @DisplayName("lets any failure but a missing object through, never reading it as missing")
  void failsLoudly() {
    answer(500, """
        <?xml version="1.0" encoding="UTF-8"?>
        <Error><Code>InternalError</Code><Message>down</Message></Error>""");
    assertThatThrownBy(() -> storage.getObjectBytesIfPresent("docs/42"))
        .isInstanceOf(S3Exception.class);

    answer(403, """
        <?xml version="1.0" encoding="UTF-8"?>
        <Error><Code>AccessDenied</Code><Message>no</Message></Error>""");
    assertThatThrownBy(() -> storage.objectExists("docs/42")).isInstanceOf(S3Exception.class);
  }

  @Test
  @DisplayName("asks whether the object is there without fetching it")
  void exists() {
    answer(200, "");
    assertThat(storage.objectExists("docs/42")).isTrue();
    assertThat(only().method()).isEqualTo("HEAD");

    answer(404, "");
    assertThat(storage.objectExists("docs/43")).isFalse();
  }

  @Test
  @DisplayName("deletes the object")
  void deletes() {
    answer(204, "");

    storage.deleteObject("docs/42");

    assertThat(only().method()).isEqualTo("DELETE");
    assertThat(only().path()).isEqualTo("/cbr-bucket/docs/42");
  }

  @Test
  @DisplayName("deletes the objects only once the rows' delete commits, and only if it does")
  void deletesAfterCommit() {
    answer(204, "");
    TransactionSynchronizationManager.initSynchronization();
    try {
      storage.deleteAfterCommit(List.of("docs/1", "docs/2"));
      assertThat(received).isEmpty();

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    assertThat(received).extracting(Received::method, Received::path).containsExactly(
        tuple("DELETE", "/cbr-bucket/docs/1"), tuple("DELETE", "/cbr-bucket/docs/2"));
  }

  @Test
  @DisplayName("deletes at once outside a transaction, and logs a delete that fails")
  void deletesNow() {
    answer(500, """
        <?xml version="1.0" encoding="UTF-8"?>
        <Error><Code>InternalError</Code><Message>down</Message></Error>""");

    storage.deleteAfterCommit(List.of("docs/1"));

    assertThat(received).extracting(Received::method).contains("DELETE");
  }

  @Test
  @DisplayName("deletes an object just put when its rows roll back, and keeps it when they commit")
  void deletesOnRollback() {
    answer(204, "");
    for (int status : new int[] {
        TransactionSynchronization.STATUS_COMMITTED, TransactionSynchronization.STATUS_ROLLED_BACK}) {
      TransactionSynchronizationManager.initSynchronization();
      try {
        storage.deleteOnRollback("docs/" + status);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCompletion(status));
      } finally {
        TransactionSynchronizationManager.clearSynchronization();
      }
    }

    assertThat(only().path())
        .isEqualTo("/cbr-bucket/docs/" + TransactionSynchronization.STATUS_ROLLED_BACK);
  }

  @Test
  @DisplayName("starts without settings, as locally, but refuses every call")
  void unconfigured() {
    ObjectStorageService unset =
        new ObjectStorageService(new ObjectStorageProperties("", null, null, null));

    assertThatThrownBy(() -> unset.getObjectBytesIfPresent("docs/42"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OBJECT_STORAGE_HOST");
    unset.destroy();
  }

  @Test
  @DisplayName("is configured only when all four are set, and prints none of them")
  void properties() {
    ObjectStorageProperties set = new ObjectStorageProperties("https://h", "b", "AKID", "SECRET");

    assertThat(set.configured()).isTrue();
    assertThat(new ObjectStorageProperties("https://h", "b", "AKID", " ").configured()).isFalse();
    assertThat(set).hasToString("ObjectStorageProperties[configured=true]");
  }
}
