package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.LoadRatingNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignLoadRatingXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignVehicleLoadCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.LoadPostingSignWrrntdCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.LoadRatingReasonXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreLoadRatingRsnCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingCodesResponse.DesignVehicle;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

/** Load Rating Details: its fields, and the manual ratings a P.Eng keeps, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureLoadRatingsService.class, LoadRatingService.class})
class StructureLoadRatingsServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(5);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(5);

  @Autowired
  private StructureLoadRatingsService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\ENGINEER");
    when(loggedUser.canDestroy()).thenReturn(true);
    when(loggedUser.isPeng()).thenReturn(true);
    for (String entity : List.of("StructureLoadRatingEntity", "CrossingStructureEntity",
        "CrossingSiteEntity", "LoadPostingSignWrrntdCodeEntity", "DesignVehicleLoadCodeEntity",
        "DesignLoadRatingXrefEntity", "StrctreLoadRatingRsnCodeEntity",
        "LoadRatingReasonXrefEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String code : List.of("YES", "NO", "UNK")) {
      entityManager.persist(LoadPostingSignWrrntdCodeEntity.builder()
          .loadPostingSignWrrntdCode(code).description(code).effectiveDate(PAST).expiryDate(FUTURE)
          .build());
    }
    for (String code : List.of("L75", "OTHER")) {
      entityManager.persist(DesignVehicleLoadCodeEntity.builder().designVehicleLoadCode(code)
          .description(code).effectiveDate(PAST).expiryDate(FUTURE).build());
    }
    entityManager.persist(new DesignLoadRatingXrefEntity("L75", 75L));
    entityManager.persist(new DesignLoadRatingXrefEntity("OTHER", -1L));
    for (String code : List.of("INSP", "OTHR", "NS")) {
      entityManager.persist(StrctreLoadRatingRsnCodeEntity.builder()
          .strctreLoadRatingRsnCode(code).description(code).effectiveDate(PAST).expiryDate(FUTURE)
          .build());
    }
    entityManager.persist(new LoadRatingReasonXrefEntity("INSP", "INC"));
    entityManager.persist(new LoadRatingReasonXrefEntity("INSP", "DEC"));
    entityManager.persist(new LoadRatingReasonXrefEntity("OTHR", "UNDEF"));
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").capitalRoadInd("N").crossingSiteTypeCode("XNG").build());
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(7L).crossingStructureName("B7").crossingSiteId("62-001")
        .structureTypeClassCode("TB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N").loadPostingSignWrrntdCode("UNK")
        .loadRatingUnknownIndicator("Y").build());
  }

  private CrossingStructureEntity structure() {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(CrossingStructureEntity.class, 7L);
  }

  private long givenManual(String rating, String reason, LocalDateTime entered) {
    StructureLoadRatingEntity entity = StructureLoadRatingEntity.builder()
        .crossingStructureId(7L).loadRating(new BigDecimal(rating)).reasonCode(reason)
        .siteAtTimeOfLoadRating("62-001").entryUserid("IDIR\\OLD").entryTimestamp(entered)
        .updateUserid("IDIR\\OLD").updateTimestamp(entered).build();
    entityManager.persist(entity);
    return entity.getStructureLoadRatingId();
  }

  private Map<String, String> detailsRefused(LoadRatingDetailsRequest request) {
    try {
      service.updateDetails(7L, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the details were saved");
  }

  private Map<String, String> addRefused(LoadRatingRequest request) {
    try {
      service.add(7L, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the rating was added");
  }

  @Test
  @DisplayName("lists the vehicles with the rating each fixes, and the reasons with their changes")
  void codes() {
    LoadRatingCodesResponse codes = service.codes();

    assertThat(codes.designVehicles()).containsExactly(
        new DesignVehicle("L75", "L75", 75L), new DesignVehicle("OTHER", "OTHER", null));
    assertThat(codes.reasons()).filteredOn(reason -> reason.code().equals("INSP"))
        .singleElement().satisfies(reason ->
            assertThat(reason.deltas()).containsExactlyInAnyOrder("INC", "DEC"));
    assertThat(codes.loadPostingSigns()).hasSize(3);
  }

  @Test
  @DisplayName("saves the fields, the design rating taken from a vehicle that fixes one")
  void savesDetails() {
    service.updateDetails(7L, new LoadRatingDetailsRequest("YES", false, "L75", "ignored", 3L));

    CrossingStructureEntity structure = structure();
    assertThat(structure.getLoadPostingSignWrrntdCode()).isEqualTo("YES");
    assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("N");
    assertThat(structure.getDesignVehicleLoadCode()).isEqualTo("L75");
    assertThat(structure.getDesignVehicleCmt()).isNull();
    assertThat(structure.getDesignLoadRating()).isEqualByComparingTo("75");
  }

  @Test
  @DisplayName("keeps the stored design rating while the vehicle is left as it was")
  void keepsStoredDesignRating() {
    service.updateDetails(7L, new LoadRatingDetailsRequest("UNK", true, "L75", null, null));
    entityManager.createQuery(
        "UPDATE CrossingStructureEntity s SET s.designLoadRating = 63 WHERE s.crossingStructureId = 7")
        .executeUpdate();

    service.updateDetails(7L, new LoadRatingDetailsRequest("UNK", false, "L75", null, 75L));

    assertThat(structure().getDesignLoadRating()).isEqualByComparingTo("63");
  }

  @Test
  @DisplayName("lets Level 1 change Review Required alone, as legacy's page")
  void level1() {
    when(loggedUser.canDestroy()).thenReturn(false);
    when(loggedUser.isPeng()).thenReturn(false);

    assertThat(detailsRefused(new LoadRatingDetailsRequest("YES", true, null, null, null)))
        .containsExactly(Map.entry("loadPostingSignsCode",
            "Are Load Posting Signs Warranted? can be changed only at Level 2 and up."));
    service.updateDetails(7L, new LoadRatingDetailsRequest("UNK", false, null, null, null));
    assertThat(structure().getLoadRatingUnknownIndicator()).isEqualTo("N");
  }

  @Test
  @DisplayName("keeps the Other vehicle's words a P.Eng's, so Level 2 cannot save Other, as legacy")
  void otherVehicle() {
    when(loggedUser.isPeng()).thenReturn(false);

    assertThat(detailsRefused(new LoadRatingDetailsRequest("UNK", true, "OTHER", "Logging truck",
        40L))).containsExactly(Map.entry("designVehicleComment",
            "Only a P.Eng can enter the Design Vehicle Configuration details."));
    assertThat(detailsRefused(new LoadRatingDetailsRequest("UNK", true, "OTHER", null, 40L)))
        .containsExactly(Map.entry("designVehicleComment",
            "Design Vehicle Configuration Details is required."));

    when(loggedUser.isPeng()).thenReturn(true);
    service.updateDetails(7L,
        new LoadRatingDetailsRequest("UNK", true, "OTHER", "Logging truck", 40L));
    assertThat(structure().getDesignLoadRating()).isEqualByComparingTo("40");
  }

  @Test
  @DisplayName("needs a typed design rating of at least 1, and Load Posting Signs")
  void detailRules() {
    assertThat(detailsRefused(new LoadRatingDetailsRequest(" ", true, "OTHER", "x", 0L)))
        .containsExactly(
            Map.entry("loadPostingSignsCode", "Are Load Posting Signs Warranted? is required."),
            Map.entry("designLoadRating",
                "Original Design Load Rating GVW (tonnes) must be greater than or equal to 1."));
  }

  @Test
  @DisplayName("adds a rating that becomes current, copying it on and clearing Review Required")
  void addsCurrent() {
    String id = service.add(7L, new LoadRatingRequest(45L, "INSP", "dropped"));

    CrossingStructureEntity structure = structure();
    assertThat(structure.getCurrentLoadRating()).isEqualByComparingTo("45");
    assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("N");
    StructureLoadRatingEntity rating =
        entityManager.find(StructureLoadRatingEntity.class, Long.parseLong(id));
    assertThat(rating.getSiteAtTimeOfLoadRating()).isEqualTo("62-001");
    assertThat(rating.getReasonComment()).isNull();
    assertThat(rating.getEntryUserid()).isEqualTo("IDIR\\ENGINEER");
  }

  @Test
  @DisplayName("needs a rating, a reason, and Other's words")
  void addRules() {
    assertThat(addRefused(new LoadRatingRequest(null, " ", null))).containsExactly(
        Map.entry("rating", "Load Rating is required."),
        Map.entry("reasonCode", "Load Rating Reason is required."));
    assertThat(addRefused(new LoadRatingRequest(1_000_000L, "OTHR", " "))).containsExactly(
        Map.entry("rating", "Load Rating must be a whole number from 0 to 999999."),
        Map.entry("reasonComment", "Load Rating Reason Comment is required for Other."));
  }

  @Test
  @DisplayName("refuses to add over the 0-tonne New Structure placeholder, as legacy")
  void newStructurePlaceholder() {
    givenManual("0", "NS", LocalDateTime.now().minusDays(1));

    assertThat(addRefused(new LoadRatingRequest(45L, "INSP", null))).containsExactly(
        Map.entry("rating", "Edit the 0-tonne New Structure rating rather than adding another."));
  }

  @Test
  @DisplayName("changes a manual rating and copies the current one on again")
  void updates() {
    long id = givenManual("30", "INSP", LocalDateTime.now().minusDays(1));

    service.update(7L, id, new LoadRatingRequest(35L, "OTHR", "Retested"));

    assertThat(structure().getCurrentLoadRating()).isEqualByComparingTo("35");
    StructureLoadRatingEntity rating = entityManager.find(StructureLoadRatingEntity.class, id);
    assertThat(rating.getReasonComment()).isEqualTo("Retested");
    assertThat(rating.getUpdateUserid()).isEqualTo("IDIR\\ENGINEER");
  }

  @Test
  @DisplayName("deleting the current rating sets Review Required when no inspection was reviewed")
  void deletesCurrent() {
    givenManual("20", "INSP", LocalDateTime.now().minusDays(10));
    long latest = givenManual("30", "INSP", LocalDateTime.now().minusDays(1));
    service.updateDetails(7L, new LoadRatingDetailsRequest("UNK", false, null, null, null));

    service.delete(7L, latest);

    CrossingStructureEntity structure = structure();
    assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("Y");
    assertThat(structure.getCurrentLoadRating()).isEqualByComparingTo("20");
  }

  @Test
  @DisplayName("reaches only this structure's manual ratings")
  void onlyManualRatings() {
    StructureLoadRatingEntity fromInspection = StructureLoadRatingEntity.builder()
        .crossingStructureId(7L).inspectionId(99L).loadRating(new BigDecimal("50"))
        .entryTimestamp(LocalDateTime.now()).build();
    entityManager.persist(fromInspection);
    long id = fromInspection.getStructureLoadRatingId();

    assertThatThrownBy(() -> service.delete(7L, id))
        .isInstanceOf(LoadRatingNotFoundException.class);
  }
}
