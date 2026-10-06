package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientLocationEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.RecreationProjectEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.LoadRatingEntry;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.LoadRatingStatus;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.OutstandingItem;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Section;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * One structure, for its page. Against the database: most of the answer is joins and decodes, and
 * the load rating history is a union a mock would only restate.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureDetailService.class, LoadRatingService.class})
class StructureDetailServiceTest {

  private static final LocalDateTime EARLY = LocalDateTime.of(2000, 1, 1, 0, 0);
  private static final LocalDateTime LATE = LocalDateTime.of(9999, 12, 31, 0, 0);

  @Autowired
  private StructureDetailService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("InspectionReportStatusEntity", "StructureLoadRatingEntity",
        "StructureInspectionEntity", "InspectionReportStatusCodeEntity",
        "StructureInspectionReviewerEntity", "StructureCommentEntity",
        "StructureReplacementXrefEntity", "ForestServiceBridgeSpanEntity",
        "ForestServiceBridgeEntity", "ForestServiceCulvertEntity", "CrossingStructureEntity",
        "CrossingSiteEntity", "CbrRoadSectionEntity", "CbrOrgUnitEntity", "OrgUnitEntity",
        "ClientLocationEntity", "ClientPublicEntity", "RecreationProjectEntity",
        "CrossingSiteStatusCodeEntity", "StructureInspectionStatusCodeEntity",
        "StructureTypeClassCodeEntity", "BuiltByCodeEntity", "AbutmentCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
  }

  /* ------------------------------------------------------------------ fixtures */

  private void givenSite(String id, Consumer<CrossingSiteEntity.CrossingSiteEntityBuilder> change) {
    CrossingSiteEntity.CrossingSiteEntityBuilder site = CrossingSiteEntity.builder()
        .crossingSiteId(id).capitalRoadInd("N").crossingSiteTypeCode("XNG");
    change.accept(site);
    entityManager.persist(site.build());
  }

  /**
   * A timber bridge that is complete by the structure page's rules: a source, a next inspection,
   * a load restriction year, a manual load rating (added by the caller where it matters, else
   * Review Required), and a complete bridge row.
   */
  private void givenStructure(
      long id, String name, String siteId,
      Consumer<CrossingStructureEntity.CrossingStructureEntityBuilder> change) {
    CrossingStructureEntity.CrossingStructureEntityBuilder structure =
        CrossingStructureEntity.builder()
            .crossingStructureId(id)
            .crossingStructureName(name)
            .crossingSiteId(siteId)
            .structureTypeClassCode("TB")
            .activeInd("Y")
            .closeProximityInd("N")
            .portableStructureInd("N")
            .asBuiltInfoPresentInd("N")
            .loadRatingUnknownIndicator("Y")
            .fullLogHaulReplacementYear(2040)
            .nextPlannedInspectionDate(LocalDate.of(2027, 1, 1))
            .structureSourceCode("MOF");
    change.accept(structure);
    entityManager.persist(structure.build());
  }

  private void givenBridge(long id, long structureId,
      Consumer<ForestServiceBridgeEntity.ForestServiceBridgeEntityBuilder> change) {
    ForestServiceBridgeEntity.ForestServiceBridgeEntityBuilder bridge =
        ForestServiceBridgeEntity.builder()
            .forestServiceBridgeId(id).crossingStructureId(structureId)
            .totalBridgeLength(new BigDecimal("24.5")).deckWidth(new BigDecimal("4.27"))
            .runningSurfaceCode("GRV").deckTypeCode("TIM").superstructureTypeCode("STL")
            .leftAbutmentCode("CON").rightAbutmentCode("CON").needleBeamInd("N");
    change.accept(bridge);
    entityManager.persist(bridge.build());
  }

  private void givenBridge(long id, long structureId) {
    givenBridge(id, structureId, bridge -> { });
  }

