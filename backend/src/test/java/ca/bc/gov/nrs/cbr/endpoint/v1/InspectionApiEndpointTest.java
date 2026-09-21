package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Pins the search contract and its gate.
 *
 * <p>Thinner than {@link SiteApiEndpointTest}, because there is one operation and it is a read. The
 * tests worth having are the ones that would otherwise fail silently: an unguarded endpoint keeps
 * working, and criteria bound without {@code @Valid} turn a misformatted month into a filter that is
 * quietly dropped rather than a 400 — which widens the search instead of refusing it.
 */
class InspectionApiEndpointTest {

  private static Method searchInspections() {
    return Arrays.stream(InspectionApiEndpoint.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals("searchInspections"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("is GET /api/v1/inspections/search")
  void isMappedAsAGet() {
    assertThat(InspectionApiEndpoint.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/inspections");
    assertThat(searchInspections().getAnnotation(GetMapping.class).value())
        .containsExactly("/search");
  }

  @Test
  @DisplayName("is gated on READ, the legacy /showInspectionSearch privilege")
  void requiresRead() {
    PreAuthorize preAuthorize = searchInspections().getAnnotation(PreAuthorize.class);

    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo(CbrAuthorities.READ);
  }

  @Test
  @DisplayName("validates the criteria rather than letting a bad month through")
  void validatesTheCriteria() {
    // Without @Valid a misformatted month is not rejected — it falls out of the WHERE clause in
    // InspectionSearchSpecifications and the search runs wider than the user asked for.
    Method method = searchInspections();

    assertThat(method.getParameters()[0].getType()).isEqualTo(InspectionSearchCriteria.class);
    assertThat(method.getParameters()[0].getAnnotation(Valid.class))
        .as("criteria are bound without @Valid")
        .isNotNull();
  }
}
