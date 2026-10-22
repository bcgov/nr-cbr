package ca.bc.gov.nrs.cbr.specification.v1;

import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.contains;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsNumber;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsText;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.monthRange;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
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
 * Which inspections the Inspections Conducted reports list, and in what order, as one JPA criteria
 * query selecting only what the Summary prints ({@link InspectionsConductedLine}).
 *
 * <p>Replaces the {@code WHERE} clause legacy's {@code InspectionsConductedReportForm.createSearch}
 * and {@code OracleReportQueryDAO.generateReportQuery} assemble as a string for
 * {@code CBR_REPORTING.INSPCTNS_CONDUCTED_RPT}. Its rules are legacy's: an active structure; never a
 * portable superstructure in service; an inspection with a report status, listed once under its
 * current one — the status a Report Status filter matches; text an unanchored, case-insensitive
 * contains; codes exact. The inspection date is a range of whole months, as on Inspection Search;
 * legacy's day-precise bounds left out a one-sided range's own end.
 */
public final class InspectionsConductedReportSpecifications {

  private static final String STRUCTURE_ID = "crossingStructureId";
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String ACTIVE_IND = "activeInd";
  private static final String CLOSE_PROXIMITY = "closeProximityInd";

  private static final String SITE = "site";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String SITE_ID = "crossingSiteId";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String CROSSING_NAME = "crossingName";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String USER_KM = "userKm";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String MANAGEMENT_ORG_UNIT_NO = "managementOrgUnitNo";
  private static final String BUSINESS_AREA_ORG_UNIT_NO = "businessAreaOrgUnitNo";
  private static final String CLIENT_NUMBER = "clientNumber";
  private static final String CLIENT_LOCATION = "clientLocnCode";
  private static final String SITE_TYPE = "crossingSiteTypeCode";

  private static final String INSPECTION_ID = "inspectionId";
  private static final String INSPECTION_DATE = "inspectionDate";
  private static final String INSPECTION_TYPE = "strctreInspectionTypeCode";
  private static final String INSPECTOR_NAME = "inspectorName";
  private static final String PROFESSIONAL_REQUIRED = "pengInspectionReqInd";
  private static final String CURRENT_STATUS = "currentStatus";
  private static final String STATUS_CODE = "inspectionReportStatusCode";

  private static final String PORTABLE_STATUS = "portableSuperstructureStatusCode";

  private static final String YES = "Y";
  private static final String ROUTINE = "ROUT";
  /** A portable superstructure in service: legacy leaves its structure out of the report. */
  private static final String IN_SERVICE = "S";

  private InspectionsConductedReportSpecifications() {}

  /**
   * The inspections the report lists for these criteria, in the order chosen — in the direction
   * legacy gives it — then, so the order is stable, by Structure #, newest inspection first, and
   * the inspection's id.
   */
  public static CriteriaQuery<InspectionsConductedLine> lines(
      CriteriaBuilder builder, InspectionsConductedCriteria criteria, InspectionsConductedSort sort) {
    CriteriaQuery<InspectionsConductedLine> query =
        builder.createQuery(InspectionsConductedLine.class);
    Root<StructureInspectionEntity> inspection = query.from(StructureInspectionEntity.class);
    Root<CrossingStructureEntity> structure = query.from(CrossingStructureEntity.class);
    From<?, ?> status = inspection.join(CURRENT_STATUS, JoinType.INNER);
    From<?, ?> site = structure.join(SITE, JoinType.LEFT);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(inspection.get(STRUCTURE_ID), structure.get(STRUCTURE_ID)));
    predicates.add(builder.equal(structure.get(ACTIVE_IND), YES));
    predicates.add(builder.not(builder.exists(portableInService(query, builder, structure))));
    inspectionCriteria(builder, query, inspection, status, criteria, predicates);
    structureCriteria(builder, structure, site, criteria, predicates);

