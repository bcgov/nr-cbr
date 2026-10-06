package ca.bc.gov.nrs.cbr.struct.v1;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What an archive request must carry. */
class StructureArchiveRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("refuses an empty selection — legacy's 'No structures have been selected.'")
  void refusesNone() {
    assertThat(validator.validate(new StructureArchiveRequest(List.of()))).isNotEmpty();
    assertThat(validator.validate(new StructureArchiveRequest(null))).isNotEmpty();
  }

  @Test
  @DisplayName("refuses a null id")
  void refusesNullIds() {
    assertThat(validator.validate(new StructureArchiveRequest(Arrays.asList(1L, null))))
        .isNotEmpty();
  }

  @Test
  @DisplayName("accepts one or more ids")
  void acceptsIds() {
    assertThat(validator.validate(new StructureArchiveRequest(List.of(1L, 2L)))).isEmpty();
  }
}
