package ca.bc.gov.nrs.cbr.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.RecreationDistrictXrefEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * The three org-unit queries, against a real database.
 *
 * <p>Worth testing beyond "do the method names parse": these are the only lookups whose predicates
 * select rather than just sort, and what they select on — {@code ORG_UNIT_TYPE} — is the view's
 * stamp for which branch a row came from, not a column of any table.
 *
 * <p><b>H2 stands in for Oracle, and it is a flat table rather than the view.</b> That is the limit
 * of what these prove: the fixtures below set {@code orgUnitType} by hand, where in production the
 * view derives it. So this covers the half of the rule that lives in the repository — the right
 * branch, scoped to the right district, ordered by name — and not the half that lives in the view,
 * which decides which rows carry which stamp. A row that the view could never produce can be
 * persisted here, so a test asserting the view's own filters would be asserting the fixture.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class CbrOrgUnitRepositoryTest {

  private static final LocalDateTime LONG_AGO = LocalDateTime.now().minusYears(20);
  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(1);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(10);

  @Autowired
  private CbrOrgUnitRepository repository;

  @Autowired
  private EntityManager entityManager;

  /** A current district: the view's {@code D} branch — its own rollup, still in effect. */
  private void givenDistrict(long orgUnitNo, String code, String name) {
    entityManager.persist(CbrOrgUnitEntity.builder()
        .orgUnitNo(orgUnitNo)
        .orgUnitCode(code)
        .orgUnitName(name)
        .orgLevelCode("D")
        .orgUnitType(CbrOrgUnitRepository.CURRENT_DISTRICT)
        .rollupDistNo(orgUnitNo)
        .effectiveDate(LONG_AGO)
        .expiryDate(FUTURE)
        .build());
  }

  /**
   * An obsolete district — a management area — now rolling up into {@code rollupDistNo}. The view's
   * {@code O} branch, which is the same {@code ORG_LEVEL_CODE} as a current district and differs
   * only in being expired.
   */
  private void givenObsoleteDistrict(long orgUnitNo, String code, String name, long rollupDistNo) {
    entityManager.persist(CbrOrgUnitEntity.builder()
        .orgUnitNo(orgUnitNo)
        .orgUnitCode(code)
        .orgUnitName(name)
        .orgLevelCode("D")
        .orgUnitType(CbrOrgUnitRepository.OBSOLETE_DISTRICT)
        .rollupDistNo(rollupDistNo)
        .effectiveDate(LONG_AGO)
        .expiryDate(PAST)
        .build());
  }

  /** A BCTS business area: the view's {@code T} branch, which carries no rollup district. */
  private void givenBusinessArea(long orgUnitNo, String code, String name) {
    entityManager.persist(CbrOrgUnitEntity.builder()
        .orgUnitNo(orgUnitNo)
        .orgUnitCode(code)
        .orgUnitName(name)
        .orgLevelCode("T")
        .orgUnitType(CbrOrgUnitRepository.BUSINESS_AREA)
        .rollupDistNo(null)
        .effectiveDate(LONG_AGO)
        .expiryDate(FUTURE)
        .build());
  }

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CbrOrgUnitEntity").executeUpdate();
  }

  @Test
  @DisplayName("forest districts are the current ones, by name")
  void findsCurrentDistrictsByName() {
    givenDistrict(21L, "DKA", "Thompson Rivers");
    givenDistrict(18L, "DPG", "Prince George");

    assertThat(repository.findForestDistricts())
        .extracting(CbrOrgUnitEntity::getOrgUnitName)
        .containsExactly("Prince George", "Thompson Rivers");
  }

  @Test
  @DisplayName("forest districts exclude retired districts and every other kind of org unit")
  void excludesRetiredDistrictsAndNonDistricts() {
    givenDistrict(18L, "DPG", "Prince George");
    givenObsoleteDistrict(26L, "DRV", "Robson Valley", 18L);
    // A business area — the view's T branch. Note it shares neither the type nor a rollup district.
    givenBusinessArea(1833L, "RNI", "North Area");

    assertThat(repository.findForestDistricts())
        .extracting(CbrOrgUnitEntity::getOrgUnitName)
        .containsExactly("Prince George");
  }

  @Test
  @DisplayName("business areas are the T branch, by name")
  void findsBusinessAreasByName() {
    givenBusinessArea(1835L, "TSN", "Seaward-Tlasta Business Area");
    givenBusinessArea(1833L, "TBA", "Babine Business Area");

    assertThat(repository.findBusinessAreas())
        .extracting(CbrOrgUnitEntity::getOrgUnitName)
        .containsExactly("Babine Business Area", "Seaward-Tlasta Business Area");
  }

  @Test
  @DisplayName("business areas exclude districts, current and retired alike")
  void businessAreasExcludeDistricts() {
    // BCTS runs its own geography: a business area is not a level of the district hierarchy, and a
    // site records one in BUSINESS_AREA_ORG_UNIT_NO alongside its district in ORG_UNIT_NO. A query
    // that leaned on ORG_LEVEL_CODE alone would still separate these, but only by accident — the
    // branch is what says which question is being asked.
    givenBusinessArea(1833L, "TBA", "Babine Business Area");
    givenDistrict(18L, "DPG", "Prince George");
    givenObsoleteDistrict(26L, "DRV", "Robson Valley", 18L);

    assertThat(repository.findBusinessAreas())
        .extracting(CbrOrgUnitEntity::getOrgUnitName)
        .containsExactly("Babine Business Area");
  }

  @Test
  @DisplayName("management areas are the FORMER districts that roll up into the one selected")
  void findsObsoleteDistrictsForTheSelectedDistrict() {
    // This is how CBR asks "which of the old districts was the site originally recorded under?".
    givenDistrict(18L, "DPG", "Prince George");
    givenObsoleteDistrict(26L, "DRV", "Robson Valley", 18L);
    givenObsoleteDistrict(27L, "DVA", "Vanderhoof", 18L);
    givenObsoleteDistrict(17L, "DCW", "Clearwater", 21L);

    assertThat(repository.findManagementAreas(18L))
        .extracting(CbrOrgUnitEntity::getOrgUnitName)
        .containsExactly("Robson Valley", "Vanderhoof");
  }

  @Test
  @DisplayName("management areas exclude the selected district itself")
  void excludesTheCurrentDistrict() {
    // A current district is its own rollup, so a query that did not separate the two branches would
    // list every district as one of its own management areas.
    givenDistrict(18L, "DPG", "Prince George");

    assertThat(repository.findManagementAreas(18L)).isEmpty();
  }

  @Test
  @DisplayName("management areas of one district are not offered for another")
  void scopesToTheSelectedDistrict() {
    givenObsoleteDistrict(26L, "DRV", "Robson Valley", 18L);

    assertThat(repository.findManagementAreas(21L)).isEmpty();
  }

  @Test
  @DisplayName("a district with no former districts is an empty list")
  void toleratesNoManagementAreas() {
    assertThat(repository.findManagementAreas(1809L)).isEmpty();
  }

  /** A recreation district: the view's {@code RD} branch, in effect today. */
  private void givenRecreationDistrict(long orgUnitNo, String code, String name) {
    entityManager.persist(CbrOrgUnitEntity.builder()
        .orgUnitNo(orgUnitNo)
        .orgUnitCode(code)
        .orgUnitName(name)
        .orgLevelCode("D")
        .orgUnitType(CbrOrgUnitRepository.RECREATION_DISTRICT)
        .effectiveDate(LONG_AGO)
        .expiryDate(FUTURE)
        .build());
  }

  private void givenFileInDistrict(String forestFileId, String districtCode) {
    entityManager.persist(RecreationDistrictXrefEntity.builder()
        .forestFileId(forestFileId).recreationDistrictCode(districtCode).build());
  }

  @Test
  @DisplayName("offers only the recreation districts the project file belongs to")
  void recreationDistrictsAreScopedToTheFile() {
    // Where a crossing takes its district from the road, a recreation site takes its *choices*
    // from the file and the user picks among them.
    givenRecreationDistrict(1L, "RDA", "Cariboo Recreation");
    givenRecreationDistrict(2L, "RDB", "Kootenay Recreation");
    givenFileInDistrict("R00123", "RDA");
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findRecreationDistricts("R00123"))
        .extracting(CbrOrgUnitEntity::getOrgUnitName)
        .containsExactly("Cariboo Recreation");
  }

  @Test
  @DisplayName("offers nothing for a file that is cross-referenced to none")
  void answersNothingForAnUnknownFile() {
    givenRecreationDistrict(1L, "RDA", "Cariboo Recreation");
    givenFileInDistrict("R00123", "RDA");
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findRecreationDistricts("R99999")).isEmpty();
  }

  @Test
  @DisplayName("leaves out a recreation district that has expired")
  void excludesExpiredDistricts() {
    givenRecreationDistrict(1L, "RDA", "Cariboo Recreation");
    entityManager.persist(CbrOrgUnitEntity.builder()
        .orgUnitNo(2L).orgUnitCode("RDB").orgUnitName("Retired Recreation")
        .orgLevelCode("D").orgUnitType(CbrOrgUnitRepository.RECREATION_DISTRICT)
        .effectiveDate(LONG_AGO).expiryDate(PAST).build());
    givenFileInDistrict("R00123", "RDA");
    givenFileInDistrict("R00123", "RDB");
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findRecreationDistricts("R00123"))
        .extracting(CbrOrgUnitEntity::getOrgUnitCode)
        .containsExactly("RDA");
  }

  @Test
  @DisplayName("does not mistake a forest district for a recreation one")
  void excludesOtherBranches() {
    // They are different kinds of org unit, and the cross-reference joins on a code that a forest
    // district could coincidentally share.
    givenDistrict(3L, "RDA", "Prince George");
    givenFileInDistrict("R00123", "RDA");
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findRecreationDistricts("R00123")).isEmpty();
  }
}
