package ca.bc.gov.nrs.cbr.specification.v1;

import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.contains;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsNumber;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsText;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummarySort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Which monitoring items the Monitors Summary lists, and in what order, as one JPA criteria query
 * selecting only what the report prints ({@link MonitorsSummaryLine}).
 *
 * <p>Replaces the {@code WHERE} clause legacy's {@code MonitorSummaryReportForm.createSearch} and
 * {@code OracleReportQueryDAO.generateReportQuery} assemble as a string for
 * {@code CBR_REPORTING.MONITOR_SUMMARY}. Its rules are legacy's: an active structure; an item whose
 * status is in the code table, as legacy's inner join had it; text an unanchored, case-insensitive
 * contains; codes exact. One is new: Most Recent Inspections Only, which legacy printed on the
 * criteria line but never applied, keeps the items of each structure's latest routine inspection.
 */
public final class MonitorsSummaryReportSpecifications {

  private static final String STRUCTURE_ID = "crossingStructureId";
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String ACTIVE_IND = "activeInd";

  private static final String SITE = "site";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String SITE_ID = "crossingSiteId";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String CROSSING_NAME = "crossingName";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String BUSINESS_AREA_ORG_UNIT_NO = "businessAreaOrgUnitNo";
  private static final String CLIENT_NUMBER = "clientNumber";
  private static final String CLIENT_LOCATION = "clientLocnCode";
  private static final String SITE_STATUS = "crossingSiteStatusCode";
  private static final String SITE_TYPE = "crossingSiteTypeCode";

  private static final String INSPECTION = "inspection";
  private static final String INSPECTION_ID = "inspectionId";
  private static final String INSPECTION_DATE = "inspectionDate";
  private static final String INSPECTION_TYPE = "strctreInspectionTypeCode";
  private static final String INSPECTION_COMMENT = "inspectionComment";

  private static final String MONITOR_ID = "monitorId";
  private static final String MONITOR_NUMBER = "monitorNumber";
  private static final String MONITORING_STATUS = "monitoringStatusCode";
  private static final String DESCRIPTION = "description";

  private static final String YES = "Y";
  private static final String ROUTINE = "ROUT";

  private MonitorsSummaryReportSpecifications() {}

