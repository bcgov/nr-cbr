package ca.bc.gov.nrs.cbr.struct.v1;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The two rules the criteria object carries: what a month looks like, and what "empty" means.
 *
 * <p>Both exist because the frontend is not the only caller. The month pattern is paired with
 * {@code MONTH_PATTERN} in {@code InspectionSearch/validation.ts} and must say the same thing; the
 * emptiness rule is what stops a bare request returning every inspection ever recorded.
 */
class InspectionSearchCriteriaTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private static boolean monthIsValid(String value) {
    return VALIDATOR
        .validate(InspectionSearchCriteria.builder().inspectionDateStart(value).build())
        .isEmpty();
  }

  @Nested
  @DisplayName("the month bounds are validated as yyyy/mm")
  class MonthValidation {

    @ParameterizedTest
    @ValueSource(strings = {"2026/01", "2026/1", "2026/12", "1999/09"})
    @DisplayName("accepts a month, with or without a leading zero")
    void acceptsMonths(String value) {
      // A one-digit month is accepted because legacy's SimpleDateFormat("yyyy/MM") accepts it, and
      // so does Oracle's TO_DATE(:1, 'yyyy/MM').
      assertThat(monthIsValid(value)).isTrue();
    }

    @Test
    @DisplayName("accepts an untouched field, which arrives as an empty string rather than absent")
    void acceptsEmpty() {
      assertThat(monthIsValid("")).isTrue();
      assertThat(monthIsValid(null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01", "2026/13", "2026/0", "26/01", "2026", "2026/01/15", "june"})
    @DisplayName("rejects anything that is not a month")
    void rejectsNonMonths(String value) {
      assertThat(monthIsValid(value)).isFalse();
    }

    @Test
    @DisplayName("names the field it rejected")
    void namesTheField() {
      assertThat(VALIDATOR.validate(
          InspectionSearchCriteria.builder().inspectionDateEnd("2026-01").build()))
          .singleElement()
          .satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("inspectionDateEnd");
            assertThat(violation.getMessage()).isEqualTo("Enter a month as yyyy/mm, e.g. 2026/01");
          });
    }
  }

  @Nested
  @DisplayName("emptiness decides whether the search runs at all")
  class Emptiness {

    @Test
    @DisplayName("nothing set is empty")
    void nothingSetIsEmpty() {
      assertThat(InspectionSearchCriteria.builder().build().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("sortBy is not a criterion")
    void sortByIsNotACriterion() {
      assertThat(InspectionSearchCriteria.builder()
          .sortBy(InspectionSearchCriteria.STRUCTURE_ID_DATE_SORT).build().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("structures at previous sites is not a criterion either")
    void findMovedStructuresIsNotACriterion() {
      // It is not a filter — it changes what Site # means, so on its own it narrows nothing. Legacy
      // lets a form holding only this tick past its guard and then runs no query at all, leaving
      // the user with no error, no results and no reason.
      assertThat(InspectionSearchCriteria.builder().findMovedStructures(true).build().isEmpty())
          .isTrue();
    }

    @Test
    @DisplayName("a blank string is not a criterion")
    void blanksAreNotCriteria() {
      assertThat(InspectionSearchCriteria.builder().siteId("   ").inspectorName("").build()
          .isEmpty()).isTrue();
    }

    @Test
    @DisplayName("any real filter makes it non-empty, including a toggle that is one")
    void realCriteriaCount() {
      assertThat(InspectionSearchCriteria.builder().siteId("1234").build().isEmpty()).isFalse();
      assertThat(InspectionSearchCriteria.builder().inspectionDateStart("2026/01").build()
          .isEmpty()).isFalse();
      assertThat(InspectionSearchCriteria.builder().closeProximity(true).build().isEmpty())
          .isFalse();
      assertThat(InspectionSearchCriteria.builder().mostRecentInspections(true).build().isEmpty())
          .isFalse();
      assertThat(InspectionSearchCriteria.builder().findChangedReviewed(true).build().isEmpty())
          .isFalse();
    }
  }
}
