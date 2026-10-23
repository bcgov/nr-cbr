package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.client.ClamAvClient;
import ca.bc.gov.nrs.cbr.client.ScanResult;
import ca.bc.gov.nrs.cbr.configuration.ClamAvProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Whether an uploaded file may be stored, by clamd's verdict on it — copied from nr-frep's.
 *
 * <p>Fail-closed, with no switch to change it: a file clamd could not check is refused as surely
 * as an infected one, so nothing unscanned is ever stored. Only files users upload are scanned;
 * what CBR makes itself, as the inspection's audit PDF, is not.
 */
@Service
public class VirusScanner {

  private static final Logger log = LoggerFactory.getLogger(VirusScanner.class);

  public static final String UNAVAILABLE =
      "The virus scanner could not check this file. Try again shortly.";

  private final ClamAvClient clamd;
  private final boolean enabled;

  public VirusScanner(ClamAvClient clamd, ClamAvProperties properties) {
    this.clamd = clamd;
    this.enabled = properties.enabled();
  }

  /**
   * Says at startup whether clamd can be reached, so a wrong host or a missing network policy in
   * bd657f shows in the log before the first upload is refused. Never stops the app.
   */
  @EventListener(ApplicationReadyEvent.class)
  void reportAtStartup() {
    if (!enabled) {
      log.warn("Virus scanning is OFF (cbr.clamav.enabled=false): uploads are stored unscanned");
    } else if (clamd.ping()) {
      log.info("Virus scanning is on: clamd answers at {}", clamd.address());
    } else {
      log.warn("Virus scanning is on but clamd does not answer at {}: every upload will be "
          + "refused until it does. Check the host and bd657f's ingress policy for this "
          + "namespace.", clamd.address());
    }
  }

  /**
   * Why {@code content} may not be stored, said for the person who uploaded it; empty when it may.
   *
   * <p>One log line a scan, its outcome first, for the audit trail. The file's name is the
   * browser's and is not logged.
   */
  public Optional<String> refusal(byte[] content) {
    if (!enabled) {
      return Optional.empty();
    }
    ScanResult result = clamd.scan(content);
    return switch (result.status()) {
      case CLEAN -> {
        log.info("Virus scan CLEAN: {} bytes", content.length);
        yield Optional.empty();
      }
      case INFECTED -> {
        log.warn("Virus scan INFECTED ({}): {} bytes refused", result.signature(), content.length);
        yield Optional.of(
            "This file was rejected: a virus was detected (" + result.signature() + ").");
      }
      case ERROR -> {
        log.error("Virus scan UNAVAILABLE ({}): {} bytes refused", result.detail(),
            content.length);
        yield Optional.of(UNAVAILABLE);
      }
    };
  }
}
