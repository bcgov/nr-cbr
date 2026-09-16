package ca.bc.gov.nrs.cbr.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusXrefEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Exercises the JPQL against a real database.
 *
 * <p>H2 rather than Oracle, so this is not a test of Oracle behaviour — it is a test that the query
 * <em>parses</em>, that the entity and column names resolve, and that the join and ordering do what
 * the procedure they replace did. Those are the parts that otherwise fail at pod startup or, worse,
 * silently return the wrong rows.
 *
 * <p>The schema is created from the entities ({@code ddl-auto=create-drop}, overriding the pinned
 * {@code none}), under a {@code THE} schema created by {@code src/test/resources/schema.sql} —
 * without it Hibernate has nowhere to put the tables, because the entities are schema-qualified for
 * the same reason production needs them to be.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class CrossingSiteStatusCodeRepositoryTest {

  @Autowired
  private CrossingSiteStatusCodeRepository repository;

  @Autowired
  private EntityManager entityManager;

  private void given(String code, String description, Integer displayOrder) {
    given(code, description, displayOrder, LocalDateTime.now().plusYears(10));
  }

  private void given(String code, String description, Integer displayOrder, LocalDateTime expiry) {
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode(code)
        .description(description)
        .effectiveDate(LocalDateTime.now().minusYears(20))
        .expiryDate(expiry)
        .updateTimestamp(LocalDateTime.now())
        .build());
    if (displayOrder != null) {
      entityManager.persist(CrossingSiteStatusXrefEntity.builder()
          .crossingSiteStatusCode(code)
          .displayOrder(displayOrder)
          .build());
    }
  }

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CrossingSiteStatusXrefEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteStatusCodeEntity").executeUpdate();
  }

  @Test
  @DisplayName("returns statuses in display order, not in key order")
  void ordersByDisplayOrder() {
    given("ACT", "Active", 2);
    given("DEC", "Decommissioned", 1);

    assertThat(repository.findAllInDisplayOrder())
        .extracting(CrossingSiteStatusCodeEntity::getCrossingSiteStatusCode)
        .containsExactly("DEC", "ACT");
  }

  @Test
  @DisplayName("omits a status that has no display-order row")
  void omitsUnpublishedStatuses() {
    // The inner join is how legacy decides whether a code is published to the UI at all. A left
    // join here would put a half-configured code — added to the code table, not yet added to the
    // xref — in front of users.
    given("ACT", "Active", 1);
    given("WIP", "Added to the code table but not to the xref", null);

    assertThat(repository.findAllInDisplayOrder())
        .extracting(CrossingSiteStatusCodeEntity::getCrossingSiteStatusCode)
        .containsExactly("ACT");
  }

  @Test
  @DisplayName("includes expired statuses, because sites still carry them")
  void includesExpiredStatuses() {
    // Deliberate, and the opposite of what a data-entry dropdown wants: this list populates a
    // search filter, so filtering expired codes out would make older sites unfindable by status.
    given("OLD", "Retired in 2011", 1, LocalDateTime.now().minusYears(1));

    assertThat(repository.findAllInDisplayOrder())
        .extracting(CrossingSiteStatusCodeEntity::getCrossingSiteStatusCode)
        .containsExactly("OLD");
  }

  @Test
  @DisplayName("an empty code table is an empty list")
  void toleratesNoRows() {
    assertThat(repository.findAllInDisplayOrder()).isEmpty();
  }
}