    return query
        .select(builder.construct(InspectionsConductedLine.class,
            structure.get(STRUCTURE_ID),
            structure.get(STRUCTURE_NAME),
            site.get(SITE_ID),
            roadSection.get(ROAD_SECTION_NAME),
            site.get(KILOMETRES),
            site.get(USER_KM),
            site.get(CROSSING_NAME),
            inspection.get(INSPECTION_ID),
            inspection.get(INSPECTION_DATE),
            inspection.get(INSPECTION_TYPE),
            status.get(STATUS_CODE)))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(order(sort, builder, inspection, structure, site, roadSection));
  }

  private static void inspectionCriteria(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<StructureInspectionEntity> inspection,
      From<?, ?> status,
      InspectionsConductedCriteria criteria,
      List<Predicate> predicates) {
    equalsText(builder, status.get(STATUS_CODE), criteria.inspectionReportStatusCode())
        .ifPresent(predicates::add);
    equalsText(builder, inspection.get(INSPECTION_TYPE), criteria.inspectionTypeCode())
        .ifPresent(predicates::add);
    monthRange(builder, inspection.get(INSPECTION_DATE),
        criteria.inspectionDateStart(), criteria.inspectionDateEnd()).ifPresent(predicates::add);
    contains(builder, inspection.get(INSPECTOR_NAME), criteria.inspectorName())
        .ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.professionalInspection())) {
      predicates.add(builder.equal(inspection.get(PROFESSIONAL_REQUIRED), YES));
    }
    if (Boolean.TRUE.equals(criteria.mostRecentInspections())) {
      predicates.add(mostRecentRoutine(builder, query, inspection));
    }
  }

  private static void structureCriteria(
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> structure,
      From<?, ?> site,
      InspectionsConductedCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, structure.get(STRUCTURE_NAME), criteria.structureName())
        .ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.closeProximity())) {
      predicates.add(builder.equal(structure.get(CLOSE_PROXIMITY), YES));
    }
    contains(builder, site.get(FOREST_FILE_ID), criteria.forestFileId()).ifPresent(predicates::add);
    contains(builder, site.get(ROAD_SECTION_ID), criteria.roadSectionId())
        .ifPresent(predicates::add);
    // Legacy matches the maintainer with LIKE, as it does every text box; a number picked from the
    // lookup is a whole value, so here it is exact, as on the other reports.
    equalsText(builder, site.get(CLIENT_NUMBER), criteria.clientNumber())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(CLIENT_LOCATION), criteria.clientLocationCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_TYPE), criteria.siteTypeCode()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(BUSINESS_AREA_ORG_UNIT_NO), criteria.businessAreaOrgUnit())
        .ifPresent(predicates::add);
  }

  /**
   * "Most Recent Inspections Only": a routine inspection on the latest date of any routine
   * inspection of its structure — legacy's {@code SEARCH_MOST_RECENT_INSPECTIONS}.
   */
  private static Predicate mostRecentRoutine(
      CriteriaBuilder builder, CriteriaQuery<?> query, Root<StructureInspectionEntity> inspection) {
    Subquery<LocalDate> latest = query.subquery(LocalDate.class);
    Root<StructureInspectionEntity> other = latest.from(StructureInspectionEntity.class);
    latest.select(builder.greatest(other.<LocalDate>get(INSPECTION_DATE))).where(
        builder.equal(other.get(STRUCTURE_ID), inspection.get(STRUCTURE_ID)),
        builder.equal(other.get(INSPECTION_TYPE), ROUTINE));
    return builder.and(
        builder.equal(inspection.get(INSPECTION_TYPE), ROUTINE),
        builder.equal(inspection.get(INSPECTION_DATE), latest));
  }

  /** The structure's bridge, if it is a portable superstructure in service. */
  private static Subquery<Long> portableInService(
      CriteriaQuery<?> query, CriteriaBuilder builder, Root<CrossingStructureEntity> structure) {
    Subquery<Long> rows = query.subquery(Long.class);
    Root<ForestServiceBridgeEntity> bridge = rows.from(ForestServiceBridgeEntity.class);
    return rows.select(bridge.get(STRUCTURE_ID)).where(
        builder.equal(bridge.get(STRUCTURE_ID), structure.get(STRUCTURE_ID)),
        builder.equal(bridge.get(PORTABLE_STATUS), IN_SERVICE));
  }

  /**
   * The order chosen, in legacy's direction for it, then Structure #, newest inspection first and
   * the inspection's id — legacy sorted on the one column, and ties came back in any order.
   */
  private static List<Order> order(
      InspectionsConductedSort sort,
      CriteriaBuilder builder,
      Root<StructureInspectionEntity> inspection,
      Root<CrossingStructureEntity> structure,
      From<?, ?> site,
      From<?, ?> roadSection) {
    List<Order> order = new ArrayList<>(sortColumns(sort, builder, inspection, site, roadSection));
    order.addAll(List.of(
        builder.asc(structure.get(STRUCTURE_NAME)),
        builder.desc(inspection.get(INSPECTION_DATE)),
        builder.asc(inspection.get(INSPECTION_ID))));
    return order;
  }

  /** The columns a Sort By orders on, each in legacy's direction. */
  private static List<Order> sortColumns(
      InspectionsConductedSort sort,
      CriteriaBuilder builder,
      Root<StructureInspectionEntity> inspection,
      From<?, ?> site,
      From<?, ?> roadSection) {
    return switch (sort) {
      case FOREST_SERVICE_ROAD -> List.of(builder.desc(roadSection.get(ROAD_SECTION_NAME)));
      case FOREST_FILE_ID -> List.of(builder.desc(site.get(FOREST_FILE_ID)));
      case USER_KM -> List.of(builder.asc(site.get(USER_KM)));
      case INSPECTION_DATE -> List.of(builder.desc(inspection.get(INSPECTION_DATE)));
      case PROJECT_BRANCH_USER_KM -> List.of(
          builder.asc(site.get(FOREST_FILE_ID)),
          builder.asc(site.get(ROAD_SECTION_ID)),
          builder.asc(site.get(USER_KM)));
      case ROAD_NAME_USER_KM -> List.of(
          builder.asc(roadSection.get(ROAD_SECTION_NAME)),
          builder.asc(site.get(USER_KM)));
    };
  }

  /**
   * The inspections the Detail prints for these criteria, by id, in its order: Structure # first,
   * the Sort By chosen within it, then newest inspection first — legacy's
   * {@code InspectionReportAction.run}, which put {@code S.CROSSING_STRUCTURE_NAME ASC} ahead of
   * whatever was chosen.
   *
   * <p>Its rules differ from the Summary's where legacy's {@code INSPCTN_RPT} differs from
   * {@code INSPCTNS_CONDUCTED_RPT}: a portable superstructure in service is listed; an inspection
   * without a report status is listed too, unless a status is asked for; the site, its district
   * and the inspection's type are required, as legacy's inner joins had them.
   */
  public static CriteriaQuery<Long> detailIds(
      CriteriaBuilder builder, InspectionsConductedCriteria criteria, InspectionsConductedSort sort) {
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<StructureInspectionEntity> inspection = query.from(StructureInspectionEntity.class);
    Root<CrossingStructureEntity> structure = query.from(CrossingStructureEntity.class);
    From<?, ?> status = inspection.join(CURRENT_STATUS, JoinType.LEFT);
    From<?, ?> site = structure.join(SITE, JoinType.INNER);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(inspection.get(STRUCTURE_ID), structure.get(STRUCTURE_ID)));
    predicates.add(builder.equal(structure.get(ACTIVE_IND), YES));
    predicates.add(builder.isNotNull(site.get(ORG_UNIT_NO)));
    predicates.add(builder.isNotNull(inspection.get(INSPECTION_TYPE)));
    inspectionCriteria(builder, query, inspection, status, criteria, predicates);
    structureCriteria(builder, structure, site, criteria, predicates);

    List<Order> order = new ArrayList<>();
    order.add(builder.asc(structure.get(STRUCTURE_NAME)));
    order.addAll(sortColumns(sort, builder, inspection, site, roadSection));
    order.add(builder.desc(inspection.get(INSPECTION_DATE)));
    order.add(builder.asc(inspection.get(INSPECTION_ID)));
    return query
        .select(inspection.get(INSPECTION_ID))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(order);
  }
}
