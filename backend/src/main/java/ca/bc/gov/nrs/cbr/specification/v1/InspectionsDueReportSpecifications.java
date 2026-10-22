package ca.bc.gov.nrs.cbr.specification.v1;

import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.contains;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.dayRange;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsNumber;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsText;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueSort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * Which structures the Inspections Due reports and the Blank Report list, and in what order, as
 * one JPA criteria query selecting what the reports print ({@link InspectionsDueLine}).
 *
 * <p>Replaces the {@code WHERE} clause legacy's {@code InspectionsDue…ReportForm.createSearch} and
 * {@code OracleReportQueryDAO.generateReportQuery} assemble as a string. Its rules are legacy's: an
 * active structure; text an unanchored, case-insensitive contains; codes exact; "None" for Special
 * Equipment and No Next Planned Inspection Date each a blank column. Inspection Status is the
 * site's. Next Planned Inspection Date includes both its ends, where legacy's one-sided range left
 * its own end out.
 */
public final class InspectionsDueReportSpecifications {

  private static final String STRUCTURE_ID = "crossingStructureId";
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String ACTIVE_IND = "activeInd";
  private static final String CLOSE_PROXIMITY = "closeProximityInd";
  private static final String TYPE_CLASS = "structureTypeClassCode";
  private static final String NEXT_PLANNED = "nextPlannedInspectionDate";
  private static final String SPECIAL_EQUIPMENT = "specialEquipmentRqmtCode";

  private static final String SITE = "site";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ORG_UNIT = "orgUnit";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String SITE_ID = "crossingSiteId";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String CROSSING_NAME = "crossingName";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String USER_KM = "userKm";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String ORG_UNIT_NAME = "orgUnitName";
  private static final String MANAGEMENT_ORG_UNIT_NO = "managementOrgUnitNo";
  private static final String BUSINESS_AREA_ORG_UNIT_NO = "businessAreaOrgUnitNo";
  private static final String CLIENT_NUMBER = "clientNumber";
  private static final String CLIENT_LOCATION = "clientLocnCode";
  private static final String SITE_TYPE = "crossingSiteTypeCode";
  private static final String SITE_STATUS = "crossingSiteStatusCode";
  private static final String INSPECTION_STATUS = "structureInspectionStatusCode";
  private static final String SPECIAL_ACCESS = "specialAccessRqmtCode";
  private static final String YEAR_BUILT = "yearBuilt";
  private static final String LATITUDE = "latitude";
  private static final String LONGITUDE = "longitude";

  private static final String YES = "Y";

  private InspectionsDueReportSpecifications() {}

  /**
   * The structures listed for these criteria: by forest district — its number, as legacy sorts it
   * — then the order chosen, the branch, kilometre and user kilometre, as legacy's; then, so the
   * order is stable, Structure # and the structure's id.
   */
  public static CriteriaQuery<InspectionsDueLine> lines(
      CriteriaBuilder builder, InspectionsDueCriteria criteria) {
    return query(builder, criteria, false);
  }

  /**
   * The structures the Detailed lists: the Summary's, less a structure with no type/class —
   * legacy's {@code INSPCTNS_DUE_DETAILED} inner-joined its code table — and ordered by management
   * area within the district, as legacy's.
   */
  public static CriteriaQuery<InspectionsDueLine> detailedLines(
      CriteriaBuilder builder, InspectionsDueCriteria criteria) {
    return query(builder, criteria, true);
  }

