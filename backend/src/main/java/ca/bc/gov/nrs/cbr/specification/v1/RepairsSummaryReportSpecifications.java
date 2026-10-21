package ca.bc.gov.nrs.cbr.specification.v1;

import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.contains;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsNumber;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsText;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeXrefEntity;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummarySort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Which repairs the Repairs Summary lists, and in what order, as one JPA criteria query selecting
 * only what the report prints ({@link RepairsSummaryLine}) — the same repairs for the PDF and the
 * CSV.
 *
 * <p>Replaces the {@code WHERE} clause legacy's {@code RepairSummaryReportForm.createSearch} and
 * {@code OracleReportQueryDAO.generateReportQuery} assemble as a string for
 * {@code CBR_REPORTING.REPAIR_SUMMARY}. Its rules are legacy's: an active structure; never a repair
 * carried forward to a later inspection; never a portable superstructure in service; text an
 * unanchored, case-insensitive contains; codes exact, a group of them any one.
 *
 * <p>Two of legacy's joins are left out, as they only repeated rows. Legacy joined the structure's
 * culvert records, which the report never prints, so a structure with two listed every repair
 * twice; and it joined the repair type's classes, of which a type can have several, to filter on
 * Structural Safety — here an {@code EXISTS}. The bridge, read only to leave out a portable
 * superstructure in service, is a {@code NOT EXISTS} for the same reason.
 */
public final class RepairsSummaryReportSpecifications {

  private static final String STRUCTURE_ID = "crossingStructureId";
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String TYPE_CLASS_CODE = "structureTypeClassCode";
  private static final String ACTIVE_IND = "activeInd";
  private static final String CURRENT_LOAD_RATING = "currentLoadRating";
  private static final String DESIGN_LOAD_RATING = "designLoadRating";
  private static final String LOAD_RATING_UNKNOWN = "loadRatingUnknownIndicator";
  private static final String NEXT_PLANNED_INSPECTION = "nextPlannedInspectionDate";

  private static final String SITE = "site";
  private static final String ORG_UNIT = "orgUnit";
  private static final String ORG_UNIT_NAME = "orgUnitName";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String SITE_ID = "crossingSiteId";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String CROSSING_NAME = "crossingName";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String USER_KM = "userKm";
  private static final String LONGITUDE = "longitude";
  private static final String LATITUDE = "latitude";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String MANAGEMENT_ORG_UNIT_NO = "managementOrgUnitNo";
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
  private static final String PENG_COMMENT = "pengComment";

  private static final String REPAIR_ID = "repairId";
  private static final String REPAIR_NUMBER = "repairNumber";
  private static final String REPAIR_STATUS = "repairStatusCode";
  private static final String REPAIR_PRIORITY = "repairPriorityCode";
  private static final String REPAIR_TYPE = "structureRepairTypeCode";
  private static final String REPAIR_QUANTITY = "repairQuantity";
  private static final String ESTIMATE = "estimate";
  private static final String DESCRIPTION = "description";
  private static final String CARRIED_FORWARD = "carriedForwardInd";

  private static final String XREF_KEY = "id";
  private static final String XREF_TYPE_CLASS = "structureTypeClassCode";
  private static final String XREF_REPAIR_TYPE = "structureRepairTypeCode";
  private static final String XREF_CLASS = "structureRepairClassCode";

  private static final String PORTABLE_STATUS = "portableSuperstructureStatusCode";

  private static final String YES = "Y";
  private static final String NO = "N";
  private static final String ROUTINE = "ROUT";
  /** A portable superstructure in service: legacy leaves its structure out of the report. */
  private static final String IN_SERVICE = "S";
  private static final List<String> OUTSTANDING = List.of("SUG", "REQ", "CF");
  private static final List<String> SUGGESTED_OR_REQUIRED = List.of("SUG", "REQ");
  /** The site statuses and type/classes legacy's reports count as downrated: live crossings. */
  private static final List<String> DOWNRATED_SITE_STATUSES = List.of("ACT", "BAR");
  private static final List<String> DOWNRATED_TYPE_CLASSES = List.of("PB", "TB", "WLC");

  private RepairsSummaryReportSpecifications() {}

