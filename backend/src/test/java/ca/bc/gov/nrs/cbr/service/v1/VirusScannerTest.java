package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.client.ClamAvClient;
import ca.bc.gov.nrs.cbr.client.ScanResult;
import ca.bc.gov.nrs.cbr.configuration.ClamAvProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What clamd's verdict means for an upload. */
class VirusScannerTest {

  private static final byte[] FILE = {1, 2, 3};

  private final ClamAvClient clamd = mock(ClamAvClient.class);

  private VirusScanner scanner(boolean enabled) {
    return new VirusScanner(clamd, new ClamAvProperties(
        enabled, "clamd", 3310, Duration.ofSeconds(5), Duration.ofSeconds(30)));
  }

  @Test
  @DisplayName("passes a clean file")
  void clean() {
    when(clamd.scan(FILE)).thenReturn(ScanResult.clean());

    assertThat(scanner(true).refusal(FILE)).isEmpty();
  }

  @Test
  @DisplayName("refuses an infected file, naming the virus")
  void infected() {
    when(clamd.scan(FILE)).thenReturn(
        ScanResult.infected("Win.Test.EICAR_HDB-1", "stream: Win.Test.EICAR_HDB-1 FOUND"));

    assertThat(scanner(true).refusal(FILE))
        .contains("This file was rejected: a virus was detected (Win.Test.EICAR_HDB-1).");
  }

  @Test
  @DisplayName("refuses a file clamd could not check: fail-closed")
  void unavailable() {
    when(clamd.scan(FILE)).thenReturn(ScanResult.error("SocketTimeoutException: Read timed out"));

    assertThat(scanner(true).refusal(FILE)).contains(VirusScanner.UNAVAILABLE);
  }

  @Test
  @DisplayName("off, as locally, passes every file without calling clamd")
  void disabled() {
    VirusScanner off = scanner(false);

    assertThat(off.refusal(FILE)).isEmpty();
    off.reportAtStartup();
    verify(clamd, never()).scan(any());
    verify(clamd, never()).ping();
  }

  @Test
  @DisplayName("checks clamd at startup without stopping the app when it does not answer")
  void startup() {
    when(clamd.ping()).thenReturn(false, true);
    VirusScanner scanner = scanner(true);

    assertThatNoException().isThrownBy(scanner::reportAtStartup);
    assertThatNoException().isThrownBy(scanner::reportAtStartup);
    verify(clamd, times(2)).ping();
  }
}
