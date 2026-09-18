package ca.bc.gov.nrs.cbr.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * A derived query rather than JPQL, so what this pins is narrower than
 * {@link CrossingSiteStatusCodeRepositoryTest}: that the entity's column names resolve, and that the
 * absence of a date filter is deliberate rather than forgotten.
 *
 * <p>That second half is the reason this file exists. {@code ACC} is an expired status that still
 * sits on inspections nobody has re-saved, and {@code OFL} is the one the legacy filter drops when
 * it runs — so "no filter" is load-bearing here in a way it is not on the other code lists, and a
 * later change that added one would look like a tidy-up.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class InspectionReportStatusCodeRepositoryTest {

  @Autowired
  private InspectionReportStatusCodeRepository repository;

  @Autowired
  private EntityManager entityManager;

  private void given(String code, String description, LocalDateTime expiry) {
    entityManager.persist(InspectionReportStatusCodeEntity.builder()
        .inspectionReportStatusCode(code)
        .description(description)
        .effectiveDate(LocalDateTime.now().minusYears(20))
        .expiryDate(expiry)
        .updateTimestamp(LocalDateTime.now())
        .build());
  }

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM InspectionReportStatusCodeEntity").executeUpdate();
  }

  @Test
  @DisplayName("orders by description, not by code")
  void ordersByDescription() {
    given("SUB", "Submitted", LocalDateTime.now().plusYears(10));
    given("PRO", "In progress", LocalDateTime.now().plusYears(10));

    assertThat(repository.findAllByOrderByDescriptionAsc())
        .extracting(InspectionReportStatusCodeEntity::getInspectionReportStatusCode)
        .containsExactly("PRO", "SUB");
  }

  @Test
  @DisplayName("keeps ACC and OFL, which is the whole point of not filtering")
  void keepsTheStatusesAFormWouldHide() {
    // ACC is expired and unsettable — saving an inspection that carries it rewrites it to RVD — so
    // it survives only on rows nobody has touched, which is exactly the set someone would search
    // for. OFL is machine-set by the offline checkout, and finding those rows is a thing this
    // screen exists to do.
    given("ACC", "Accepted", LocalDateTime.now().minusYears(5));
    given("OFL", "Offline", LocalDateTime.now().plusYears(10));

    assertThat(repository.findAllByOrderByDescriptionAsc())
        .extracting(InspectionReportStatusCodeEntity::getInspectionReportStatusCode)
        .containsExactlyInAnyOrder("ACC", "OFL");
  }
}
