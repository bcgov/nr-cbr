package ca.bc.gov.nrs.cbr.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassXrefEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Exercises the JPQL against a real database, for the same reasons set out on
 * {@link CrossingSiteStatusCodeRepositoryTest} — that the query parses, that the entity and column
 * names resolve, and that the join and ordering do what the procedure they replace did.
 *
 * <p>The date filter is the part that earns a test of its own here. It is the one code list on the
 * Inspection Search form that has one, so "is it applied" and "is it applied to the right
 * boundary" are both things a reader would otherwise have to take on trust.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class StructureTypeClassCodeRepositoryTest {

  private static final LocalDateTime LONG_AGO = LocalDateTime.now().minusYears(20);
  private static final LocalDateTime FAR_OFF = LocalDateTime.now().plusYears(10);

  @Autowired
  private StructureTypeClassCodeRepository repository;

  @Autowired
  private EntityManager entityManager;

  private void given(String code, String description, Integer displayOrder) {
    given(code, description, displayOrder, LONG_AGO, FAR_OFF);
  }

  private void given(
      String code,
      String description,
      Integer displayOrder,
      LocalDateTime effective,
      LocalDateTime expiry) {
    entityManager.persist(StructureTypeClassCodeEntity.builder()
        .structureTypeClassCode(code)
        .description(description)
        .effectiveDate(effective)
        .expiryDate(expiry)
        .updateTimestamp(LocalDateTime.now())
        .build());
    if (displayOrder != null) {
      entityManager.persist(StructureTypeClassXrefEntity.builder()
          .structureTypeClassCode(code)
          .displayOrder(displayOrder)
          .build());
    }
  }

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM StructureTypeClassXrefEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM StructureTypeClassCodeEntity").executeUpdate();
  }

  @Test
  @DisplayName("returns types in display order, not in key order")
  void ordersByDisplayOrder() {
    given("BRIDGE", "Forest service bridge", 2);
    given("CULVERT", "Forest service culvert", 1);

    assertThat(repository.findAllCurrentInDisplayOrder())
        .extracting(StructureTypeClassCodeEntity::getStructureTypeClassCode)
        .containsExactly("CULVERT", "BRIDGE");
  }

  @Test
  @DisplayName("omits a type that has no display-order row")
  void omitsUnpublishedTypes() {
    // Same rule as the site statuses: the xref is how a code is published to the UI at all, so a
    // code added to the table but not yet to the xref is half-configured, not ready to offer.
    given("BRIDGE", "Forest service bridge", 1);
    given("WIP", "Added to the code table but not to the xref", null);

    assertThat(repository.findAllCurrentInDisplayOrder())
        .extracting(StructureTypeClassCodeEntity::getStructureTypeClassCode)
        .containsExactly("BRIDGE");
  }

  @Test
  @DisplayName("omits expired and not-yet-effective types, unlike the other code lists here")
  void omitsCodesOutsideTheirDateRange() {
    // Legacy's own behaviour, and legacy's own inconsistency: InspectionSearchAction asks for this
    // list with excludeExpired defaulting to true while asking for inspection types and report
    // statuses with it false. See the repository, which explains what that costs.
    given("BRIDGE", "Forest service bridge", 1);
    given("OLD", "Retired last year", 2, LONG_AGO, LocalDateTime.now().minusYears(1));
    given("SOON", "Not effective until next year", 3, LocalDateTime.now().plusYears(1), FAR_OFF);

    assertThat(repository.findAllCurrentInDisplayOrder())
        .extracting(StructureTypeClassCodeEntity::getStructureTypeClassCode)
        .containsExactly("BRIDGE");
  }

  @Test
  @DisplayName("an empty code table is an empty list")
  void toleratesNoRows() {
    assertThat(repository.findAllCurrentInDisplayOrder()).isEmpty();
  }
}
