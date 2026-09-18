package ca.bc.gov.nrs.cbr.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * A derived query rather than JPQL, so what this pins is that the entity's column names resolve and
 * that the two decisions taken in the repository hold: an ordering the legacy procedure does not
 * have, and no date filter.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class StrctreInspectionTypeCodeRepositoryTest {

  @Autowired
  private StrctreInspectionTypeCodeRepository repository;

  @Autowired
  private EntityManager entityManager;

  private void given(String code, String description, LocalDateTime expiry) {
    entityManager.persist(StrctreInspectionTypeCodeEntity.builder()
        .strctreInspectionTypeCode(code)
        .description(description)
        .effectiveDate(LocalDateTime.now().minusYears(20))
        .expiryDate(expiry)
        .updateTimestamp(LocalDateTime.now())
        .build());
  }

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM StrctreInspectionTypeCodeEntity").executeUpdate();
  }

  @Test
  @DisplayName("orders by description, which the legacy procedure does not do at all")
  void ordersByDescription() {
    // CBR.FIND_INSPECTION_TYPE_CODES has no ORDER BY, so there is no legacy order to preserve —
    // only whatever Oracle happened to return. Sorting is a choice made here, and this pins it.
    given("ROUT", "Routine", LocalDateTime.now().plusYears(10));
    given("DETL", "Detailed", LocalDateTime.now().plusYears(10));

    assertThat(repository.findAllByOrderByDescriptionAsc())
        .extracting(StrctreInspectionTypeCodeEntity::getStrctreInspectionTypeCode)
        .containsExactly("DETL", "ROUT");
  }

  @Test
  @DisplayName("keeps expired types, because inspections still carry them")
  void keepsExpiredTypes() {
    given("OLD", "Retired five years ago", LocalDateTime.now().minusYears(5));

    assertThat(repository.findAllByOrderByDescriptionAsc())
        .extracting(StrctreInspectionTypeCodeEntity::getStrctreInspectionTypeCode)
        .containsExactly("OLD");
  }
}
