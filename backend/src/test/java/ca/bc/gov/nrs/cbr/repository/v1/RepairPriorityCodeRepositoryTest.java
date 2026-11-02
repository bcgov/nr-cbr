package ca.bc.gov.nrs.cbr.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeOrderEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * The Priority list's JPQL against a real database — H2, so a test that the join resolves and
 * orders as legacy's {@code FIND_REPAIR_PRIORITIES} does, not of Oracle. See
 * {@link CrossingSiteStatusCodeRepositoryTest} for the schema setup.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class RepairPriorityCodeRepositoryTest {

  @Autowired
  private RepairPriorityCodeRepository repository;

  @Autowired
  private EntityManager entityManager;

  private void given(String code, Integer rank, LocalDateTime expiry) {
    entityManager.persist(RepairPriorityCodeEntity.builder()
        .repairPriorityCode(code)
        .description(code)
        .effectiveDate(LocalDateTime.now().minusYears(20))
        .expiryDate(expiry)
        .build());
    if (rank != null) {
      entityManager.persist(RepairPriorityCodeOrderEntity.builder()
          .repairPriorityCodeOrderId(rank)
          .repairPriorityCode(code)
          .repairPriorityCodeOrder(rank)
          .build());
    }
  }

  private void given(String code, Integer rank) {
    given(code, rank, LocalDateTime.now().plusYears(10));
  }

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM RepairPriorityCodeOrderEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM RepairPriorityCodeEntity").executeUpdate();
  }

  @Test
  @DisplayName("lists the priorities by rank, not by code")
  void ordersByRank() {
    given("H", 2);
    given("U", 1);
    given("L", 4);
    given("M", 3);

    assertThat(repository.findAllCurrent())
        .extracting(RepairPriorityCodeEntity::getRepairPriorityCode)
        .containsExactly("U", "H", "M", "L");
  }

  @Test
  @DisplayName("leaves out a priority with no rank, and an expired one")
  void omitsUnrankedAndExpired() {
    given("H", 1);
    given("X", null);
    given("OLD", 2, LocalDateTime.now().minusDays(1));

    assertThat(repository.findAllCurrent())
        .extracting(RepairPriorityCodeEntity::getRepairPriorityCode)
        .containsExactly("H");
  }
}
