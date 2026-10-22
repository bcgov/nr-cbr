package ca.bc.gov.nrs.cbr.specification.v1;

import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsNumber;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsText;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.integerRange;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementStructuresCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * Which structures the Replacement Structures Summary lists, as one JPA criteria query selecting
 * what the report prints ({@link ReplacementStructureLine}).
 *
 * <p>Replaces the {@code WHERE} clause legacy's {@code ReplacementStructureSummaryReportForm
 * .createSearch} and {@code OracleReportQueryDAO.generateReportQuery} assemble as a string, which
 * {@code CBR_REPORTING.RPLCMNT_STRUCTURE_RPT} applied to each of its three sections alike. Its rules
 * are legacy's: an active structure; codes and org units exact; Capital Road, when asked for, a
 * capital road. Both year ranges pick the structure, whichever section it is then listed under, and
 * include both their ends, where legacy's one-sided range left its own end out. A structure is
 * listed only if it has a year for at least one section; which sections, and in what order, is the
 * report's ({@code ReplacementStructuresRows}).
 */
public final class ReplacementStructuresReportSpecifications {

  private static final String STRUCTURE_ID = "crossingStructureId";
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String ACTIVE_IND = "activeInd";
  private static final String TYPE_CLASS = "structureTypeClassCode";
  private static final String DESIGN_LOAD_RATING = "designLoadRating";
  private static final String CURRENT_LOAD_RATING = "currentLoadRating";
  private static final String END_OF_DESIGN_LIFE = "originalReplacementYear";
  private static final String REPLACEMENT_COST = "estimatedReplacementCost";
  private static final String LOAD_RESTRICTION_YEAR = "fullLogHaulReplacementYear";
  private static final String REPLACEMENT_YEAR = "lightVehicleReplacementYear";
  private static final String CLOSURE_YEAR = "estimatedClosureYear";

  private static final String SITE = "site";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ORG_UNIT = "orgUnit";
  private static final String SITE_ID = "crossingSiteId";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String CROSSING_NAME = "crossingName";
  private static final String CLIENT_NUMBER = "clientNumber";
  private static final String CLIENT_LOCATION = "clientLocnCode";
  private static final String CAPITAL_ROAD = "capitalRoadInd";
  private static final String SITE_STATUS = "crossingSiteStatusCode";
  private static final String SITE_TYPE = "crossingSiteTypeCode";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String ROLLUP_REGION_NO = "rollupRegionNo";
  private static final String MANAGEMENT_ORG_UNIT_NO = "managementOrgUnitNo";
  private static final String BUSINESS_AREA_ORG_UNIT_NO = "businessAreaOrgUnitNo";

  private static final String YES = "Y";

  private ReplacementStructuresReportSpecifications() {}

  /** The structures listed for these criteria, by id: the report puts them in its own order. */
  public static CriteriaQuery<ReplacementStructureLine> lines(
      CriteriaBuilder builder, ReplacementStructuresCriteria criteria) {
    CriteriaQuery<ReplacementStructureLine> query =
        builder.createQuery(ReplacementStructureLine.class);
    Root<CrossingStructureEntity> structure = query.from(CrossingStructureEntity.class);
    From<?, ?> site = structure.join(SITE, JoinType.LEFT);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);
    From<?, ?> district = site.join(ORG_UNIT, JoinType.LEFT);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(structure.get(ACTIVE_IND), YES));
    predicates.add(builder.or(
        builder.isNotNull(structure.get(LOAD_RESTRICTION_YEAR)),
        builder.isNotNull(structure.get(REPLACEMENT_YEAR)),
        builder.isNotNull(structure.get(CLOSURE_YEAR))));
    integerRange(builder, structure.get(LOAD_RESTRICTION_YEAR),
        criteria.loadRestrictionYearStart(), criteria.loadRestrictionYearEnd())
        .ifPresent(predicates::add);
    integerRange(builder, structure.get(REPLACEMENT_YEAR),
        criteria.replacementYearStart(), criteria.replacementYearEnd())
        .ifPresent(predicates::add);
    siteCriteria(builder, site, criteria, predicates);

    return query
        .select(builder.construct(ReplacementStructureLine.class,
            structure.get(STRUCTURE_ID),
            structure.get(STRUCTURE_NAME),
            structure.get(TYPE_CLASS),
            structure.get(DESIGN_LOAD_RATING),
            structure.get(CURRENT_LOAD_RATING),
            structure.get(END_OF_DESIGN_LIFE),
            structure.get(REPLACEMENT_COST),
            structure.get(LOAD_RESTRICTION_YEAR),
            structure.get(REPLACEMENT_YEAR),
            structure.get(CLOSURE_YEAR),
            site.get(SITE_ID),
            site.get(FOREST_FILE_ID),
            site.get(ROAD_SECTION_ID),
            roadSection.get(ROAD_SECTION_NAME),
            site.get(KILOMETRES),
            site.get(CROSSING_NAME),
            site.get(CLIENT_NUMBER),
            site.get(CAPITAL_ROAD),
            site.get(SITE_STATUS),
            site.get(ORG_UNIT_NO),
            district.get(ROLLUP_REGION_NO),
            site.get(MANAGEMENT_ORG_UNIT_NO)))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(builder.asc(structure.get(STRUCTURE_ID)));
  }

  private static void siteCriteria(
      CriteriaBuilder builder,
      From<?, ?> site,
      ReplacementStructuresCriteria criteria,
      List<Predicate> predicates) {
    equalsNumber(builder, site.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(BUSINESS_AREA_ORG_UNIT_NO), criteria.businessAreaOrgUnit())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_STATUS), criteria.siteStatusCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_TYPE), criteria.siteTypeCode()).ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.capitalRoad())) {
      predicates.add(builder.equal(site.get(CAPITAL_ROAD), YES));
    }
    // Legacy matches the maintainer with LIKE, as it does every text box; a number picked from the
    // lookup is a whole value, so here it is exact, as on the other reports.
    equalsText(builder, site.get(CLIENT_NUMBER), criteria.clientNumber())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(CLIENT_LOCATION), criteria.clientLocationCode())
        .ifPresent(predicates::add);
  }
}