  /**
   * The repairs the report lists for these criteria: the order chosen, then legacy's — each
   * structure's repairs together, newest inspection first, then by status (descending), priority
   * and repair number.
   *
   * <p>TODO: rank priority by {@code REPAIR_PRIORITY_CODE_ORDER.REPAIR_PRIORITY_CODE_ORDER}, as
   * legacy does, once the role holds SELECT on it — see the same TODO on
   * {@code StructureRepairRepository.findPageByStructure}. Until then priority sorts by its code.
   */
  public static CriteriaQuery<RepairsSummaryLine> lines(
      CriteriaBuilder builder, RepairsSummaryCriteria criteria) {
    CriteriaQuery<RepairsSummaryLine> query = builder.createQuery(RepairsSummaryLine.class);
    Root<StructureRepairEntity> repair = query.from(StructureRepairEntity.class);
    Root<CrossingStructureEntity> structure = query.from(CrossingStructureEntity.class);
    From<?, ?> inspection = repair.join(INSPECTION, JoinType.LEFT);
    From<?, ?> site = structure.join(SITE, JoinType.LEFT);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);
    From<?, ?> district = site.join(ORG_UNIT, JoinType.LEFT);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(repair.get(STRUCTURE_ID), structure.get(STRUCTURE_ID)));
    predicates.add(builder.equal(structure.get(ACTIVE_IND), YES));
    predicates.add(builder.equal(repair.get(CARRIED_FORWARD), NO));
    predicates.add(builder.not(builder.exists(portableInService(query, builder, structure))));
    structureCriteria(builder, structure, site, criteria, predicates);
    siteCriteria(builder, site, roadSection, criteria, predicates);
    repairCriteria(builder, query, repair, structure, criteria, predicates);
    if (Boolean.TRUE.equals(criteria.mostRecentInspections())) {
      predicates.add(mostRecentRoutine(builder, query, inspection));
    }

    return query
        .select(builder.construct(RepairsSummaryLine.class,
            structure.get(STRUCTURE_ID),
            structure.get(STRUCTURE_NAME),
            structure.get(TYPE_CLASS_CODE),
            structure.get(CURRENT_LOAD_RATING),
            structure.get(NEXT_PLANNED_INSPECTION),
            site.get(SITE_ID),
            site.get(FOREST_FILE_ID),
            site.get(ROAD_SECTION_ID),
            site.get(CROSSING_NAME),
            district.get(ORG_UNIT_NAME),
            roadSection.get(ROAD_SECTION_NAME),
            site.get(KILOMETRES),
            site.get(USER_KM),
            site.get(LONGITUDE),
            site.get(LATITUDE),
            site.get(CLIENT_NUMBER),
            site.get(CLIENT_LOCATION),
            inspection.get(INSPECTION_ID),
            inspection.get(INSPECTION_DATE),
            inspection.get(INSPECTION_COMMENT),
            inspection.get(PENG_COMMENT),
            repair.get(REPAIR_NUMBER),
            repair.get(REPAIR_STATUS),
            repair.get(REPAIR_PRIORITY),
            repair.get(REPAIR_TYPE),
            repair.get(REPAIR_QUANTITY),
            repair.get(ESTIMATE),
            repair.get(DESCRIPTION)))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(order(criteria.sortOrDefault(), builder, repair, structure, site, roadSection,
            inspection));
  }

  private static void structureCriteria(
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> structure,
      From<?, ?> site,
      RepairsSummaryCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, structure.get(STRUCTURE_NAME), criteria.structureName())
        .ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.downrated())) {
      predicates.add(downrated(builder, structure, site));
    }
  }

  private static void siteCriteria(
      CriteriaBuilder builder,
      From<?, ?> site,
      From<?, ?> roadSection,
      RepairsSummaryCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, site.get(SITE_ID), criteria.siteId()).ifPresent(predicates::add);
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
    equalsNumber(builder, site.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(BUSINESS_AREA_ORG_UNIT_NO), criteria.businessAreaOrgUnit())
        .ifPresent(predicates::add);
  }

  /** Repair Status, Repair Priority and Structural Safety — a code or one of legacy's groups. */
  private static void repairCriteria(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<StructureRepairEntity> repair,
      Root<CrossingStructureEntity> structure,
      RepairsSummaryCriteria criteria,
      List<Predicate> predicates) {
    List<String> statuses = statuses(criteria.repairStatusCode());
    if (!statuses.isEmpty()) {
      predicates.add(repair.get(REPAIR_STATUS).in(statuses));
    }
    List<String> priorities = group(criteria.repairPriorityCode());
    if (!priorities.isEmpty()) {
      predicates.add(repair.get(REPAIR_PRIORITY).in(priorities));
    }
    List<String> classes = group(criteria.repairClassCode());
    if (!classes.isEmpty()) {
      Subquery<String> xref = query.subquery(String.class);
      Root<StructureRepairTypeXrefEntity> row = xref.from(StructureRepairTypeXrefEntity.class);
      Path<?> key = row.get(XREF_KEY);
      predicates.add(builder.exists(xref.select(key.get(XREF_REPAIR_TYPE)).where(
          builder.equal(key.get(XREF_REPAIR_TYPE), repair.get(REPAIR_TYPE)),
          builder.equal(key.get(XREF_TYPE_CLASS), structure.get(TYPE_CLASS_CODE)),
          key.get(XREF_CLASS).in(classes))));
    }
  }

  /** The statuses a Repair Status means: legacy's two groups, or the one code. */
  static List<String> statuses(String code) {
    if (!StringUtils.hasText(code)) {
      return List.of();
    }
    return switch (code.trim()) {
      case RepairsSummaryCriteria.OUTSTANDING -> OUTSTANDING;
      case RepairsSummaryCriteria.SUGGESTED_OR_REQUIRED -> SUGGESTED_OR_REQUIRED;
      default -> List.of(code.trim());
    };
  }

  /** The codes of a group — {@code U&H&M} — or the one code. */
  static List<String> group(String codes) {
    if (!StringUtils.hasText(codes)) {
      return List.of();
    }
    return Arrays.stream(codes.split(RepairsSummaryCriteria.GROUP_SEPARATOR))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(code -> code.toUpperCase(Locale.ROOT))
        .toList();
  }

  /**
   * "Most Recent Inspections Only": the repair's inspection is a routine one, on the latest date of
   * any routine inspection of its structure — legacy's {@code SEARCH_MOST_RECENT_INSPECTIONS}. A
   * repair on no inspection is left out.
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
   * "Downrated Structure?" as legacy's reports define it ({@code SEARCH_DOWNLOAD_RATED}): rated
   * below its design, or at 0, or not at all, or marked "Load Rating Review Required"; and only a
   * live crossing — an active or barricaded site, and a bridge or a wood-log culvert.
   */
  private static Predicate downrated(
      CriteriaBuilder builder, Root<CrossingStructureEntity> structure, From<?, ?> site) {
    Expression<BigDecimal> current = structure.get(CURRENT_LOAD_RATING);
    return builder.and(
        builder.or(
            builder.lessThan(current, structure.<BigDecimal>get(DESIGN_LOAD_RATING)),
            builder.equal(current, BigDecimal.ZERO),
            builder.isNull(current),
            builder.equal(structure.get(LOAD_RATING_UNKNOWN), YES)),
        site.get(SITE_STATUS).in(DOWNRATED_SITE_STATUSES),
        structure.get(TYPE_CLASS_CODE).in(DOWNRATED_TYPE_CLASSES));
  }

  /**
   * The order chosen, then legacy's own: structure (descending, its id keeping each structure's
   * repairs together), inspection date (newest first, its id keeping each inspection's together),
   * status (descending), priority, repair number.
   */
  private static List<Order> order(
      RepairsSummarySort sort,
      CriteriaBuilder builder,
      Root<StructureRepairEntity> repair,
      Root<CrossingStructureEntity> structure,
      From<?, ?> site,
      From<?, ?> roadSection,
      From<?, ?> inspection) {
    List<Order> order = new ArrayList<>(switch (sort) {
      case PROJECT_BRANCH_KM -> List.of(
          builder.asc(site.get(FOREST_FILE_ID)),
          builder.asc(site.get(ROAD_SECTION_ID)),
          builder.asc(site.get(KILOMETRES)));
      case ROAD_NAME_USER_KM -> List.of(
          builder.asc(roadSection.get(ROAD_SECTION_NAME)),
          builder.asc(site.get(USER_KM)));
      case SITE_ID -> List.of(builder.asc(site.get(SITE_ID)));
    });
    order.addAll(List.of(
        builder.desc(structure.get(STRUCTURE_NAME)),
        builder.asc(structure.get(STRUCTURE_ID)),
        builder.desc(inspection.get(INSPECTION_DATE)),
        builder.asc(inspection.get(INSPECTION_ID)),
        builder.desc(repair.get(REPAIR_STATUS)),
        builder.asc(repair.get(REPAIR_PRIORITY)),
        builder.asc(repair.get(REPAIR_NUMBER)),
        builder.asc(repair.get(REPAIR_ID))));
    return order;
  }
}