  /**
   * The items the report lists for these criteria: the order chosen, then legacy's — by Structure
   * # (its id keeping each structure's items together), newest inspection first (its id keeping
   * each inspection's together), then monitor number.
   */
  public static CriteriaQuery<MonitorsSummaryLine> lines(
      CriteriaBuilder builder, MonitorsSummaryCriteria criteria) {
    CriteriaQuery<MonitorsSummaryLine> query = builder.createQuery(MonitorsSummaryLine.class);
    Root<StructureMonitorItemEntity> item = query.from(StructureMonitorItemEntity.class);
    Root<CrossingStructureEntity> structure = query.from(CrossingStructureEntity.class);
    From<?, ?> inspection = item.join(INSPECTION, JoinType.LEFT);
    From<?, ?> site = structure.join(SITE, JoinType.LEFT);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(item.get(STRUCTURE_ID), structure.get(STRUCTURE_ID)));
    predicates.add(builder.equal(structure.get(ACTIVE_IND), YES));
    predicates.add(builder.exists(knownStatus(query, builder, item)));
    contains(builder, structure.get(STRUCTURE_NAME), criteria.structureName())
        .ifPresent(predicates::add);
    siteCriteria(builder, site, roadSection, criteria, predicates);
    equalsText(builder, item.get(MONITORING_STATUS), criteria.monitoringStatusCode())
        .ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.mostRecentInspections())) {
      predicates.add(mostRecentRoutine(builder, query, inspection));
    }

    return query
        .select(builder.construct(MonitorsSummaryLine.class,
            structure.get(STRUCTURE_ID),
            structure.get(STRUCTURE_NAME),
            site.get(SITE_ID),
            site.get(SITE_TYPE),
            site.get(FOREST_FILE_ID),
            site.get(ROAD_SECTION_ID),
            site.get(CROSSING_NAME),
            roadSection.get(ROAD_SECTION_NAME),
            site.get(KILOMETRES),
            site.get(CLIENT_NUMBER),
            inspection.get(INSPECTION_ID),
            inspection.get(INSPECTION_DATE),
            inspection.get(INSPECTION_COMMENT),
            item.get(MONITOR_NUMBER),
            item.get(MONITORING_STATUS),
            item.get(DESCRIPTION)))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(order(criteria.sortOrDefault(), builder, item, structure, site, roadSection,
            inspection));
  }

  private static void siteCriteria(
      CriteriaBuilder builder,
      From<?, ?> site,
      From<?, ?> roadSection,
      MonitorsSummaryCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, site.get(FOREST_FILE_ID), criteria.forestFileId()).ifPresent(predicates::add);
    contains(builder, site.get(ROAD_SECTION_ID), criteria.roadSectionId())
        .ifPresent(predicates::add);
    contains(builder, roadSection.get(ROAD_SECTION_NAME), criteria.forestServiceRoad())
        .ifPresent(predicates::add);
    // Legacy matches the maintainer with LIKE, as it does every text box; a number picked from the
    // lookup is a whole value, so here it is exact, as on the other reports.
    equalsText(builder, site.get(CLIENT_NUMBER), criteria.clientNumber())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(CLIENT_LOCATION), criteria.clientLocationCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_STATUS), criteria.siteStatusCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_TYPE), criteria.siteTypeCode()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(BUSINESS_AREA_ORG_UNIT_NO), criteria.businessAreaOrgUnit())
        .ifPresent(predicates::add);
  }

  /** The item's status, if the code table holds it — legacy's inner join. */
  private static Subquery<String> knownStatus(
      CriteriaQuery<?> query, CriteriaBuilder builder, Root<StructureMonitorItemEntity> item) {
    Subquery<String> rows = query.subquery(String.class);
    Root<MonitoringStatusCodeEntity> status = rows.from(MonitoringStatusCodeEntity.class);
    return rows.select(status.get(MONITORING_STATUS))
        .where(builder.equal(status.get(MONITORING_STATUS), item.get(MONITORING_STATUS)));
  }

  /**
   * "Most Recent Inspections Only": the item's inspection is a routine one, on the latest date of
   * any routine inspection of its structure — the rule legacy's Repairs Summary applies. An item on
   * no inspection is left out.
   */
  private static Predicate mostRecentRoutine(
      CriteriaBuilder builder, CriteriaQuery<?> query, From<?, ?> inspection) {
    Subquery<LocalDate> latest = query.subquery(LocalDate.class);
    Root<StructureInspectionEntity> other = latest.from(StructureInspectionEntity.class);
    latest.select(builder.greatest(other.<LocalDate>get(INSPECTION_DATE))).where(
        builder.equal(other.get(STRUCTURE_ID), inspection.get(STRUCTURE_ID)),
        builder.equal(other.get(INSPECTION_TYPE), ROUTINE));
    return builder.and(
        builder.equal(inspection.get(INSPECTION_TYPE), ROUTINE),
        builder.equal(inspection.get(INSPECTION_DATE), latest));
  }

  private static List<Order> order(
      MonitorsSummarySort sort,
      CriteriaBuilder builder,
      Root<StructureMonitorItemEntity> item,
      Root<CrossingStructureEntity> structure,
      From<?, ?> site,
      From<?, ?> roadSection,
      From<?, ?> inspection) {
    List<Order> order = new ArrayList<>(switch (sort) {
      case PROJECT_BRANCH_KM -> List.of(
          builder.asc(site.get(FOREST_FILE_ID)),
          builder.asc(site.get(ROAD_SECTION_ID)),
          builder.asc(site.get(KILOMETRES)));
      case ROAD_NAME_STRUCTURE -> List.of(builder.asc(roadSection.get(ROAD_SECTION_NAME)));
    });
    order.addAll(List.of(
        builder.asc(structure.get(STRUCTURE_NAME)),
        builder.asc(structure.get(STRUCTURE_ID)),
        builder.desc(inspection.get(INSPECTION_DATE)),
        builder.asc(inspection.get(INSPECTION_ID)),
        builder.asc(item.get(MONITOR_NUMBER)),
        builder.asc(item.get(MONITOR_ID))));
    return order;
  }
}
