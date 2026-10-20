package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Pins the reports' contract, and that every report is guarded — iterating over the declared
 * methods, so a report added later without a {@code @PreAuthorize} fails the build.
 */
class ReportApiEndpointTest {

  @Test
  @DisplayName("is published under the versioned API path")
  void mappedUnderApiV1() {
    assertThat(ReportApiEndpoint.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/reports");
  }

  @Test
  @DisplayName("every report is a GET gated on READ, as legacy's /show…Report privileges are")
  void everyReportRequiresRead() {
    assertThat(ReportApiEndpoint.class.getDeclaredMethods()).isNotEmpty().allSatisfy(method -> {
      assertThat(method.getAnnotation(GetMapping.class)).as(method.getName()).isNotNull();
      assertThat(method.getAnnotation(PreAuthorize.class).value()).as(method.getName())
          .isEqualTo(CbrAuthorities.READ);
    });
  }

  @Test
  @DisplayName("serves the Structure Inventory – Summary at its own path")
  void structureInventorySummaryPath() throws NoSuchMethodException {
    Method method = ReportApiEndpoint.class.getMethod("structureInventorySummary",
        ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria.class,
        ca.bc.gov.nrs.cbr.struct.v1.ReportFormat.class);

    assertThat(method.getAnnotation(GetMapping.class).value())
        .containsExactly("/structure-inventory-summary");
  }
}
