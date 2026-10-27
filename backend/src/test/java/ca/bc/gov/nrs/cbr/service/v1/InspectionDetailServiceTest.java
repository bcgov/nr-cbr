package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreLoadRatingRsnCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse.StatusChange;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** One inspection, read for its page. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(InspectionDetailService.class)
class InspectionDetailServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);
  private static final LocalDateTime FUTURE = LocalDateTime.of(2999, 1, 1, 0, 0);

  @Autowired
  private InspectionDetailService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    entityManager.persist(CrossingStructureEntity.builder().crossingStructureId(7L)
        .crossingStructureName("B7").structureTypeClassCode("PB").activeInd("Y")
        .closeProximityInd("N").portableStructureInd("N").asBuiltInfoPresentInd("N").build());
    entityManager.persist(StrctreInspectionTypeCodeEntity.builder()
        .strctreInspectionTypeCode("ROUT").description("Routine").effectiveDate(PAST)
        .expiryDate(FUTURE).updateTimestamp(PAST).build());
    for (String[] code : new String[][] {{"PRO", "In Progress"}, {"SUB", "Submitted"},
        {"RVD", "Reviewed"}, {"OFL", "Offline"}}) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(code[0]).description(code[1]).effectiveDate(PAST)
          .expiryDate(FUTURE).updateTimestamp(PAST).build());
    }
  }

  /** The reviewer the inspections are bound to: none of the table's, unless a test adds one. */
  private long reviewerId = 3L;

  private void inspection(long id, String... statuses) {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(7L).strctreInspectionTypeCode("ROUT")
        .inspectionDate(LocalDate.of(2024, 5, 1)).inspectorName("Jo Smith")
        .inspectorCompanyName("Bridges Ltd").inspectionCost(1200L).pengInspectionReqInd("Y")
        .inspectionComment("Fine").pengComment("Agreed").siteAtTimeOfInspection("62-001")
        .inspectionReviewerId(reviewerId).pengReviewerDate(LocalDateTime.of(2024, 6, 2, 9, 0))
        .channelPwlDepth(new BigDecimal("1.5")).scourHazardInd("Y").debrisHazardInd("N")
        .hazardComment("Some scour").entryUserid("IDIR\\JO")
        .entryTimestamp(LocalDateTime.of(2024, 5, 1, 8, 0)).build());
    long statusId = id * 10;
    for (String status : statuses) {
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(statusId++).inspectionId(id)
          .inspectionReportStatusCode(status).entryUserid("IDIR\\" + status)
          .entryTimestamp(LocalDateTime.of(2024, 5, (int) (statusId % 10) + 1, 8, 0)).build());
    }
  }

  private InspectionDetailResponse read(long id) {
    entityManager.flush();
    entityManager.clear();
    return service.findById(id);
  }

  @Test
  @DisplayName("reads the inspection, its hazards and its status history, newest first")
  void readsTheInspection() {
    inspection(41L, "PRO", "SUB", "RVD");

    InspectionDetailResponse response = read(41L);

    assertThat(response.id()).isEqualTo("41");
    assertThat(response.structureId()).isEqualTo("7");
    assertThat(response.structureName()).isEqualTo("B7");
    assertThat(response.type().description()).isEqualTo("Routine");
    assertThat(response.status().code()).isEqualTo("RVD");
    assertThat(response.statusHistory())
        .extracting(change -> change.status().description(), StatusChange::changedBy)
        .containsExactly(tuple("Reviewed", "IDIR\\RVD"), tuple("Submitted", "IDIR\\SUB"),
            tuple("In Progress", "IDIR\\PRO"));
    assertThat(response.inspectorCompanyName()).isEqualTo("Bridges Ltd");
    assertThat(response.cost()).isEqualTo(1200L);
    assertThat(response.professionalInspectionRequired()).isTrue();
    assertThat(response.hazards().presentWaterDepth()).isEqualByComparingTo("1.5");
    assertThat(response.hazards().scour()).isTrue();
    assertThat(response.hazards().debris()).isFalse();
    assertThat(response.entered().userId()).isEqualTo("IDIR\\JO");
    assertThat(response.offline()).isFalse();
  }

  @Test
  @DisplayName("carries the review: who and when, the load rating and its reason, the comments")
  void readsTheReview() {
    StructureInspectionReviewerEntity reviewer = StructureInspectionReviewerEntity.builder()
        .userid("IDIR\\PAT").firstName("Pat").lastName("Engineer").build();
    entityManager.persist(reviewer);
    reviewerId = reviewer.getInspectionReviewerId();
    inspection(41L, "RVD");
    entityManager.persist(StrctreLoadRatingRsnCodeEntity.builder().strctreLoadRatingRsnCode("SD")
        .description("Structural deterioration").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(StructureLoadRatingEntity.builder().crossingStructureId(7L)
        .inspectionId(41L).loadRating(new BigDecimal("55")).reasonCode("SD")
        .entryTimestamp(PAST).build());

    var review = read(41L).review();

    assertThat(review.reviewedBy()).isEqualTo("Pat Engineer");
    assertThat(review.reviewedDate()).isEqualTo(LocalDate.of(2024, 6, 2));
    assertThat(review.loadRating()).isEqualByComparingTo("55");
    assertThat(review.loadRatingReason().description()).isEqualTo("Structural deterioration");
    assertThat(review.pengComment()).isEqualTo("Agreed");
  }

  @Test
  @DisplayName("lists the answers where they were asked: a span, a pier, a stringer")
  void readsTheAnswers() {
    inspection(41L, "PRO");
    entityManager.persist(StructureInspectionItemEntity.builder().structureInspectionItemId(1L)
        .inspectionId(41L).subsectionCode("APP").questionCode("APPQ").answerValue("G").build());
    entityManager.persist(StructureInspectionItemEntity.builder().structureInspectionItemId(2L)
        .inspectionId(41L).subsectionCode("STR").questionCode("PEEL")
        .forestServiceBridgeSpanId(701L).stringerNumber(2L).answerValue("Y")
        .itemComment("Peeled").build());

    assertThat(read(41L).items())
        .extracting(Item::subsectionCode, Item::spanId, Item::stringerNumber, Item::answer)
        .containsExactly(tuple("APP", null, null, "G"), tuple("STR", "701", 2L, "Y"));
  }

  @Test
  @DisplayName("says when it is out on the offline client, and refuses one that does not exist")
  void offlineAndMissing() {
    inspection(41L, "OFL");

    assertThat(read(41L).offline()).isTrue();
    assertThat(read(41L).review().loadRating()).isNull();
    assertThatThrownBy(() -> service.findById(404L))
        .isInstanceOf(InspectionNotFoundException.class);
  }
}
