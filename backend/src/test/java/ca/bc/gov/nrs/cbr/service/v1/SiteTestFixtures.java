package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSegmentEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.SpecialAccessRequirementCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;

/**
 * The reference rows a valid site points at, shared by the create and update tests — both save
 * through the same validator, so both need the same code tables and the same road.
 */
final class SiteTestFixtures {

  private SiteTestFixtures() {}

  /** The code and org-unit rows a valid site points at. Every one is a real foreign key. */
  static void givenCodeTables(EntityManager entityManager) {
    LocalDateTime from = LocalDateTime.now().minusYears(10);
    LocalDateTime until = LocalDateTime.now().plusYears(10);

    for (String code : new String[] {"ACT", "PP", "DAC"}) {
      if (entityManager.find(CrossingSiteStatusCodeEntity.class, code) == null) {
        entityManager.persist(CrossingSiteStatusCodeEntity.builder()
            .crossingSiteStatusCode(code).description(code)
            .effectiveDate(from).expiryDate(until).updateTimestamp(from).build());
      }
    }
    for (String code : new String[] {"CRS", "REC", "STRG"}) {
      if (entityManager.find(CrossingSiteTypeCodeEntity.class, code) == null) {
        entityManager.persist(CrossingSiteTypeCodeEntity.builder()
            .crossingSiteTypeCode(code).description(code)
            .effectiveDate(from).expiryDate(until).updateTimestamp(from).build());
      }
    }
    for (String code : new String[] {"INS", "DNI"}) {
      if (entityManager.find(StructureInspectionStatusCodeEntity.class, code) == null) {
        entityManager.persist(StructureInspectionStatusCodeEntity.builder()
            .structureInspectionStatusCode(code).description(code)
            .effectiveDate(from).expiryDate(until).updateTimestamp(from).build());
      }
    }
    if (entityManager.find(SpecialAccessRequirementCodeEntity.class, "HEL") == null) {
      entityManager.persist(SpecialAccessRequirementCodeEntity.builder()
          .specialAccessRequirementCode("HEL").description("Helicopter")
          .effectiveDate(from).expiryDate(until).updateTimestamp(from).build());
    }
    // Two tables, and they are not interchangeable. CROSSING_SITE's foreign keys point at
    // ORG_UNIT, while legacy validates an org unit against the CBR_ORG_UNIT view —
    // CBR_GENERAL.FIND_ORG_UNIT_BY_NUMBER selects from it — so a fixture with only one of them
    // tests half the rule.
    for (long orgUnitNo : new long[] {18L, 26L, 31L}) {
      if (entityManager.find(OrgUnitEntity.class, orgUnitNo) == null) {
        entityManager.persist(OrgUnitEntity.builder()
            .orgUnitNo(orgUnitNo).orgUnitCode("U" + orgUnitNo).orgUnitName("Unit " + orgUnitNo)
            .build());
      }
      if (entityManager.find(CbrOrgUnitEntity.class, orgUnitNo) == null) {
        entityManager.persist(CbrOrgUnitEntity.builder()
            .orgUnitNo(orgUnitNo).orgUnitCode("U" + orgUnitNo).orgUnitName("Unit " + orgUnitNo)
            .orgUnitType("D").effectiveDate(from).expiryDate(until).build());
      }
    }
    // The road the valid request names, with two segments so "the first one" means something.
    // Persisted out of order on purpose: legacy's ORDER BY ROAD_SEGMENT_ID is what decides, not
    // insertion order, and a fixture that agreed with both would not tell them apart.
    givenRoadSegment(entityManager, "R00123", "01", 77L);
    givenRoadSegment(entityManager, "R00123", "01", 42L);
    entityManager.flush();
  }

  static void givenRoadSegment(
      EntityManager entityManager, String forestFileId, String roadSectionId, long roadSegmentId) {
    entityManager.persist(CbrRoadSegmentEntity.builder()
        .forestFileId(forestFileId)
        .roadSectionId(roadSectionId)
        .roadSegmentId(roadSegmentId)
        .roadResponsibilityTypeCode("MOF")
        .build());
  }
}
