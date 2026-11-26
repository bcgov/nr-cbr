package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link StructureInventoryReportSupport#format}: legacy's patterns, its formatters reused. */
class ReportNumberFormatTest {

  @Test
  @DisplayName("formats in legacy's patterns, half-even, blank for none")
  void formats() {
    assertThat(StructureInventoryReportSupport.format(new BigDecimal("1234.55"), "#,##0.0"))
        .isEqualTo("1,234.6");
    assertThat(StructureInventoryReportSupport.format(new BigDecimal("12.25"), "###0.0"))
        .isEqualTo("12.2");
    assertThat(StructureInventoryReportSupport.format(new BigDecimal("7"), "#,##0"))
        .isEqualTo("7");
    assertThat(StructureInventoryReportSupport.format(null, "#,##0")).isEmpty();
  }

  @Test
  @DisplayName("reuses a formatter per thread, and threads formatting at once do not mix them")
  void safeAcrossThreads() throws Exception {
    assertThat(StructureInventoryReportSupport.decimalFormat("#,##0.00"))
        .isSameAs(StructureInventoryReportSupport.decimalFormat("#,##0.00"));

    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> tasks = IntStream.range(0, 8).<Callable<Boolean>>mapToObj(t -> () -> {
        for (int i = 0; i < 5_000; i++) {
          BigDecimal value = BigDecimal.valueOf(t * 100_000L + i, 2);
          String expected = String.format(java.util.Locale.CANADA, "%,.2f", value);
          if (!StructureInventoryReportSupport.format(value, "#,##0.00").equals(expected)) {
            return false;
          }
        }
        return true;
      }).toList();
      for (Future<Boolean> result : pool.invokeAll(tasks)) {
        assertThat(result.get()).isTrue();
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
