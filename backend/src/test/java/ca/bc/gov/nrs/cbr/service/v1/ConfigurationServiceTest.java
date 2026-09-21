package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.SpecialAccessRequirementCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CbrOrgUnitRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.SpecialAccessRequirementCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StrctreInspectionTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureTypeClassCodeRepository;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each lookup reads its own table and maps it to the right two fields.
 *
 * <p>Ten methods that each fetch a list and map it are exactly where a copy-paste slip lives: every
 * one returns the same type, so calling the wrong repository or reading the wrong getter compiles
 * and returns a plausible list. Each stub below carries a value that identifies where it came from,
 * which is what makes such a mix-up visible.
 */
class ConfigurationServiceTest {

  private final CrossingSiteStatusCodeRepository siteStatusCodes =
      mock(CrossingSiteStatusCodeRepository.class);
  private final StructureInspectionStatusCodeRepository inspectionStatusCodes =
      mock(StructureInspectionStatusCodeRepository.class);
  private final SpecialAccessRequirementCodeRepository specialAccessCodes =
      mock(SpecialAccessRequirementCodeRepository.class);
  private final CrossingSiteTypeCodeRepository siteTypeCodes =
      mock(CrossingSiteTypeCodeRepository.class);
  private final StructureTypeClassCodeRepository structureTypeClassCodes =
      mock(StructureTypeClassCodeRepository.class);
  private final StrctreInspectionTypeCodeRepository inspectionTypeCodes =
      mock(StrctreInspectionTypeCodeRepository.class);
  private final InspectionReportStatusCodeRepository inspectionReportStatusCodes =
      mock(InspectionReportStatusCodeRepository.class);
  private final CbrOrgUnitRepository orgUnits = mock(CbrOrgUnitRepository.class);

  private final ConfigurationService service = new ConfigurationService(
      siteStatusCodes,
      inspectionStatusCodes,
      specialAccessCodes,
      siteTypeCodes,
      structureTypeClassCodes,
      inspectionTypeCodes,
      inspectionReportStatusCodes,
      orgUnits);

  private static CbrOrgUnitEntity orgUnit(long orgUnitNo, String code, String name) {
    return CbrOrgUnitEntity.builder()
        .orgUnitNo(orgUnitNo).orgUnitCode(code).orgUnitName(name).build();
  }

  @Test
  @DisplayName("each code list is read from its own table and mapped to code + description")
  void eachCodeListComesFromItsOwnTable() {
    when(siteStatusCodes.findAllInDisplayOrder()).thenReturn(List.of(
        CrossingSiteStatusCodeEntity.builder()
            .crossingSiteStatusCode("ST").description("from the status table").build()));
    when(inspectionStatusCodes.findAllByOrderByDescriptionAsc()).thenReturn(List.of(
        StructureInspectionStatusCodeEntity.builder()
            .structureInspectionStatusCode("IN").description("from the inspection table").build()));
    when(specialAccessCodes.findAllByOrderByDescriptionAsc()).thenReturn(List.of(
        SpecialAccessRequirementCodeEntity.builder()
            .specialAccessRequirementCode("AC").description("from the access table").build()));
    when(siteTypeCodes.findAllByOrderByDescriptionAsc()).thenReturn(List.of(
        CrossingSiteTypeCodeEntity.builder()
            .crossingSiteTypeCode("TY").description("from the type table").build()));
    when(structureTypeClassCodes.findAllCurrentInDisplayOrder()).thenReturn(List.of(
        StructureTypeClassCodeEntity.builder()
            .structureTypeClassCode("TC").description("from the type class table").build()));
    when(inspectionTypeCodes.findAllByOrderByDescriptionAsc()).thenReturn(List.of(
        StrctreInspectionTypeCodeEntity.builder()
            .strctreInspectionTypeCode("IT").description("from the inspection type table").build()));
    when(inspectionReportStatusCodes.findAllByOrderByDescriptionAsc()).thenReturn(List.of(
        InspectionReportStatusCodeEntity.builder()
            .inspectionReportStatusCode("RS").description("from the report status table").build()));

    assertThat(service.getSiteStatusCodes())
        .containsExactly(new CodeOptionResponse("ST", "from the status table"));
    assertThat(service.getStructureInspectionStatusCodes())
        .containsExactly(new CodeOptionResponse("IN", "from the inspection table"));
    assertThat(service.getSpecialAccessCodes())
        .containsExactly(new CodeOptionResponse("AC", "from the access table"));
    assertThat(service.getSiteTypeCodes())
        .containsExactly(new CodeOptionResponse("TY", "from the type table"));
    assertThat(service.getStructureTypeClassCodes())
        .containsExactly(new CodeOptionResponse("TC", "from the type class table"));
    assertThat(service.getInspectionTypeCodes())
        .containsExactly(new CodeOptionResponse("IT", "from the inspection type table"));
    assertThat(service.getInspectionReportStatusCodes())
        .containsExactly(new CodeOptionResponse("RS", "from the report status table"));
  }

