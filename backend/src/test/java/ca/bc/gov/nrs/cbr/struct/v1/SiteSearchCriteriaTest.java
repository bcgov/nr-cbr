package ca.bc.gov.nrs.cbr.struct.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SiteSearchCriteriaTest {

  private static SiteSearchCriteria empty() {
    return new SiteSearchCriteria(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null);
  }

  @Test
  @DisplayName("carries every criterion the legacy form offers")
  void hasAllSeventeenCriteria() {
    // Field-for-field with site_search.jsp and the frontend's SiteSearchCriteria. A dropped field is
    // a search someone can no longer run, with nothing to notice.
    assertThat(SiteSearchCriteria.class.getRecordComponents()).hasSize(20);
  }

  @Test
  @DisplayName("an unset request is the legacy search-everything case")
  void emptyWhenNothingSet() {
    assertThat(empty().isEmpty()).isTrue();
  }

  @Test
  @DisplayName("blank and whitespace-only values do not count as criteria")
  void blanksAreNotCriteria() {
    // Query strings arrive with empty values for untouched inputs — `?siteId=&crossingName=` — so
    // treating "" as a filter would turn every unfilled box into a predicate.
    SiteSearchCriteria blanks =
        new SiteSearchCriteria(
            "", "  ", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "", false, "");

    assertThat(blanks.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("any single set criterion makes the request non-empty")
  void oneCriterionIsEnough() {
    SiteSearchCriteria withSiteId =
        new SiteSearchCriteria(
            "12345", null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null);

    assertThat(withSiteId.isEmpty()).isFalse();
  }

  @Test
  @DisplayName("a toggled-on boolean filter counts, a toggled-off one does not")
  void booleanFiltersCountOnlyWhenTrue() {
    SiteSearchCriteria incompleteOn =
        new SiteSearchCriteria(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, true, null, null, null);
    SiteSearchCriteria incompleteOff =
        new SiteSearchCriteria(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, false, null, null, null);

    assertThat(incompleteOn.isEmpty()).isFalse();
    assertThat(incompleteOff.isEmpty()).isTrue();
  }

  @Nested
  @DisplayName("the kilometre bounds are validated as decimals")
  class KilometreBounds {

    private static final Validator VALIDATOR =
        Validation.buildDefaultValidatorFactory().getValidator();

    private static SiteSearchCriteria withKilometres(String value) {
      return new SiteSearchCriteria(null, null, null, null, null, null, null, null, null, null,
          value, value, null, value, value, null, null, null, null, null);
    }

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = {"", "0", "5", "12.5", "12.50", "999999", "999999.99"})
    @DisplayName("accepts an unset value and anything NUMBER(8,2) can hold")
    void accepts(String value) {
      // The empty string is the load-bearing case: these arrive from a form, so an untouched field
      // is "" rather than absent, and @Pattern only skips null.
      assertThat(VALIDATOR.validate(withKilometres(value))).isEmpty();
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"abc", "12.345", "1e3", "-5", "1,5", "1234567", " 5", "5 "})
    @DisplayName("rejects what cannot be compared against the column")
    void rejects(String value) {
      // Legacy accepts 1e3 and -5 — new Double(...) parses both — and then matches nothing. Three
      // decimal places and seven integer digits do not fit NUMBER(8,2) at all.
      assertThat(VALIDATOR.validate(withKilometres(value))).hasSize(4);
    }

    @Test
    @DisplayName("names the field that was wrong, not the form")
    void namesTheField() {
      // Legacy raises one page-level message per pair; the point of validating here is that the
      // response says which of the four boxes to fix.
      SiteSearchCriteria criteria = new SiteSearchCriteria(null, null, null, null, null, null, null,
          null, null, null, "oops", null, null, null, null, null, null, null, null, null);

      assertThat(VALIDATOR.validate(criteria))
          .singleElement()
          .satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("kiloStart");
            assertThat(violation.getMessage()).contains("Kilometres must be a number");
          });
    }

    @Test
    @DisplayName("each bound stands alone — no bound depends on another being filled")
    void boundsAreIndependent() {
      // Legacy guards the User Km upper bound with a test on kiloEnd, so "User Kilometres To" on
      // its own is silently dropped. Validation is the first place that asymmetry could creep back.
      SiteSearchCriteria onlyUserKmEnd = new SiteSearchCriteria(null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, "bad", null, null, null, null, null);

      assertThat(VALIDATOR.validate(onlyUserKmEnd))
          .singleElement()
          .satisfies(violation ->
              assertThat(violation.getPropertyPath()).hasToString("userKmEnd"));
    }
  }
}
