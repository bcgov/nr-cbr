package ca.bc.gov.nrs.cbr.struct.v1;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a repair-responsibility request must carry, with legacy's messages. */
class StructureRepairResponsibilityRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private List<String> messages(StructureRepairResponsibilityRequest request) {
    return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
  }

  @Test
  @DisplayName("needs a structure, a maintainer and a location")
  void needsEverything() {
    assertThat(messages(new StructureRepairResponsibilityRequest(List.of(), " ", "")))
        .containsExactlyInAnyOrder(
            "No structures have been selected.",
            "Designated Maintainer is required.",
            "Maintainer Location is required.");
  }

  @Test
  @DisplayName("accepts a complete request")
  void acceptsComplete() {
    assertThat(messages(new StructureRepairResponsibilityRequest(List.of(1L), "00001012", "01")))
        .isEmpty();
  }
}
