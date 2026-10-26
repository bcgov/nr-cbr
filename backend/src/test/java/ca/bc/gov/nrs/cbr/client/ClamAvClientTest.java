package ca.bc.gov.nrs.cbr.client;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.client.ScanResult.Status;
import ca.bc.gov.nrs.cbr.configuration.ClamAvProperties;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** clamd's protocol, against a stand-in clamd on a local port. */
class ClamAvClientTest {

  private ServerSocket clamd;
  /** The command and the streamed file, as the stand-in received them. */
  private final CompletableFuture<String> command = new CompletableFuture<>();
  private final CompletableFuture<byte[]> streamed = new CompletableFuture<>();

  @BeforeEach
  void setUp() throws IOException {
    clamd = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
  }

  @AfterEach
  void tearDown() throws IOException {
    clamd.close();
  }

  /** Answers one connection with {@code reply} and a {@code \0}; null answers nothing. */
  private void answer(String reply) {
    Thread.ofVirtual().start(() -> {
      try (Socket socket = clamd.accept()) {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        String received = untilNul(in);
        command.complete(received);
        if (received.equals("zINSTREAM")) {
          ByteArrayOutputStream file = new ByteArrayOutputStream();
          for (int length = in.readInt(); length > 0; length = in.readInt()) {
            file.write(in.readNBytes(length));
          }
          streamed.complete(file.toByteArray());
        }
        if (reply == null) {
          in.read(); // until the client gives up
          return;
        }
        socket.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
      } catch (IOException ignored) {
        // the client hung up
      }
    });
  }

  private static String untilNul(InputStream in) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (int b = in.read(); b > 0; b = in.read()) {
      bytes.write(b);
    }
    return bytes.toString(StandardCharsets.US_ASCII);
  }

  private ClamAvClient client(String host, int port) {
    return new ClamAvClient(new ClamAvProperties(
        true, host, port, Duration.ofSeconds(1), Duration.ofMillis(300)));
  }

  private ClamAvClient client() {
    return client("127.0.0.1", clamd.getLocalPort());
  }

  @Test
  @DisplayName("streams the file in length-prefixed chunks, and reads OK as clean")
  void clean() throws Exception {
    answer("stream: OK");
    byte[] file = new byte[ClamAvClient.CHUNK_SIZE * 2 + 5];
    for (int i = 0; i < file.length; i++) {
      file[i] = (byte) i;
    }

    assertThat(client().scan(file)).isEqualTo(ScanResult.clean());
    assertThat(command.get(1, TimeUnit.SECONDS)).isEqualTo("zINSTREAM");
    assertThat(streamed.get(1, TimeUnit.SECONDS)).isEqualTo(file);
  }

  @Test
  @DisplayName("reads FOUND as infected, with the virus's name")
  void infected() {
    answer("stream: Win.Test.EICAR_HDB-1 FOUND");

    ScanResult result = client().scan("X5O!P%@AP".getBytes(StandardCharsets.US_ASCII));

    assertThat(result.status()).isEqualTo(Status.INFECTED);
    assertThat(result.signature()).isEqualTo("Win.Test.EICAR_HDB-1");
  }

  @Test
  @DisplayName("has no verdict when clamd errs, says nothing it understands, or nothing at all")
  void noVerdict() {
    answer("INSTREAM size limit exceeded. ERROR");
    assertThat(client().scan(new byte[] {1}).status()).isEqualTo(Status.ERROR);

    answer("stream: maybe");
    assertThat(client().scan(new byte[] {1}).status()).isEqualTo(Status.ERROR);

    answer("");
    assertThat(client().scan(new byte[] {1}).status()).isEqualTo(Status.ERROR);
  }

  @Test
  @DisplayName("has no verdict, and does not throw, when clamd is silent, down or unnamed")
  void unreachable() throws IOException {
    answer(null);
    assertThat(client().scan(new byte[] {1}).detail()).startsWith("SocketTimeoutException");

    int closed = clamd.getLocalPort();
    clamd.close();
    assertThat(client("127.0.0.1", closed).scan(new byte[] {1}).status())
        .isEqualTo(Status.ERROR);
    assertThat(client(null, 3310).scan(new byte[] {1}).status()).isEqualTo(Status.ERROR);
    assertThat(client(null, 3310).ping()).isFalse();
  }

  @Test
  @DisplayName("pings, and is answered PONG")
  void ping() throws Exception {
    answer("PONG");

    assertThat(client().ping()).isTrue();
    assertThat(command.get(1, TimeUnit.SECONDS)).isEqualTo("zPING");
    assertThat(client().address()).isEqualTo("127.0.0.1:" + clamd.getLocalPort());
  }
}
