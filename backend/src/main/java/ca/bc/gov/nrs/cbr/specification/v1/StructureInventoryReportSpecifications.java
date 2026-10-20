package ca.bc.gov.nrs.cbr.specification.v1;

import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.contains;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.decimalRange;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsNumber;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsText;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.integerRange;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.longRange;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySort;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.util.StringUtils;

/**
 * Which structures the Structure Inventory reports list, and in what order, as JPA criteria queries
 * selecting only what each prints: a Summary line ({@link StructureInventoryLine}) or a Detailed
 * block ({@link StructureInventoryDetailLine}). The criteria the two share are written once.
 *
 * <p>Replaces the {@code WHERE} clause legacy's {@code StructureInventorySummaryReportForm.createSearch}
 * and {@code OracleReportQueryDAO.generateReportQuery} assemble as a string for
 * {@code CBR_REPORTING.STRCTRE_INVNTRY_SUMMRY_RPT}. The meanings are legacy's — always
 * {@code ACTIVE_IND = 'Y'}; text an unanchored, case-insensitive contains; codes exact — with one
 * change shared with the search screens: a range with only one end set includes that end. Legacy's
 * {@code >} and {@code <} left it out, so "from 1990" missed what was built in 1990.
 *
 * <p>The site and its road are left joins, so a structure with no site is still listed, as it is in
 * legacy's outer-joined select. The bridge and culvert criteria are {@code EXISTS} subqueries, as on
 * Structure Search.
 */
public final class StructureInventoryReportSpecifications {

  private static final String STRUCTURE_ID = "crossingStructureId";
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String TYPE_CLASS_CODE = "structureTypeClassCode";
  private static final String ACTIVE_IND = "activeInd";
  private static final String DESIGN_LOAD_RATING = "designLoadRating";
  private static final String CURRENT_LOAD_RATING = "currentLoadRating";
  private static final String LOAD_RATING_UNKNOWN = "loadRatingUnknownIndicator";
  private static final String YEAR_BUILT = "yearBuilt";
  private static final String LOAD_RESTRICTION_YEAR = "fullLogHaulReplacementYear";
  private static final String REPLACEMENT_YEAR = "lightVehicleReplacementYear";
  private static final String PORTABLE_IND = "portableStructureInd";
  private static final String DESIGN_VEHICLE = "designVehicleLoadCode";
  private static final String BUILT_BY = "builtByCode";
  private static final String NEXT_PLANNED_INSPECTION = "nextPlannedInspectionDate";
  private static final String REPLACEMENT_COST = "estimatedReplacementCost";
  private static final String CLOSURE_YEAR = "estimatedClosureYear";
  private static final String STRUCTURE_SOURCE = "structureSourceCode";
  private static final String LONGITUDE = "longitude";
  private static final String LATITUDE = "latitude";
  private static final String STRUCTURE_INSPECTION_STATUS = "structureInspectionStatusCode";
  private static final String ROLLUP_REGION_NO = "rollupRegionNo";

  private static final String TYPE_CLASS = "typeClass";
  private static final String DESCRIPTION = "description";

  private static final String SITE = "site";
  private static final String SITE_STATUS_CODE = "status";
  private static final String ORG_UNIT = "orgUnit";
  private static final String ORG_UNIT_NAME = "orgUnitName";
  private static final String SITE_ID = "crossingSiteId";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ROAD_SECTION_NAME = "roadSectName";
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
  private static final String SITE_STATUS = "crossingSiteStatusCode";
  private static final String SITE_TYPE = "crossingSiteTypeCode";
  private static final String SPECIAL_ACCESS_CODE = "specialAccessRqmtCode";
  private static final String CAPITAL_ROAD = "capitalRoadInd";

  private static final String OWNING_STRUCTURE = "crossingStructureId";
  private static final String SUPERSTRUCTURE_TYPE = "superstructureTypeCode";
  private static final String DECK_TYPE = "deckTypeCode";
  private static final String PORTABLE_STATUS = "portableSuperstructureStatusCode";
  private static final String CULVERT_TYPE = "engineeredCulvertTypeCode";
  private static final String CULVERT_MATERIAL = "engineeredCulvertMaterialCode";
  private static final String OPENING_WIDTH = "openingWidth";

  private static final String YES = "Y";
  /** The site statuses and type/classes legacy's reports count as downrated: live crossings. */
  private static final List<String> DOWNRATED_SITE_STATUSES = List.of("ACT", "BAR");
  private static final List<String> DOWNRATED_TYPE_CLASSES = List.of("PB", "TB", "WLC");

