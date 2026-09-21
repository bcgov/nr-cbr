package ca.bc.gov.nrs.cbr.specification.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/**
 * The criteria-to-SQL translation, against a real database.
 *
 * <p>This is the test that matters most in the search path, for the reasons set out on
 * {@link SiteSearchSpecificationsTest} — a specification's failure mode is the wrong column, the
 * wrong operator, or an inner join where a left one was meant.
 *
 * <p>Two things here are not in the site equivalent and are the reason several of these tests exist:
 *
 * <ul>
 *   <li><b>An inspection has no status column.</b> Its current status is the newest row of its
 *       history, resolved by the {@code @JoinFormula} on
 *       {@link StructureInspectionEntity#getCurrentStatus()}. Everything that filters, orders or
 *       displays a status goes through it, so it is exercised directly.</li>
 *   <li><b>Two criteria are subqueries</b> rather than predicates on a column — "most recent
 *       inspections" and "previously reviewed".</li>
 * </ul>
 *
 * <p>H2 stands in for Oracle, so this proves the predicates and the joins, not Oracle's behaviour.
 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // For the query-shape tests below, which read the SQL Hibernate actually emits.
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "ca.bc.gov.nrs.cbr.specification.v1.CapturingStatementInspector",
})
class InspectionSearchSpecificationsTest {

  @Autowired
  private StructureInspectionRepository repository;

  @Autowired
  private EntityManager entityManager;

  /**
   * A structure type/class code.
   *
   * <p>Invented: the column is {@code VARCHAR2(10)} and {@code nr-mof-db} excludes code-table
   * contents, so the real values are not in source. Nothing here depends on which value it is —
   * only that the filter matches it exactly and non-matches do not.
   */
  private static final String BRIDGE = "BRDG";

  private long nextStatusId = 1L;

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM InspectionReportStatusEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM StructureInspectionEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingStructureEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CbrRoadSectionEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM OrgUnitEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM InspectionReportStatusCodeEntity").executeUpdate();
    nextStatusId = 1L;
  }

  /* ------------------------------------------------------------------ fixtures */

  /**
   * The district a site points at.
   *
   * <p>Persisted because {@code ddl-auto=create-drop} generates a real foreign key from the
   * association on {@link CrossingSiteEntity}, so a site pointing at an org unit that does not exist
   * cannot be inserted here — which is stricter than Oracle, where the column is nullable and
   * historical rows do point at units the view has no branch for.
   */
  private void givenOrgUnit(long orgUnitNo, String code, String name) {
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(orgUnitNo).orgUnitCode(code).orgUnitName(name).build());
  }

  private void givenSite(String siteId) {
    if (entityManager.find(OrgUnitEntity.class, 18L) == null) {
      givenOrgUnit(18L, "DPG", "Prince George Natural Resource District");
    }
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId(siteId)
        .crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.50"))
        .forestFileId("R00123")
        .roadSectionId("01")
        .orgUnitNo(18L)
        .managementOrgUnitNo(26L)
        .businessAreaOrgUnitNo(1833L)
        .clientNumber("00001012")
        .build());
  }

  private void givenStructure(long structureId, String name, String siteId) {
    givenStructure(structureId, name, siteId, BRIDGE, "N");
  }

  private void givenStructure(
      long structureId, String name, String siteId, String typeClass, String closeProximity) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(structureId)
        .crossingStructureName(name)
        .crossingSiteId(siteId)
        .structureTypeClassCode(typeClass)
        .closeProximityInd(closeProximity)
        .activeInd("Y")
        .build());
  }

  /** An inspection plus one status-history row, which is what gives it a current status. */
  private void givenInspection(long id, long structureId, LocalDate date, String status) {
    givenInspection(id, structureId, date, "site-1", "J. Smith", null, status);
  }

  private void givenInspection(
      long id,
      long structureId,
      LocalDate date,
      String siteAtTimeOfInspection,
      String inspectorName,
      Long reviewerId,
      String... statuses) {
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(id)
        .crossingStructureId(structureId)
        .inspectionDate(date)
        .siteAtTimeOfInspection(siteAtTimeOfInspection)
        .inspectorName(inspectorName)
        .inspectionReviewerId(reviewerId)
        .strctreInspectionTypeCode("ROUT")
        .build());
    for (String status : statuses) {
      givenStatusRow(id, status);
    }
  }

  /**
   * History rows are appended, so the last one written is the current status.
   *
   * <p>The code-table row is created on demand: {@code ddl-auto=create-drop} turns the association
   * on {@link InspectionReportStatusEntity} into a real foreign key, so a history row naming a code
   * that does not exist cannot be inserted here.
   */
  private void givenStatusRow(long inspectionId, String code) {
    if (entityManager.find(InspectionReportStatusCodeEntity.class, code) == null) {
      givenStatusCode(code, code);
    }
    entityManager.persist(InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(nextStatusId++)
        .inspectionId(inspectionId)
        .inspectionReportStatusCode(code)
        .entryUserid("IDIR\\TESTER")
        .entryTimestamp(LocalDateTime.now())
        .build());
  }

  private void givenStatusCode(String code, String description) {
    if (entityManager.find(InspectionReportStatusCodeEntity.class, code) != null) {
      return;
    }
    entityManager.persist(InspectionReportStatusCodeEntity.builder()
        .inspectionReportStatusCode(code)
        .description(description)
        .effectiveDate(LocalDateTime.now().minusYears(20))
        .expiryDate(LocalDateTime.now().plusYears(10))
        .updateTimestamp(LocalDateTime.now())
        .build());
  }

  /** One complete inspection: site, structure, inspection, status. */
  private void givenCompleteInspection(long id, String status) {
    givenSite("site-1");
    givenStructure(100L, "BR000001", "site-1");
    givenInspection(id, 100L, LocalDate.of(2026, 6, 15), status);
  }

  private List<Long> search(InspectionSearchCriteria criteria) {
    return repository
        .findAll(InspectionSearchSpecifications.matching(criteria), PageRequest.of(0, 50))
        .getContent()
        .stream()
        .map(StructureInspectionEntity::getInspectionId)
        .toList();
  }

  private static InspectionSearchCriteria.InspectionSearchCriteriaBuilder criteria() {
    return InspectionSearchCriteria.builder()
        .sortBy(InspectionSearchCriteria.STRUCTURE_ID_DATE_SORT);
  }

  /* ------------------------------------------------------------------ the tests */

  @Nested
  @DisplayName("the current status")
  class CurrentStatus {

    @Test
    @DisplayName("is the newest history row, not the first or the only one")
    void resolvesTheLatestStatus() {
      // There is no status column on STRUCTURE_INSPECTION. Legacy resolves this with
      // CBR.GET_LAST_STATUS_ID, which is MAX(INSPECTION_REPORT_STATUS_ID) for the inspection.
      givenSite("site-1");
      givenStructure(100L, "BR000001", "site-1");
      givenInspection(1L, 100L, LocalDate.of(2026, 6, 15), "site-1", "J. Smith", null,
          "PRO", "SUB", "RVD");

      assertThat(search(criteria().inspectionReportStatusCode("RVD").build())).containsExactly(1L);
      assertThat(search(criteria().inspectionReportStatusCode("SUB").build())).isEmpty();
      assertThat(search(criteria().inspectionReportStatusCode("PRO").build())).isEmpty();
    }

    @Test
    @DisplayName("excludes an inspection with no history at all, as legacy's inner join does")
    void requiresAStatus() {
      givenSite("site-1");
      givenStructure(100L, "BR000001", "site-1");
      givenInspection(1L, 100L, LocalDate.of(2026, 6, 15), "site-1", "J. Smith", null);

      assertThat(search(criteria().siteId("site-1").build())).isEmpty();
    }
  }

  @Nested
  @DisplayName("the joins")
  class Joins {

    @Test
    @DisplayName("an inspection whose site has no road section is still found by other criteria")
    void roadSectionIsOptional() {
      // The road section comes from a materialized view over a DB link and is routinely absent.
      // An inner join here would drop those inspections from every search.
      givenCompleteInspection(1L, "SUB");

      assertThat(search(criteria().siteId("site-1").build())).containsExactly(1L);
    }

    @Test
    @DisplayName("an inspection whose site has no org unit is still returned")
    void orgUnitIsOptional() {
      // Divergence 3: legacy INNER JOINs CBR_ORG_UNIT, so a site pointing at an org unit the view
      // has no branch for disappears from the results entirely. Here it appears, with a blank
      // District Code — which is what the legacy screen shows for such a site anyway, when it shows
      // it at all.
      //
      // The site is built here rather than through givenSite() because that helper always points at
      // a district; the whole point of this case is a site that does not.
      entityManager.persist(CrossingSiteEntity.builder()
          .crossingSiteId("site-orphan")
          .crossingName("Deadman Creek")
          .orgUnitNo(null)
          .build());
      givenStructure(300L, "BR000003", "site-orphan");
      givenInspection(2L, 300L, LocalDate.of(2026, 6, 15), "SUB");

      assertThat(search(criteria().siteId("site-orphan").build())).containsExactly(2L);
    }

    @Test
    @DisplayName("filters on the road section name when one is asked for")
    void filtersByForestServiceRoad() {
      entityManager.persist(CbrRoadSectionEntity.builder()
          .forestFileId("R00123").roadSectionId("01").roadSectName("Deadman FSR").build());
      givenCompleteInspection(1L, "SUB");

      assertThat(search(criteria().forestServiceRoad("Deadman").build())).containsExactly(1L);
      assertThat(search(criteria().forestServiceRoad("Nowhere").build())).isEmpty();
    }
  }

  @Nested
  @DisplayName("text criteria are a partial match, as legacy's Search.LIKE is")
  class TextCriteria {

    @Test
    @DisplayName("structure name and inspector name match on a fragment")
    void partialMatches() {
      givenCompleteInspection(1L, "SUB");

      assertThat(search(criteria().structureName("000001").build())).containsExactly(1L);
      assertThat(search(criteria().inspectorName("Smith").build())).containsExactly(1L);
      assertThat(search(criteria().structureName("BR999").build())).isEmpty();
    }

    @Test
    @DisplayName("code criteria are an exact match")
    void codesAreExact() {
      givenCompleteInspection(1L, "SUB");

      assertThat(search(criteria().inspectionTypeCode("ROUT").build())).containsExactly(1L);
      assertThat(search(criteria().inspectionTypeCode("ROU").build())).isEmpty();
    }
  }

  @Nested
  @DisplayName("the inspection date range")
  class DateRange {

    @BeforeEach
    void inspections() {
      givenSite("site-1");
      givenStructure(100L, "BR000001", "site-1");
      givenInspection(1L, 100L, LocalDate.of(2026, 1, 1), "SUB");
      givenInspection(2L, 100L, LocalDate.of(2026, 6, 15), "SUB");
      givenInspection(3L, 100L, LocalDate.of(2026, 12, 31), "SUB");
    }

    @Test
    @DisplayName("includes the whole of both months — divergence 1")
    void bothBoundsAreInclusiveWholeMonths() {
      // Legacy emits BETWEEN TO_DATE('2026/01','yyyy/MM') AND TO_DATE('2026/12','yyyy/MM'), and
      // TO_DATE resolves a month to its FIRST day — so legacy silently omits 2-31 December.
      assertThat(search(criteria().inspectionDateStart("2026/01").inspectionDateEnd("2026/12")
          .build())).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("a start month on its own includes the first of that month")
    void startIsInclusive() {
      // Legacy uses Search.GREATER_THAN — literally ">" — against the 1st, so it drops 1 January.
      assertThat(search(criteria().inspectionDateStart("2026/01").build()))
          .containsExactlyInAnyOrder(1L, 2L, 3L);
      assertThat(search(criteria().inspectionDateStart("2026/07").build())).containsExactly(3L);
    }

    @Test
    @DisplayName("an end month on its own includes the last of that month")
    void endIsInclusive() {
      assertThat(search(criteria().inspectionDateEnd("2026/12").build()))
          .containsExactlyInAnyOrder(1L, 2L, 3L);
      assertThat(search(criteria().inspectionDateEnd("2026/05").build())).containsExactly(1L);
    }

    @Test
    @DisplayName("a one-digit month is accepted, as SimpleDateFormat and TO_DATE both accept it")
    void acceptsAOneDigitMonth() {
      assertThat(search(criteria().inspectionDateStart("2026/6").inspectionDateEnd("2026/6")
          .build())).containsExactly(2L);
    }
  }

  @Nested
  @DisplayName("Site # and structures at previous sites")
  class SiteCriteria {

    @BeforeEach
    void inspections() {
      givenSite("site-1");
      givenSite("site-2");
      givenStructure(100L, "BR000001", "site-1");
      givenStructure(200L, "BR000002", "site-2");
      // Inspection 2 is of a structure now at site-2, but it was inspected while at site-1.
      givenInspection(1L, 100L, LocalDate.of(2026, 6, 15), "site-1", "J. Smith", null, "SUB");
      givenInspection(2L, 200L, LocalDate.of(2026, 6, 16), "site-1", "J. Smith", null, "SUB");
    }

    @Test
    @DisplayName("matches the structure's current site by default")
    void matchesCurrentSite() {
      assertThat(search(criteria().siteId("site-1").build())).containsExactly(1L);
    }

    @Test
    @DisplayName("also matches where the structure stood at the time, when the toggle is on")
    void includesMovedStructures() {
      assertThat(search(criteria().siteId("site-1").findMovedStructures(true).build()))
          .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("the toggle does nothing on its own, because it only changes what Site # means")
    void toggleAloneDoesNothing() {
      assertThat(search(criteria().findMovedStructures(true).build()))
          .containsExactlyInAnyOrder(1L, 2L);
    }
  }

  @Nested
  @DisplayName("the two subquery criteria")
  class SubqueryCriteria {

    @Test
    @DisplayName("most recent inspections keeps each structure's latest only")
    void mostRecentInspections() {
      givenSite("site-1");
      givenStructure(100L, "BR000001", "site-1");
      givenStructure(200L, "BR000002", "site-1");
      givenInspection(1L, 100L, LocalDate.of(2024, 6, 15), "SUB");
      givenInspection(2L, 100L, LocalDate.of(2026, 6, 15), "SUB");
      givenInspection(3L, 200L, LocalDate.of(2025, 1, 1), "SUB");

      assertThat(search(criteria().mostRecentInspections(true).build()))
          .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("previously reviewed finds inspections that left RVD, not ones sitting in it")
    void findChangedReviewed() {
      // The legacy field is findChangedReviewed, and that is what it means: reviewed, then changed.
      // Moving an inspection off RVD nulls its reviewer, so these history rows are the only
      // surviving evidence it was ever reviewed.
      givenSite("site-1");
      givenStructure(100L, "BR000001", "site-1");
      givenInspection(1L, 100L, LocalDate.of(2026, 1, 1), "site-1", "J. Smith", null, "RVD", "SUB");
      givenInspection(2L, 100L, LocalDate.of(2026, 2, 1), "site-1", "J. Smith", null, "PRO", "RVD");
      givenInspection(3L, 100L, LocalDate.of(2026, 3, 1), "site-1", "J. Smith", null, "SUB");

      assertThat(search(criteria().findChangedReviewed(true).build())).containsExactly(1L);
    }
  }

  @Nested
  @DisplayName("the toggles and the org units")
  class TogglesAndOrgUnits {

    @Test
    @DisplayName("close proximity filters one way only")
    void closeProximityIsOneWay() {
      givenSite("site-1");
      givenStructure(100L, "BR000001", "site-1", BRIDGE, "Y");
      givenStructure(200L, "BR000002", "site-1", BRIDGE, "N");
      givenInspection(1L, 100L, LocalDate.of(2026, 6, 15), "SUB");
      givenInspection(2L, 200L, LocalDate.of(2026, 6, 16), "SUB");

      assertThat(search(criteria().closeProximity(true).build())).containsExactly(1L);
      // Switched off it is not a filter at all — not "the ones that do not require it".
      assertThat(search(criteria().closeProximity(false).siteId("site-1").build()))
          .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("district, management area and business area each read their own column")
    void orgUnitsAreDistinct() {
      // Three numeric columns on one row: crossing them over compiles and returns plausible rows.
      givenCompleteInspection(1L, "SUB");

      assertThat(search(criteria().orgUnitNo("18").build())).containsExactly(1L);
      assertThat(search(criteria().managementOrgUnitNo("26").build())).containsExactly(1L);
      assertThat(search(criteria().businessAreaOrgUnitNo("1833").build())).containsExactly(1L);

      assertThat(search(criteria().orgUnitNo("26").build())).isEmpty();
      assertThat(search(criteria().managementOrgUnitNo("1833").build())).isEmpty();
      assertThat(search(criteria().businessAreaOrgUnitNo("18").build())).isEmpty();
    }
  }

  @Nested
  @DisplayName("ordering and paging")
  class OrderingAndPaging {

    @BeforeEach
    void inspections() {
      givenSite("site-1");
      givenStructure(100L, "BR000002", "site-1");
      givenStructure(200L, "BR000001", "site-1");
      givenInspection(1L, 100L, LocalDate.of(2026, 1, 1), "SUB");
      givenInspection(2L, 100L, LocalDate.of(2026, 6, 1), "SUB");
      givenInspection(3L, 200L, LocalDate.of(2026, 3, 1), "SUB");
    }

    @Test
    @DisplayName("structure sort is name ascending, then date descending")
    void structureSort() {
      assertThat(search(criteria().siteId("site-1")
          .sortBy(InspectionSearchCriteria.STRUCTURE_ID_DATE_SORT).build()))
          .containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("project sort falls through to date descending when the site columns tie")
    void projectSort() {
      assertThat(search(criteria().siteId("site-1")
          .sortBy(InspectionSearchCriteria.PROJECT_BRANCH_KM_DATE_SORT).build()))
          .containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("the count query runs without the fetches or the ordering")
    void countsWithoutFetching() {
      // Spring Data derives the count from this same specification. A fetch join or an ORDER BY in
      // a count is invalid, and Hibernate rejects it — so this failing means the guard has gone.
      assertThat(repository.findAll(
          InspectionSearchSpecifications.matching(criteria().siteId("site-1").build()),
          PageRequest.of(0, 2)).getTotalElements()).isEqualTo(3L);
    }
  }

  @Nested
  @DisplayName("the shape of the query")
  class QueryShape {

    /** A page of five inspections of one structure, with every association present. */
    private void givenAPageOfResults() {
      givenSite("site-1");
      givenStructure(100L, "BR000001", "site-1");
      entityManager.persist(CbrRoadSectionEntity.builder()
          .forestFileId("R00123").roadSectionId("01").roadSectName("Deadman FSR").build());
      for (long id = 1; id <= 5; id++) {
        givenInspection(id, 100L, LocalDate.of(2026, 6, (int) id), "SUB");
      }
      entityManager.flush();
      entityManager.clear();
      CapturingStatementInspector.clear();
    }

    /**
     * One page, asked for with room to spare.
     *
     * <p>A page size larger than the number of matches means Spring Data can tell the total from
     * the rows it already has and skips the count query, so what these tests count is the page
     * query alone. The count query has its own test in {@link OrderingAndPaging}.
     */
    private List<StructureInspectionEntity> page() {
      return repository
          .findAll(
              InspectionSearchSpecifications.matching(criteria().siteId("site-1").build()),
              PageRequest.of(0, 20))
          .getContent();
    }

    @Test
    @DisplayName("reads a page in one statement, with nothing loaded lazily per row")
    void oneStatementPerPage() {
      // Every value a results row shows comes from an association, so any one left unfetched is an
      // extra select per row. The org unit was exactly that: joined for the ordering, never
      // fetched, and so fetched again once per row when the row was rendered.
      givenAPageOfResults();

      List<StructureInspectionEntity> found = page();
      // Touch everything a results row renders. Anything unfetched issues its select here.
      found.forEach(inspection -> {
        inspection.getCurrentStatus().getStatusCode().getDescription();
        inspection.getStructure().getSite().getOrgUnit();
        inspection.getStructure().getSite().getRoadSection();
      });

      assertThat(found).hasSize(5);
      assertThat(CapturingStatementInspector.count())
          .as("one select for the page, and no per-row follow-ups")
          .isEqualTo(1);
    }

    @Test
    @DisplayName("joins each table once, not once per use")
    void joinsEachTableOnce() {
      // root.fetch(X) is a new join every time — it does not reuse an earlier root.join(X). An
      // earlier version made plain joins for the predicates and fetches for the projection, and the
      // emitted SQL joined CROSSING_STRUCTURE, CROSSING_SITE and INSPECTION_REPORT_STATUS twice
      // each. The duplicated status join is the expensive one: it made the current-status subquery
      // run twice per candidate row, and that subquery scans the whole status history because
      // INSPECTION_REPORT_STATUS is indexed on its primary key alone, with nothing on INSPECTION_ID.
      givenAPageOfResults();

      page();
      String sql = CapturingStatementInspector.lastContaining("structure_inspection");

      assertThat(sql).isNotEmpty();
      assertThat(CapturingStatementInspector.occurrences(sql, "crossing_structure ")).isEqualTo(1);
      assertThat(CapturingStatementInspector.occurrences(sql, "crossing_site ")).isEqualTo(1);
      assertThat(CapturingStatementInspector.occurrences(sql, "inspection_report_status "))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("resolves the current status with one subquery, not one per join")
    void oneCurrentStatusSubquery() {
      givenAPageOfResults();

      page();
      String sql = CapturingStatementInspector.lastContaining("structure_inspection");

      assertThat(CapturingStatementInspector.occurrences(sql, "MAX(h.INSPECTION_REPORT_STATUS_ID)"))
          .as("the correlated subquery is the most expensive thing in this query")
          .isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("with no criteria")
  class NoCriteria {

    @Test
    @DisplayName("the specification itself matches everything — the refusal is the service's")
    void matchesEverything() {
      givenCompleteInspection(1L, "SUB");

      assertThat(search(criteria().build())).containsExactly(1L);
    }
  }

  @Nested
  @DisplayName("the status description")
  class StatusDescription {

    @Test
    @DisplayName("is reachable through the current status's code table row")
    void resolvesTheDescription() {
      givenStatusCode("SUB", "Submitted");
      givenCompleteInspection(1L, "SUB");
      entityManager.flush();
      entityManager.clear();

      StructureInspectionEntity found = repository
          .findAll(InspectionSearchSpecifications.matching(criteria().siteId("site-1").build()),
              PageRequest.of(0, 5))
          .getContent()
          .getFirst();

      assertThat(found.getCurrentStatus().getInspectionReportStatusCode()).isEqualTo("SUB");
      assertThat(found.getCurrentStatus().getStatusCode().getDescription()).isEqualTo("Submitted");
    }
  }
}
