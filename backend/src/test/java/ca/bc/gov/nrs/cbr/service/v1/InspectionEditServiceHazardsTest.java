package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.HazardsUpdateRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** An inspection's Hydraulic Hazards, edited on its page — {@link InspectionEditService}. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionEditService.class, InspectionFormService.class})
class InspectionEditServiceHazardsTest {

  private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);

  @Autowired
  private InspectionEditService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\INSPECTOR");
    entityManager.persist(CrossingStructureEntity.builder().crossingStructureId(7L)
        .crossingStructureName("B7").structureTypeClassCode("PB").activeInd("Y")
        .closeProximityInd("N").portableStructureInd("N").build());
    for (String status : List.of("PRO", "SUB", "RVD", "ACC", "REJ")) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status).description(status).effectiveDate(PAST)
          .expiryDate(PAST.plusYears(999)).updateTimestamp(PAST).build());
    }
  }

  private void inspection(long id, String status, BigDecimal storedDepth) {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(7L).inspectionDate(LocalDate.of(2024, 5, 1))
        .channelPwlDepth(storedDepth).build());
    entityManager.persist(InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(id * 10).inspectionId(id).inspectionReportStatusCode(status)
        .entryUserid("IDIR\\X").entryTimestamp(PAST).build());
    entityManager.flush();
    entityManager.clear();
  }

  private static HazardsUpdateRequest hazards(String depth, String width, String clearance) {
    return new HazardsUpdateRequest(metres(depth), metres(width), metres(clearance), true, false,
        true, false, false, "  Debris at the pier.  ");
  }

  private static BigDecimal metres(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private Map<String, String> refused(long id, HazardsUpdateRequest request) {
    try {
      service.updateHazards(id, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("Expected the hazards to be refused");
  }

  @Test
  @DisplayName("saves the measurements, the hazards and the comment, with who and when")
  void saves() {
    inspection(41L, "SUB", null);

    service.updateHazards(41L, hazards("1.5", "12", "3.2"));
    entityManager.flush();
    entityManager.clear();

    StructureInspectionEntity stored = entityManager.find(StructureInspectionEntity.class, 41L);
    assertThat(stored.getChannelPwlDepth()).isEqualByComparingTo("1.5");
    assertThat(stored.getChannelHwlClearance()).isEqualByComparingTo("3.2");
    assertThat(stored.getHighWaterHazardInd()).isEqualTo("Y");
    assertThat(stored.getDebrisHazardInd()).isEqualTo("N");
    assertThat(stored.getScourHazardInd()).isEqualTo("Y");
    assertThat(stored.getHazardComment()).isEqualTo("Debris at the pier.");
    assertThat(stored.getUpdateUserid()).isEqualTo("IDIR\\INSPECTOR");
  }

  @Test
  @DisplayName("lets the measurements wait while In Progress, until one has been saved")
  void inProgress() {
    inspection(41L, "PRO", new BigDecimal("2"));

    service.updateHazards(41L, new HazardsUpdateRequest(new BigDecimal("2"), null, null, null, null,
        null, null, null, null));
    assertThat(refused(41L, hazards(null, null, null))).containsOnly(
        Map.entry("presentWaterDepth", "Estimated Present Water Level Depth is required."));
  }

  @Test
  @DisplayName("needs all three past In Progress, in metres to one decimal, and a short comment")
  void rules() {
    inspection(41L, "SUB", null);

    assertThat(refused(41L, hazards(null, "1.25", "100000"))).containsOnly(
        Map.entry("presentWaterDepth", "Estimated Present Water Level Depth is required."),
        Map.entry("presentWaterWidth", "Estimated Present Water Level Width must be metres "
            + "from 0 to 99999.9, with 1 decimal place at most."),
        Map.entry("highWaterClearance", "Estimated High Water Level Clearance must be metres "
            + "from 0 to 99999.9, with 1 decimal place at most."));
    HazardsUpdateRequest longComment = new HazardsUpdateRequest(BigDecimal.ONE, BigDecimal.ONE,
        BigDecimal.ONE, null, null, null, null, null, "x".repeat(2001));
    assertThat(refused(41L, longComment)).containsOnlyKeys("hazardComment");
  }

  @Test
  @DisplayName("is refused once Reviewed or Accepted, as legacy disables its page — not Rejected")
  void locks() {
    inspection(41L, "RVD", null);
    inspection(42L, "ACC", null);
    inspection(43L, "REJ", null);
    HazardsUpdateRequest request = hazards("1", "2", "3");

    assertThatThrownBy(() -> service.updateHazards(41L, request))
        .isInstanceOf(InspectionLockedException.class)
        .hasMessageContaining("can no longer be changed from its page");
    assertThatThrownBy(() -> service.updateHazards(42L, request))
        .isInstanceOf(InspectionLockedException.class);
    service.updateHazards(43L, request);
    assertThatThrownBy(() -> service.updateHazards(404L, request))
        .isInstanceOf(InspectionNotFoundException.class);
  }
}
