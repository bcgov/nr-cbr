package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every template compiles — what the startup compile does on its own thread, run here in the build,
 * so a broken template fails the build rather than a user's first report.
 */
class ReportRendererTest {

  @Test
  @DisplayName("compiles every report template on the classpath")
  void compilesEveryTemplate() {
    assertThat(new ReportRenderer().compileTemplates())
        .contains(StructureInventorySummaryService.TEMPLATE);
  }
}
