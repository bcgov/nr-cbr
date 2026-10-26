package ca.bc.gov.nrs.cbr.client;

import ca.bc.gov.nrs.cbr.configuration.ClamAvProperties;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * clamd over raw TCP — its own protocol, not HTTP. Copied from nr-frep's, itself nr-fspts's.
 *
 * <p>A scan is clamd's {@code INSTREAM}: {@code zINSTREAM\0} (the {@code z} selects
 * null-terminated framing), the file in chunks each led by its length as a 4-byte big-endian int,
 * a zero length to end, then a reply read up to its {@code \0}: {@code stream: OK},
 * {@code stream: <signature> FOUND}, or one ending {@code ERROR}.
 *
 * <p>Never throws: a connection that fails or times out is a {@link ScanResult.Status#ERROR}
 * result. What that means for the upload is {@code VirusScanner}'s to decide.
 */
@Component
public class ClamAvClient {

  private static final Logger log = LoggerFactory.getLogger(ClamAvClient.class);

  private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PING = "zPING\0".getBytes(StandardCharsets.US_ASCII);
  /** clamd's own default stream chunk; well under its {@code StreamMaxLength}. */
  static final int CHUNK_SIZE = 8192;

  private final String host;
  private final int port;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;

  public ClamAvClient(ClamAvProperties properties) {
    this.host = properties.host();
    this.port = properties.port();
    this.connectTimeoutMillis = Math.toIntExact(properties.connectTimeout().toMillis());
    this.readTimeoutMillis = Math.toIntExact(properties.readTimeout().toMillis());
  }

  /** clamd's verdict on {@code content}. */
  public ScanResult scan(byte[] content) {
    try (Socket socket = connect()) {
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      out.write(INSTREAM);
      for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
        int length = Math.min(CHUNK_SIZE, content.length - offset);
        out.writeInt(length);
        out.write(content, offset, length);
      }
      out.writeInt(0);
      out.flush();
      return parse(reply(socket.getInputStream()));
    } catch (IOException | IllegalArgumentException failed) {
      // IllegalArgumentException: no host or a port out of range — still no verdict. Logged here,
      // where it is the connection's failure; VirusScanner logs no reply text.
      log.warn("clamd could not scan at {}: {}", address(), failed.toString());
      return ScanResult.error(failed.getClass().getSimpleName() + ": " + failed.getMessage());
    }
  }

  /** Whether clamd answers {@code PING} with {@code PONG}. */
  public boolean ping() {
    try (Socket socket = connect()) {
      socket.getOutputStream().write(PING);
      socket.getOutputStream().flush();
      return "PONG".equals(reply(socket.getInputStream()));
    } catch (IOException | IllegalArgumentException failed) {
      log.warn("clamd did not answer PING at {}: {}", address(), failed.getMessage());
      return false;
    }
  }

  /** {@code host:port}, for the log. */
  public String address() {
    return host + ":" + port;
  }

  private Socket connect() throws IOException {
    Socket socket = new Socket();
    try {
      socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
      socket.setSoTimeout(readTimeoutMillis);
      return socket;
    } catch (IOException | IllegalArgumentException failed) {
      socket.close();
      throw failed;
    }
  }

  /** Up to the {@code \0}, or the end of the stream. */
  private static String reply(InputStream in) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (int b = in.read(); b > 0; b = in.read()) {
      bytes.write(b);
    }
    return bytes.toString(StandardCharsets.US_ASCII).trim();
  }

  static ScanResult parse(String reply) {
    if (reply.isEmpty()) {
      return ScanResult.error("clamd sent no reply");
    }
    if (reply.endsWith("ERROR")) {
      return ScanResult.error(reply);
    }
    if (reply.endsWith("OK")) {
      return ScanResult.clean();
    }
    if (reply.endsWith("FOUND")) {
      // "stream: Win.Test.EICAR_HDB-1 FOUND"
      String signature = reply.substring(reply.indexOf(':') + 1, reply.length() - "FOUND".length())
          .trim();
      return ScanResult.infected(signature.isEmpty() ? reply : signature, reply);
    }
    return ScanResult.error("clamd's reply was not understood: " + reply);
  }
}
