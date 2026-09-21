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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Pins the search contract and its gate.
 *
 * <p>The tests worth having are the ones that would otherwise fail silently: an unguarded endpoint
 * keeps working; criteria bound without {@code @Valid} turn a misformatted month into a filter that
 * is quietly dropped rather than a 400, widening the search instead of refusing it; and a delete
 * whose {@code @PreAuthorize} slipped to {@link CbrAuthorities#READ} would leave every reader able
 * to destroy inspections, with nothing failing and nothing to notice.
 *
 * <p>The contrast between the two gates is the point of testing them together — the same reasoning
 * {@link SiteApiEndpointTest} gives for its pair.
 */
class InspectionApiEndpointTest {

  private static Method searchInspections() {
    return method("searchInspections");
  }

  private static Method deleteInspection() {
    return method("deleteInspection");
  }

  private static Method method(String name) {
    return Arrays.stream(InspectionApiEndpoint.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(name))
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

  @Test
  @DisplayName("delete is DELETE /api/v1/inspections/{inspectionId}")
  void deleteIsMapped() {
    assertThat(deleteInspection().getAnnotation(DeleteMapping.class).value())
        .containsExactly("/{inspectionId}");
  }

  @Test
  @DisplayName("delete is gated on DESTRUCTIVE, not on READ")
  void deleteRequiresDestructive() {
    // The one operation on this endpoint that destroys data, and it takes an inspection's whole
    // audit trail with it. A @PreAuthorize widened to READ here would leave every role that can
    // search able to delete, and nothing would fail.
    PreAuthorize preAuthorize = deleteInspection().getAnnotation(PreAuthorize.class);

    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo(CbrAuthorities.DESTRUCTIVE);
    assertThat(preAuthorize.value()).isNotEqualTo(CbrAuthorities.READ);
  }

  @Test
  @DisplayName("searching and deleting do not share a gate")
  void theTwoOperationsAreGatedDifferently() {
    // Legacy gates them on different privileges — /showInspectionSearch, which every reader holds,
    // and /deleteInspection, which two roles hold. Collapsing them to one gate in either direction
    // is a security change disguised as a tidy-up.
    assertThat(searchInspections().getAnnotation(PreAuthorize.class).value())
        .isNotEqualTo(deleteInspection().getAnnotation(PreAuthorize.class).value());
  }
}
