package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The three org-unit lookups, all over the {@code THE.CBR_ORG_UNIT} view.
 *
 * <p>Read-only: the view is a union of six {@code SELECT}s and nothing here writes through it. The
 * inherited {@code JpaRepository} write methods are unusable against it and would fail at the
 * database, not here.
 *
 * <h2>Why these select on {@code ORG_UNIT_TYPE}</h2>
 * The legacy procedures spell their predicates out —
 * {@code ROLLUP_DIST_NO = ORG_UNIT_NO AND SYSDATE BETWEEN EFFECTIVE_DATE AND EXPIRY_DATE} for
 * districts, and an inverted {@code SYSDATE > EXPIRY_DATE} plus a code length and a level for
 * management areas. Both are re-derivations of something the view has already decided: each branch
 * of the union stamps an {@code ORG_UNIT_TYPE}, and those two predicates each select exactly one
 * branch. Business areas are the clearest case of all: the legacy predicate and the {@code T}
 * branch's {@code WHERE} clause are the same three conditions.
 *
 * <p>Naming the branch is equivalent, and the equivalence is total rather than approximate:
 * {@code ROLLUP_DIST_NO} is {@code NULL} in the {@code T}, {@code R}, {@code A} and {@code RD}
 * branches, so nothing outside {@code D}/{@code O} can match either predicate; the {@code D} branch
 * is defined by the district predicate; and every {@code O} row already carries the code length and
 * level the management-area query was testing for. The {@code O} branch additionally drops the
 * headwater district ({@code ORG_UNIT_NO 1827}, split into Clearwater and Robson Valley), which the
 * legacy query relied on without saying so.
 *
 * <p>What this buys is that the queries are derived from their method names, with no JPQL to read
 * past, and that the management-area rule stops being an inverted date comparison whose meaning
 * lives in a comment. What it costs is that they are anchored on the view's own stamping rather than
 * on the legacy {@code WHERE} clause, so a side-by-side against the procedures is no longer literal.
 * That is a reasonable trade for a view CBR does not own: the branch definitions would have to
 * change for it to bite, and that would break the legacy procedures in the same breath.
 */
@Repository
public interface CbrOrgUnitRepository extends JpaRepository<CbrOrgUnitEntity, Long> {

  /** A district in use. */
  String CURRENT_DISTRICT = "D";

  /**
   * A district that has been retired into another one — what CBR calls a management area.
   *
   * <p>Same {@code ORG_LEVEL_CODE} as a current district; only the branch tells them apart.
   */
  String OBSOLETE_DISTRICT = "O";

  /**
   * A BCTS business area.
   *
   * <p>The one branch of the view that is not a district at all: BC Timber Sales runs its own
   * geography, and {@code CROSSING_SITE.BUSINESS_AREA_ORG_UNIT_NO} records it independently of the
   * district in {@code ORG_UNIT_NO}. A site can carry both, which is why this is a third filter on
   * the search form rather than a level of the same hierarchy.
   */
  String BUSINESS_AREA = "T";

  List<CbrOrgUnitEntity> findAllByOrgUnitTypeOrderByOrgUnitNameAsc(String orgUnitType);

  List<CbrOrgUnitEntity> findAllByOrgUnitTypeAndRollupDistNoOrderByOrgUnitNameAsc(
      String orgUnitType, Long rollupDistNo);

  /**
   * The current forest districts, by name — the Forest District dropdown.
   *
   * <p>Replaces {@code CBR.FIND_FOREST_DISTRICTS}.
   */
  default List<CbrOrgUnitEntity> findForestDistricts() {
    return findAllByOrgUnitTypeOrderByOrgUnitNameAsc(CURRENT_DISTRICT);
  }

  /**
   * The BCTS business areas, by name — the BCTS Business Area dropdown.
   *
   * <p>Replaces {@code CBR.FIND_BUSINESS_AREAS}, whose predicate is
   * {@code ORG_LEVEL_CODE = 'T' AND LENGTH(ORG_UNIT_CODE) = 3 AND SYSDATE BETWEEN EFFECTIVE_DATE
   * AND EXPIRY_DATE} — which is, character for character, the definition of the view's {@code T}
   * branch. Naming the branch is not an approximation of that procedure here; it is the same three
   * conditions, applied once instead of twice.
   */
  default List<CbrOrgUnitEntity> findBusinessAreas() {
    return findAllByOrgUnitTypeOrderByOrgUnitNameAsc(BUSINESS_AREA);
  }

  /**
   * The management areas within one forest district, by name — the Management Area dropdown.
   *
   * <p>Replaces {@code CBR.FIND_MANAGEMENT_AREAS_BY_SLCTN}. A CBR management area is a
   * <em>former</em> district, and {@code ROLLUP_DIST_NO} on those rows is the current district that
   * absorbed it — the view hand-redirects two of them, Clearwater to Thompson Valley and Robson
   * Valley to Prince George. So picking a Forest District and then a Management Area is asking
   * "within this district, which of the old districts was the site originally recorded under?",
   * which is what {@code CROSSING_SITE.MANAGEMENT_ORG_UNIT_NO} holds.
   *
   * <p>A current district is its own rollup, so it is never one of its own management areas.
   *
   * @param forestDistrictOrgUnitNo the selected district's {@code ORG_UNIT_NO}
   */
  default List<CbrOrgUnitEntity> findManagementAreas(Long forestDistrictOrgUnitNo) {
    return findAllByOrgUnitTypeAndRollupDistNoOrderByOrgUnitNameAsc(
        OBSOLETE_DISTRICT, forestDistrictOrgUnitNo);
  }
}