  private void givenStatusCode(String status) {
    if (entityManager.find(InspectionReportStatusCodeEntity.class, status) == null) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status).description(status)
          .effectiveDate(EARLY).expiryDate(LATE).build());
    }
  }

  private void givenInspection(long inspectionId, long structureId, LocalDate date,
      String status, Long reviewerId) {
    givenStatusCode(status);
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(inspectionId).crossingStructureId(structureId).inspectionDate(date)
        .inspectionReviewerId(reviewerId)
        .pengReviewerDate(date.plusDays(10).atTime(9, 0)).build());
    entityManager.persist(InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(inspectionId).inspectionId(inspectionId)
        .inspectionReportStatusCode(status).build());
  }

  private void givenRating(long id, long structureId, Long inspectionId, String rating,
      LocalDateTime entered) {
    entityManager.persist(StructureLoadRatingEntity.builder()
        .structureLoadRatingId(id).crossingStructureId(structureId).inspectionId(inspectionId)
        .loadRating(new BigDecimal(rating)).entryTimestamp(entered)
        .entryUserid("IDIR\\ENTERER").build());
  }

  private StructureDetailResponse read(long id) {
    entityManager.flush();
    entityManager.clear();
    return service.findById(id);
  }

  /* ------------------------------------------------------------------ the tests */

  @Test
  @DisplayName("answers 404 for a structure that does not exist")
  void refusesMissing() {
    assertThatThrownBy(() -> service.findById(999L))
        .isInstanceOf(StructureNotFoundException.class);
  }

  @Nested
  @DisplayName("header")
  class Header {

    @Test
    @DisplayName("names the structure and decodes its site, every code included")
    void decodesTheSite() {
      entityManager.persist(CrossingSiteStatusCodeEntity.builder()
          .crossingSiteStatusCode("ACT").description("Active").build());
      entityManager.persist(StructureInspectionStatusCodeEntity.builder()
          .structureInspectionStatusCode("OK").description("Inspected").build());
      entityManager.persist(OrgUnitEntity.builder()
          .orgUnitNo(18L).orgUnitCode("DCC").orgUnitName("Cariboo-Chilcotin").build());
      entityManager.persist(CbrOrgUnitEntity.builder()
          .orgUnitNo(18L).orgUnitCode("DCC").orgUnitName("Cariboo-Chilcotin District").build());
      entityManager.persist(CbrOrgUnitEntity.builder()
          .orgUnitNo(26L).orgUnitCode("DCA").orgUnitName("Central Cariboo").build());
      entityManager.persist(CbrRoadSectionEntity.builder()
          .forestFileId("6970").roadSectionId("01").roadSectName("CHILCOTIN SOUTH").build());
      entityManager.persist(ClientPublicEntity.builder()
          .clientNumber("00001271").clientName("CANADIAN FOREST PRODUCTS LTD.").build());
      entityManager.persist(ClientLocationEntity.builder()
          .clientNumber("00001271").clientLocnCode("01").city("Prince George").build());
      entityManager.persist(StructureTypeClassCodeEntity.builder()
          .structureTypeClassCode("TB").description("Timber Bridge").build());
      givenSite("62-001", site -> site.crossingSiteStatusCode("ACT")
          .structureInspectionStatusCode("OK").orgUnitNo(18L).managementOrgUnitNo(26L)
          .forestFileId("6970").roadSectionId("01").crossingName("Riske Cr")
          .pointOfCommencementDistance(new BigDecimal("0.24"))
          .clientNumber("00001271").clientLocnCode("01"));
      givenStructure(7L, "B100", "62-001", s -> { });
      givenBridge(70L, 7L);

      StructureDetailResponse structure = read(7L);

      assertThat(structure.structureName()).isEqualTo("B100");
      assertThat(structure.active()).isTrue();
      assertThat(structure.typeClass()).isEqualTo(new CodeValue("TB", "Timber Bridge"));
      StructureDetailResponse.Site site = structure.site();
      assertThat(site.siteStatus()).isEqualTo(new CodeValue("ACT", "Active"));
      // Legacy shows this one as a raw code.
      assertThat(site.inspectionStatus()).isEqualTo(new CodeValue("OK", "Inspected"));
      assertThat(site.districtName()).isEqualTo("Cariboo-Chilcotin District");
      assertThat(site.managementAreaName()).isEqualTo("Central Cariboo");
      assertThat(site.forestServiceRoad()).isEqualTo("CHILCOTIN SOUTH");
      assertThat(site.crossingName()).isEqualTo("Riske Cr");
      assertThat(site.kilometres()).isEqualTo("0.24");
      assertThat(site.maintainerLabel())
          .isEqualTo("CANADIAN FOREST PRODUCTS LTD. · Prince George · 00001271-01");
    }

    @Test
    @DisplayName("gives a recreation site its project name and no management area")
    void namesTheRecreationProject() {
      entityManager.persist(RecreationProjectEntity.builder()
          .forestFileId("REC0191").projectName("Lakeside Trail").build());
      entityManager.persist(CbrOrgUnitEntity.builder()
          .orgUnitNo(26L).orgUnitCode("DCA").orgUnitName("Central Cariboo").build());
      givenSite("REC-1", site -> site.crossingSiteTypeCode("REC").forestFileId("REC0191")
          .managementOrgUnitNo(26L));
      givenStructure(7L, "B100", "REC-1", s -> { });
      givenBridge(70L, 7L);

      StructureDetailResponse.Site site = read(7L).site();

      assertThat(site.projectName()).isEqualTo("Lakeside Trail");
      assertThat(site.managementAreaName()).isNull();
    }

    @Test
    @DisplayName("says when the structure is archived")
    void reportsArchived() {
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> s.activeInd("N"));
      givenBridge(70L, 7L);

      assertThat(read(7L).active()).isFalse();
    }

    @Test
    @DisplayName("still returns a code its table does not know, with no description")
    void keepsUnknownCodes() {
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> s.structureTypeClassCode("TB"));
      givenBridge(70L, 7L);

      assertThat(read(7L).typeClass()).isEqualTo(new CodeValue("TB", null));
    }
  }

  @Nested
  @DisplayName("details")
  class Details {

    @Test
    @DisplayName("decodes the common fields and leaves what is not stored as null")
    void showsTheCommonFields() {
      entityManager.persist(BuiltByCodeEntity.builder()
          .builtByCode("MOF").description("Ministry of Forests").build());
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> s.builtByCode("MOF").yearBuilt(2000)
          .installationCost(125000L).asBuiltInfoPresentInd("Y"));
      givenBridge(70L, 7L);

      StructureDetailResponse.Common details = read(7L).details();

      assertThat(details.builtBy()).isEqualTo(new CodeValue("MOF", "Ministry of Forests"));
      assertThat(details.yearBuilt()).isEqualTo(2000);
      assertThat(details.installationCost()).isEqualTo(125000L);
      assertThat(details.asBuiltInfoOnFile()).isTrue();
      // Not stored, so not invented.
      assertThat(details.endOfDesignLifeYear()).isNull();
    }

    @Test
    @DisplayName("gives a bridge its bridge record and its span count")
    void showsTheBridge() {
      entityManager.persist(AbutmentCodeEntity.builder()
          .abutmentCode("CON").description("Concrete").build());
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> { });
      givenBridge(70L, 7L);
      entityManager.persist(ForestServiceBridgeSpanEntity.builder()
          .forestServiceBridgeSpanId(1L).forestServiceBridgeId(70L).build());
      entityManager.persist(ForestServiceBridgeSpanEntity.builder()
          .forestServiceBridgeSpanId(2L).forestServiceBridgeId(70L).build());

      StructureDetailResponse structure = read(7L);

      assertThat(structure.culvert()).isNull();
      assertThat(structure.bridge().spanCount()).isEqualTo(2);
      assertThat(structure.bridge().deckWidthMetres()).isEqualByComparingTo("4.27");
      assertThat(structure.bridge().leftAbutment()).isEqualTo(new CodeValue("CON", "Concrete"));
    }

    @Test
    @DisplayName("gives a culvert its record and how many active culverts share its site")
    void showsTheCulvert() {
      givenSite("SITE-1", site -> { });
      givenStructure(8L, "C1", "SITE-1", s -> s.structureTypeClassCode("CUL"));
      givenStructure(9L, "C2", "SITE-1", s -> s.structureTypeClassCode("WLC"));
      givenStructure(10L, "C3-ARCHIVED", "SITE-1",
          s -> s.structureTypeClassCode("CUL").activeInd("N"));
      givenStructure(11L, "BRIDGE", "SITE-1", s -> { });
      entityManager.persist(ForestServiceCulvertEntity.builder()
          .forestServiceCulvertId(80L).crossingStructureId(8L).culvertNumber(1)
          .slope(new BigDecimal("2.5")).build());

      StructureDetailResponse structure = read(8L);

      assertThat(structure.bridge()).isNull();
      assertThat(structure.culvert().culvertNumber()).isEqualTo(1);
      assertThat(structure.culvert().culvertsOnSite()).isEqualTo(2);
      assertThat(structure.culvert().gradient()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("lists general comments newest first, leaving planned-inspection ones out")
    void listsComments() {
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> { });
      givenBridge(70L, 7L);
      entityManager.persist(StructureCommentEntity.builder()
          .structureCommentId(1L).crossingStructureId(7L).structureComment("Installed.")
          .plannedInspectionCmtInd("N").updateUserid("IDIR\\A")
          .updateTimestamp(LocalDateTime.of(2000, 9, 1, 9, 0)).build());
      entityManager.persist(StructureCommentEntity.builder()
          .structureCommentId(2L).crossingStructureId(7L).structureComment("Deck replaced.")
          .plannedInspectionCmtInd("N").updateUserid("IDIR\\B")
          .updateTimestamp(LocalDateTime.of(2024, 6, 3, 14, 5)).build());
      entityManager.persist(StructureCommentEntity.builder()
          .structureCommentId(3L).crossingStructureId(7L).structureComment("Bring a ladder.")
          .plannedInspectionCmtInd("Y").updateUserid("IDIR\\C")
          .updateTimestamp(LocalDateTime.of(2025, 1, 1, 9, 0)).build());

      assertThat(read(7L).comments())
          .extracting(StructureDetailResponse.Comment::text)
          .containsExactly("Deck replaced.", "Installed.");
    }

    @Test
    @DisplayName("links the structures it replaced and was replaced by")
    void linksReplacements() {
      givenSite("SITE-1", site -> { });
      givenStructure(6L, "B050", "SITE-1", s -> { });
      givenStructure(7L, "B100", "SITE-1", s -> { });
      givenStructure(8L, "B200", "SITE-1", s -> { });
      givenBridge(70L, 7L);
      entityManager.persist(StructureReplacementXrefEntity.builder()
          .replacedStructureNumber(6L).replacesStructureNumber(7L).build());
      entityManager.persist(StructureReplacementXrefEntity.builder()
          .replacedStructureNumber(7L).replacesStructureNumber(8L).build());

      StructureDetailResponse structure = read(7L);

      assertThat(structure.replaced())
          .extracting(StructureDetailResponse.StructureRef::structureName)
          .containsExactly("B050");
      assertThat(structure.replacedBy())
          .extracting(StructureDetailResponse.StructureRef::structureName)
          .containsExactly("B200");
    }
  }

  @Nested
  @DisplayName("load ratings")
  class LoadRatings {

    @Test
    @DisplayName("merges manual ratings with reviewed inspections, newest first, current marked")
    void mergesTheHistory() {
      entityManager.persist(StructureInspectionReviewerEntity.builder()
          .inspectionReviewerId(5L).userid("IDIR\\PENG").build());
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> s.designLoadRating(new BigDecimal("63")));
      givenBridge(70L, 7L);
      givenRating(1L, 7L, null, "63", LocalDateTime.of(2000, 10, 1, 9, 0));
      givenInspection(100L, 7L, LocalDate.of(2024, 5, 1), "RVD", 5L);
      givenRating(2L, 7L, 100L, "60", LocalDateTime.of(2024, 5, 20, 9, 0));
      // Reviewed, but recorded no rating — legacy's "UNK".
      givenInspection(101L, 7L, LocalDate.of(2010, 6, 1), "ACC", 5L);
      // Not reviewed, so not a load rating at all.
      givenInspection(102L, 7L, LocalDate.of(2025, 1, 1), "SUB", 5L);

      StructureDetailResponse.LoadRating loadRating = read(7L).loadRating();

      assertThat(loadRating.history()).extracting(LoadRatingEntry::date).containsExactly(
          LocalDate.of(2024, 5, 1), LocalDate.of(2010, 6, 1), LocalDate.of(2000, 10, 1));
      LoadRatingEntry newest = loadRating.history().get(0);
      assertThat(newest.rating()).isEqualByComparingTo("60");
      assertThat(newest.status()).isEqualTo(LoadRatingStatus.REVIEWED);
      assertThat(newest.userId()).isEqualTo("IDIR\\PENG");
      assertThat(newest.reviewedDate()).isEqualTo(LocalDate.of(2024, 5, 11));
      assertThat(newest.current()).isTrue();
      assertThat(loadRating.history().get(1).rating()).isNull();
      LoadRatingEntry manual = loadRating.history().get(2);
      assertThat(manual.status()).isEqualTo(LoadRatingStatus.MANUAL);
      assertThat(manual.userId()).isEqualTo("IDIR\\ENTERER");
      assertThat(manual.current()).isFalse();
      assertThat(loadRating.currentRating()).isEqualByComparingTo("60");
      assertThat(loadRating.designLoadRating()).isEqualByComparingTo("63");
    }
  }

  @Nested
  @DisplayName("what is outstanding — legacy's structure page rules")
  class Outstanding {

    @Test
    @DisplayName("lists nothing for a complete structure")
    void completeHasNothing() {
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> { });
      givenBridge(70L, 7L);

      assertThat(read(7L).outstanding()).isEmpty();
    }

    @Test
    @DisplayName("names each missing value and where it belongs")
    void namesWhatIsMissing() {
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> s.loadRatingUnknownIndicator("N")
          .fullLogHaulReplacementYear(null).nextPlannedInspectionDate(null)
          .structureSourceCode(null));
      givenBridge(70L, 7L, bridge -> bridge.deckWidth(null).rightAbutmentCode(null));

      assertThat(read(7L).outstanding()).containsExactly(
          new OutstandingItem(Section.DETAILS, "A load rating, or Load Rating Review Required"),
          new OutstandingItem(Section.DETAILS, "Estimated Load Restriction (year)"),
          new OutstandingItem(Section.INSPECTIONS, "Next Planned Routine Inspection"),
          new OutstandingItem(Section.DETAILS, "Source"),
          new OutstandingItem(Section.DETAILS, "Deck Width (metres)"),
          new OutstandingItem(Section.DETAILS, "Right Abutment"));
    }

    @Test
    @DisplayName("does not ask a recreation site for a load restriction year")
    void spareRecreationSites() {
      givenSite("REC-1", site -> site.crossingSiteTypeCode("REC"));
      givenStructure(7L, "B100", "REC-1", s -> s.fullLogHaulReplacementYear(null));
      givenBridge(70L, 7L);

      assertThat(read(7L).outstanding()).isEmpty();
    }

    @Test
    @DisplayName("does not ask for abutments of a portable superstructure in storage")
    void spareStoredSuperstructures() {
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> s.structureTypeClassCode("PB"));
      givenBridge(70L, 7L, bridge -> bridge.portableSuperstructureStatusCode("S")
          .leftAbutmentCode(null).rightAbutmentCode(null));

      assertThat(read(7L).outstanding()).isEmpty();
    }

    @Test
    @DisplayName("counts a reviewed inspection as a load rating")
    void countsReviewedInspections() {
      entityManager.persist(StructureInspectionReviewerEntity.builder()
          .inspectionReviewerId(5L).userid("IDIR\\PENG").build());
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> s.loadRatingUnknownIndicator("N"));
      givenBridge(70L, 7L);
      givenInspection(100L, 7L, LocalDate.of(2024, 5, 1), "RVD", 5L);

      assertThat(read(7L).outstanding()).isEmpty();
    }

    @Test
    @DisplayName("asks for the bridge record of a bridge that has none")
    void asksForTheBridgeRecord() {
      givenSite("SITE-1", site -> { });
      givenStructure(7L, "B100", "SITE-1", s -> { });

      assertThat(read(7L).outstanding())
          .containsExactly(new OutstandingItem(Section.DETAILS, "Bridge details"));
    }
  }
}