  private StructureInventoryReportSpecifications() {}

  /**
   * The lines the report lists for these criteria, in the order chosen — one statement, selecting
   * the fourteen values a line prints rather than loading the structure and its site whole.
   *
   * <p>Joins, never fetches: nothing is loaded as an entity. That also keeps the site's road — a
   * {@code @NotFound} association, which Hibernate would otherwise load eagerly — to a plain left
   * join on the view.
   */
  public static CriteriaQuery<StructureInventoryLine> summary(
      CriteriaBuilder builder, StructureInventorySummaryCriteria criteria) {
    CriteriaQuery<StructureInventoryLine> query = builder.createQuery(StructureInventoryLine.class);
    Root<CrossingStructureEntity> root = query.from(CrossingStructureEntity.class);
    From<?, ?> site = root.join(SITE, JoinType.LEFT);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);

    List<Predicate> predicates = sharedCriteria(builder, query, root, site, criteria);
    constructionCriteria(builder, query, root, criteria, predicates);

    return query
        .select(builder.construct(StructureInventoryLine.class,
            root.get(STRUCTURE_ID),
            root.get(STRUCTURE_NAME),
            root.get(TYPE_CLASS_CODE),
            root.get(PORTABLE_IND),
            root.get(CURRENT_LOAD_RATING),
            root.get(YEAR_BUILT),
            root.get(LOAD_RESTRICTION_YEAR),
            site.get(SITE_ID),
            site.get(FOREST_FILE_ID),
            site.get(ROAD_SECTION_ID),
            site.get(CROSSING_NAME),
            site.get(KILOMETRES),
            site.get(CAPITAL_ROAD),
            roadSection.get(ROAD_SECTION_NAME)))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(order(criteria.sortOrDefault(), root, site, roadSection, builder));
  }

  /**
   * The structures the Structure Inventory – Detailed report covers for these criteria, in the
   * order chosen — one statement, selecting what its PDF block and its CSV row show of the structure
   * and its site, so the two formats list the same structures. The bridge, the culvert and the
   * routine inspections are read beside it.
   *
   * <p>The type/class is an inner join, as in legacy's {@code STRCTRE_INVNTRY_DTL_RPT}: a
   * structure with no type/class is left out of this report, though the Summary lists it.
   */
  public static CriteriaQuery<StructureInventoryDetailLine> detailed(
      CriteriaBuilder builder, StructureInventoryDetailedCriteria criteria) {
    CriteriaQuery<StructureInventoryDetailLine> query =
        builder.createQuery(StructureInventoryDetailLine.class);
    Root<CrossingStructureEntity> root = query.from(CrossingStructureEntity.class);
    From<?, ?> typeClass = root.join(TYPE_CLASS, JoinType.INNER);
    From<?, ?> site = root.join(SITE, JoinType.LEFT);
    From<?, ?> roadSection = site.join(ROAD_SECTION, JoinType.LEFT);
    From<?, ?> status = site.join(SITE_STATUS_CODE, JoinType.LEFT);
    From<?, ?> district = site.join(ORG_UNIT, JoinType.LEFT);

    List<Predicate> predicates = sharedCriteria(builder, query, root, site, criteria);

    return query
        .select(builder.construct(StructureInventoryDetailLine.class,
            root.get(STRUCTURE_ID),
            root.get(STRUCTURE_NAME),
            typeClass.get(DESCRIPTION),
            root.get(PORTABLE_IND),
            root.get(DESIGN_LOAD_RATING),
            root.get(CURRENT_LOAD_RATING),
            root.get(YEAR_BUILT),
            root.get(LOAD_RATING_UNKNOWN),
            site.get(SITE_ID),
            status.get(DESCRIPTION),
            site.get(KILOMETRES),
            site.get(USER_KM),
            site.get(FOREST_FILE_ID),
            site.get(ROAD_SECTION_ID),
            district.get(ORG_UNIT_NAME),
            roadSection.get(ROAD_SECTION_NAME),
            site.get(CROSSING_NAME),
            site.get(CAPITAL_ROAD),
            root.get(DESIGN_VEHICLE),
            root.get(BUILT_BY),
            root.get(NEXT_PLANNED_INSPECTION),
            root.get(REPLACEMENT_YEAR),
            root.get(REPLACEMENT_COST),
            root.get(CLOSURE_YEAR),
            root.get(LOAD_RESTRICTION_YEAR),
            root.get(STRUCTURE_SOURCE),
            site.get(CLIENT_NUMBER),
            site.get(SITE_TYPE),
            site.get(LONGITUDE),
            site.get(LATITUDE),
            site.get(MANAGEMENT_ORG_UNIT_NO),
            site.get(BUSINESS_AREA_ORG_UNIT_NO),
            district.get(ROLLUP_REGION_NO),
            site.get(STRUCTURE_INSPECTION_STATUS)))
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(order(criteria.sortOrDefault(), root, site, roadSection, builder));
  }

  /** The criteria every Structure Inventory report has — always {@code ACTIVE_IND = 'Y'}. */
  private static List<Predicate> sharedCriteria(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<CrossingStructureEntity> root,
      From<?, ?> site,
      StructureInventoryCriteria criteria) {
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.equal(root.get(ACTIVE_IND), YES));
    structureCriteria(builder, root, site, criteria, predicates);
    siteCriteria(builder, site, criteria, predicates);
    bridgeHas(builder, query, root, PORTABLE_STATUS, criteria.portableSuperstructureStatusCode())
        .ifPresent(predicates::add);
    return predicates;
  }

  private static void structureCriteria(
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> root,
      From<?, ?> site,
      StructureInventoryCriteria criteria,
      List<Predicate> predicates) {
    integerRange(builder, root.get(YEAR_BUILT),
        criteria.yearBuiltStart(), criteria.yearBuiltEnd()).ifPresent(predicates::add);
    integerRange(builder, root.get(LOAD_RESTRICTION_YEAR),
        criteria.loadRestrictionYearStart(), criteria.loadRestrictionYearEnd())
        .ifPresent(predicates::add);
    integerRange(builder, root.get(REPLACEMENT_YEAR),
        criteria.replacementYearStart(), criteria.replacementYearEnd()).ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.downrated())) {
      predicates.add(downrated(builder, root, site));
    }
  }

  private static void siteCriteria(
      CriteriaBuilder builder,
      From<?, ?> site,
      StructureInventoryCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, site.get(FOREST_FILE_ID), criteria.forestFileId()).ifPresent(predicates::add);
    contains(builder, site.get(ROAD_SECTION_ID), criteria.roadSectionId())
        .ifPresent(predicates::add);
    // Legacy matches the maintainer with LIKE, as it does every text box; a number picked from the
    // lookup is a whole value, so here it is exact, as on the search screens.
    equalsText(builder, site.get(CLIENT_NUMBER), criteria.clientNumber())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(CLIENT_LOCATION), criteria.clientLocationCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_STATUS), criteria.siteStatusCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_TYPE), criteria.siteTypeCode()).ifPresent(predicates::add);
    equalsText(builder, site.get(SPECIAL_ACCESS_CODE), criteria.specialAccessCode())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(BUSINESS_AREA_ORG_UNIT_NO), criteria.businessAreaOrgUnit())
        .ifPresent(predicates::add);
    decimalRange(builder, site.get(KILOMETRES), criteria.kiloStart(), criteria.kiloEnd())
        .ifPresent(predicates::add);
    if (Boolean.TRUE.equals(criteria.capitalRoad())) {
      predicates.add(builder.equal(site.get(CAPITAL_ROAD), YES));
    }
  }

  /**
   * The Summary's construction filters — its type/class, and the columns of the bridge row or the
   * culvert row. The Detailed report has none of them.
   */
  private static void constructionCriteria(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<CrossingStructureEntity> root,
      StructureInventorySummaryCriteria criteria,
      List<Predicate> predicates) {
    equalsText(builder, root.get(TYPE_CLASS_CODE), criteria.structureTypeClassCode())
        .ifPresent(predicates::add);
    bridgeHas(builder, query, root, SUPERSTRUCTURE_TYPE, criteria.superstructureTypeCode())
        .ifPresent(predicates::add);
    bridgeHas(builder, query, root, DECK_TYPE, criteria.deckTypeCode())
        .ifPresent(predicates::add);
    culvertHas(builder, query, root, CULVERT_TYPE, criteria.culvertTypeCode())
        .ifPresent(predicates::add);
    culvertHas(builder, query, root, CULVERT_MATERIAL, criteria.culvertMaterialCode())
        .ifPresent(predicates::add);
    if (StringUtils.hasText(criteria.openingWidthMin())
        || StringUtils.hasText(criteria.openingWidthMax())) {
      Subquery<Long> culverts = culvert(query, builder, root, (culvert, b) ->
          longRange(b, culvert.get(OPENING_WIDTH),
              criteria.openingWidthMin(), criteria.openingWidthMax())
              .orElseGet(b::conjunction));
      predicates.add(builder.exists(culverts));
    }
  }

  /**
   * "Downrated Structure?" as legacy's reports define it ({@code OracleReportQueryDAO},
   * {@code SEARCH_DOWNLOAD_RATED}) — wider than Structure Search's: rated below its design, or at
   * 0, or not at all, or marked "Load Rating Review Required"; and only a live crossing — an active
   * or barricaded site, and a bridge or a wood-log culvert.
   */
  private static Predicate downrated(
      CriteriaBuilder builder, Root<CrossingStructureEntity> root, From<?, ?> site) {
    Expression<BigDecimal> current = root.get(CURRENT_LOAD_RATING);
    return builder.and(
        builder.or(
            builder.lessThan(current, root.<BigDecimal>get(DESIGN_LOAD_RATING)),
            builder.equal(current, BigDecimal.ZERO),
            builder.isNull(current),
            builder.equal(root.get(LOAD_RATING_UNKNOWN), YES)),
        site.get(SITE_STATUS).in(DOWNRATED_SITE_STATUSES),
        root.get(TYPE_CLASS_CODE).in(DOWNRATED_TYPE_CLASSES));
  }

  private static Optional<Predicate> bridgeHas(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<CrossingStructureEntity> root,
      String column,
      String code) {
    if (!StringUtils.hasText(code)) {
      return Optional.empty();
    }
    return Optional.of(builder.exists(
        bridge(query, builder, root, (bridge, b) -> b.equal(bridge.get(column), code.trim()))));
  }

  private static Optional<Predicate> culvertHas(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<CrossingStructureEntity> root,
      String column,
      String code) {
    if (!StringUtils.hasText(code)) {
      return Optional.empty();
    }
    return Optional.of(builder.exists(
        culvert(query, builder, root, (culvert, b) -> b.equal(culvert.get(column), code.trim()))));
  }

  /** This structure's bridge row, if it has one that meets {@code condition}. */
  private static Subquery<Long> bridge(
      CriteriaQuery<?> query,
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> root,
      BiFunction<Root<ForestServiceBridgeEntity>, CriteriaBuilder, Predicate> condition) {
    Subquery<Long> rows = query.subquery(Long.class);
    Root<ForestServiceBridgeEntity> bridge = rows.from(ForestServiceBridgeEntity.class);
    return rows.select(bridge.get(OWNING_STRUCTURE)).where(
        builder.equal(bridge.get(OWNING_STRUCTURE), root.get(STRUCTURE_ID)),
        condition.apply(bridge, builder));
  }

  /** This structure's culvert row, if it has one that meets {@code condition}. */
  private static Subquery<Long> culvert(
      CriteriaQuery<?> query,
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> root,
      BiFunction<Root<ForestServiceCulvertEntity>, CriteriaBuilder, Predicate> condition) {
    Subquery<Long> rows = query.subquery(Long.class);
    Root<ForestServiceCulvertEntity> culvert = rows.from(ForestServiceCulvertEntity.class);
    return rows.select(culvert.get(OWNING_STRUCTURE)).where(
        builder.equal(culvert.get(OWNING_STRUCTURE), root.get(STRUCTURE_ID)),
        condition.apply(culvert, builder));
  }

  /** Legacy's four orders, ascending, then the structure's id so the order is total. */
  private static List<Order> order(
      StructureInventorySort sort,
      Root<CrossingStructureEntity> root,
      From<?, ?> site,
      From<?, ?> roadSection,
      CriteriaBuilder builder) {
    List<Order> order = new ArrayList<>(switch (sort) {
      case PROJECT_BRANCH_USER_KM -> List.of(
          builder.asc(site.get(FOREST_FILE_ID)),
          builder.asc(site.get(ROAD_SECTION_ID)),
          builder.asc(site.get(USER_KM)));
      case ROAD_NAME_USER_KM -> List.of(
          builder.asc(roadSection.get(ROAD_SECTION_NAME)),
          builder.asc(site.get(USER_KM)));
      case SITE_ID -> List.of(builder.asc(site.get(SITE_ID)));
      case STRUCTURE_ID -> List.of(builder.asc(root.get(STRUCTURE_NAME)));
    });
    order.add(builder.asc(root.get(STRUCTURE_ID)));
    return order;
  }
}