  /**
   * The structures the Blank Report prints a form for, by id, by Structure # — legacy's
   * {@code BLANK_INSPECTION}, which inner-joined the site and ordered by the structure's name. Its
   * id follows, so two structures of one name keep their pages apart.
   */
  public static CriteriaQuery<Long> blankIds(
      CriteriaBuilder builder, InspectionsDueCriteria criteria) {
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<CrossingStructureEntity> structure = query.from(CrossingStructureEntity.class);
    From<?, ?> site = structure.join(SITE, JoinType.INNER);
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(structure.get(ACTIVE_IND), YES));
    structureCriteria(builder, structure, criteria, predicates);
    siteCriteria(builder, site, criteria, predicates);
    return query
        .select(structure.get(STRUCTURE_ID))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(builder.asc(structure.get(STRUCTURE_NAME)), builder.asc(structure.get(STRUCTURE_ID)));
  }

  private static CriteriaQuery<InspectionsDueLine> query(
      CriteriaBuilder builder, InspectionsDueCriteria criteria, boolean detailed) {
    CriteriaQuery<InspectionsDueLine> query = builder.createQuery(InspectionsDueLine.class);
    Root<CrossingStructureEntity> structure = query.from(CrossingStructureEntity.class);
    From<?, ?> site = structure.join(SITE, JoinType.LEFT);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);
    From<?, ?> district = site.join(ORG_UNIT, JoinType.LEFT);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(structure.get(ACTIVE_IND), YES));
    if (detailed) {
      predicates.add(builder.isNotNull(structure.get(TYPE_CLASS)));
    }
    structureCriteria(builder, structure, criteria, predicates);
    siteCriteria(builder, site, criteria, predicates);

    return query
        .select(builder.construct(InspectionsDueLine.class,
            structure.get(STRUCTURE_ID),
            structure.get(STRUCTURE_NAME),
            structure.get(TYPE_CLASS),
            structure.get(NEXT_PLANNED),
            structure.get(SPECIAL_EQUIPMENT),
            site.get(SITE_ID),
            site.get(FOREST_FILE_ID),
            site.get(ROAD_SECTION_ID),
            roadSection.get(ROAD_SECTION_NAME),
            site.get(KILOMETRES),
            site.get(USER_KM),
            site.get(CROSSING_NAME),
            site.get(SPECIAL_ACCESS),
            site.get(ORG_UNIT_NO),
            district.get(ORG_UNIT_NAME),
            structure.get(YEAR_BUILT),
            site.get(LATITUDE),
            site.get(LONGITUDE)))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(order(criteria.sortOrDefault(), builder, structure, site, roadSection, detailed));
  }

  private static void structureCriteria(
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> structure,
      InspectionsDueCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, structure.get(STRUCTURE_NAME), criteria.structureName())
        .ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.noNextPlannedInspectionDate())) {
      predicates.add(builder.isNull(structure.get(NEXT_PLANNED)));
    } else {
      dayRange(builder, structure.get(NEXT_PLANNED), criteria.nextPlannedInspectionDateStart(),
          criteria.nextPlannedInspectionDateEnd()).ifPresent(predicates::add);
    }
    equalsText(builder, structure.get(TYPE_CLASS), criteria.structureTypeClassCode())
        .ifPresent(predicates::add);
    equalsText(builder, structure.get(CLOSE_PROXIMITY), criteria.closeProximity())
        .ifPresent(predicates::add);
    if (InspectionsDueCriteria.NO_SPECIAL_EQUIPMENT.equals(criteria.specialEquipmentCode())) {
      predicates.add(builder.isNull(structure.get(SPECIAL_EQUIPMENT)));
    } else {
      equalsText(builder, structure.get(SPECIAL_EQUIPMENT), criteria.specialEquipmentCode())
          .ifPresent(predicates::add);
    }
  }

  private static void siteCriteria(
      CriteriaBuilder builder,
      From<?, ?> site,
      InspectionsDueCriteria criteria,
      List<Predicate> predicates) {
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
    equalsText(builder, site.get(SITE_STATUS), criteria.siteStatusCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(INSPECTION_STATUS), criteria.structureInspectionStatusCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SPECIAL_ACCESS), criteria.specialAccessCode())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(BUSINESS_AREA_ORG_UNIT_NO), criteria.businessAreaOrgUnit())
        .ifPresent(predicates::add);
  }

  private static List<Order> order(
      InspectionsDueSort sort,
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> structure,
      From<?, ?> site,
      From<?, ?> roadSection,
      boolean detailed) {
    List<Order> order = new ArrayList<>();
    order.add(builder.asc(site.get(ORG_UNIT_NO)));
    if (detailed) {
      order.add(builder.asc(site.get(MANAGEMENT_ORG_UNIT_NO)));
    }
    order.add(switch (sort) {
      case FOREST_SERVICE_ROAD -> builder.asc(roadSection.get(ROAD_SECTION_NAME));
      case FOREST_FILE_ID -> builder.asc(site.get(FOREST_FILE_ID));
    });
    order.addAll(List.of(
        builder.asc(site.get(ROAD_SECTION_ID)),
        builder.asc(site.get(KILOMETRES)),
        builder.asc(site.get(USER_KM)),
        builder.asc(structure.get(STRUCTURE_NAME)),
        builder.asc(structure.get(STRUCTURE_ID))));
    return order;
  }
}