  @Test
  @DisplayName("keeps the order the query returned rather than sorting again")
  void preservesQueryOrder() {
    // Site statuses come back in CROSSING_SITE_STATUS_XREF.DISPLAY_ORDER, which is deliberate and
    // is not alphabetical. Re-sorting here — even incidentally, by collecting into a set — would
    // throw away the only reason that table exists.
    when(siteStatusCodes.findAllInDisplayOrder()).thenReturn(List.of(
        CrossingSiteStatusCodeEntity.builder().crossingSiteStatusCode("Z").description("Zed")
            .build(),
        CrossingSiteStatusCodeEntity.builder().crossingSiteStatusCode("A").description("Ay")
            .build()));

    assertThat(service.getSiteStatusCodes())
        .extracting(CodeOptionResponse::code)
        .containsExactly("Z", "A");
  }

  @Test
  @DisplayName("renders an org unit's number as text, because it is a <select> value")
  void mapsOrgUnits() {
    when(orgUnits.findForestDistricts())
        .thenReturn(List.of(orgUnit(1809L, "DCK", "Chilliwack Natural Resource District")));

    assertThat(service.getForestDistricts())
        .containsExactly(
            new OrgUnitResponse("1809", "DCK", "Chilliwack Natural Resource District"));
  }

  @Test
  @DisplayName("business areas and forest districts read different branches of the same view")
  void businessAreasAreNotDistricts() {
    // Both are OrgUnitResponse over CBR_ORG_UNIT, so calling the wrong repository method compiles
    // and returns a list that looks right. The branch is the only thing telling them apart.
    when(orgUnits.findForestDistricts())
        .thenReturn(List.of(orgUnit(1809L, "DCK", "a district")));
    when(orgUnits.findBusinessAreas())
        .thenReturn(List.of(orgUnit(1833L, "TCC", "a business area")));

    assertThat(service.getForestDistricts())
        .containsExactly(new OrgUnitResponse("1809", "DCK", "a district"));
    assertThat(service.getBusinessAreas())
        .containsExactly(new OrgUnitResponse("1833", "TCC", "a business area"));
  }

  @Test
  @DisplayName("business areas take no district — they are not a subdivision of one")
  void businessAreasAreUnscoped() {
    when(orgUnits.findBusinessAreas()).thenReturn(List.of());

    assertThat(service.getBusinessAreas()).isEmpty();

    verify(orgUnits).findBusinessAreas();
    verify(orgUnits, never()).findManagementAreas(anyLong());
  }

  @Test
  @DisplayName("scopes management areas to the district that was asked for")
  void passesTheDistrictThrough() {
    when(orgUnits.findManagementAreas(1812L))
        .thenReturn(List.of(orgUnit(21L, "DKA", "Thompson Rivers")));

    assertThat(service.getManagementAreas("1812"))
        .containsExactly(new OrgUnitResponse("21", "DKA", "Thompson Rivers"));
    verify(orgUnits).findManagementAreas(1812L);
  }

  @Test
  @DisplayName("a missing or malformed district is an empty list, not an error")
  void toleratesAnUnusableDistrict() {
    // Legacy's own answer: SiteSearchAction catches the NumberFormatException from `new Long(...)`
    // and forwards an empty list. The value arrives as form text, so a malformed one means the
    // dropdown was never populated — not that anything failed.
    assertThat(service.getManagementAreas(null)).isEmpty();
    assertThat(service.getManagementAreas("")).isEmpty();
    assertThat(service.getManagementAreas("   ")).isEmpty();
    assertThat(service.getManagementAreas("not-a-number")).isEmpty();

    // And it does not reach the database to find that out.
    verifyNoInteractions(orgUnits);
  }

  @Test
  @DisplayName("accepts a district number with surrounding whitespace")
  void trimsTheDistrict() {
    when(orgUnits.findManagementAreas(1809L)).thenReturn(List.of());

    assertThat(service.getManagementAreas(" 1809 ")).isEmpty();

    verify(orgUnits).findManagementAreas(1809L);
  }

  @Test
  @DisplayName("an empty table is an empty list")
  void toleratesNoRows() {
    when(siteTypeCodes.findAllByOrderByDescriptionAsc()).thenReturn(List.of());

    assertThat(service.getSiteTypeCodes()).isEmpty();
  }
}
