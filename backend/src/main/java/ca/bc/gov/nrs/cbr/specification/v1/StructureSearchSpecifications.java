package ca.bc.gov.nrs.cbr.specification.v1;

import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.contains;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.decimalRange;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsNumber;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.equalsText;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.integerRange;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.joinOrFetch;
import static ca.bc.gov.nrs.cbr.specification.v1.SearchPredicates.returnsEntities;

import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Structure Search, as a JPA {@link Specification}.
 *
 * <p>Replaces {@code CBR_GENERAL.FIND_STRUCTURES_BY_CRITERIA} and
 * {@code COUNT_STRUCTURES_BY_CRITERIA}, and the {@code WHERE} clause {@code StructureSearchForm}
 * and {@code OracleStructureDAO.search} assemble for them. Legacy's base select is
 * {@code CROSSING_STRUCTURE} left-joined to its bridge, culvert, site, client, road section and org
 * unit, and inner-joined to its type/class:
 *
 * <ul>
 *   <li><b>Site, road section, org unit</b> — left joins here too, through the associations, so a
 *       structure with no site is still a candidate for every criterion not about the site. Fetched
 *       when the query returns rows, since every results row reads them.</li>
 *   <li><b>Bridge and culvert</b> — {@code EXISTS} subqueries instead of joins. Each is an optional
 *       one-to-one held on the far side, which Hibernate cannot load lazily from the structure, so
 *       an association would cost a select per row on every search; and only the filters read
 *       them, never the results.</li>
 *   <li><b>Client</b> — a subquery for the name criterion, as on Site Search; the results read
 *       names separately, a page at a time.</li>
 * </ul>
 *
 * <p>Legacy also caps its unpaginated path at 200 rows; this is paged, so it does not.
 */
public final class StructureSearchSpecifications {

  /* On CROSSING_STRUCTURE. */
  private static final String STRUCTURE_ID = "crossingStructureId";
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String STRUCTURE_SITE_ID = "crossingSiteId";
  private static final String TYPE_CLASS_CODE = "structureTypeClassCode";
  private static final String ACTIVE_IND = "activeInd";
  private static final String PORTABLE_IND = "portableStructureInd";
  private static final String SPECIAL_EQUIPMENT_CODE = "specialEquipmentRqmtCode";
  private static final String DESIGN_LOAD_RATING = "designLoadRating";
  private static final String CURRENT_LOAD_RATING = "currentLoadRating";
  private static final String LOAD_RATING_UNKNOWN = "loadRatingUnknownIndicator";
  private static final String YEAR_BUILT = "yearBuilt";
  private static final String LOAD_RESTRICTION_YEAR = "fullLogHaulReplacementYear";
  private static final String REPLACEMENT_YEAR = "lightVehicleReplacementYear";
  private static final String CLOSURE_YEAR = "estimatedClosureYear";
  private static final String NEXT_PLANNED_INSPECTION = "nextPlannedInspectionDate";
  private static final String STRUCTURE_SOURCE = "structureSourceCode";

  /* On CROSSING_SITE. */
  private static final String SITE_ID = "crossingSiteId";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String USER_KM = "userKm";
  private static final String CROSSING_NAME = "crossingName";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String MANAGEMENT_ORG_UNIT_NO = "managementOrgUnitNo";
  private static final String CLIENT_NUMBER = "clientNumber";
  private static final String CLIENT_LOCATION = "clientLocnCode";
  private static final String SITE_STATUS = "crossingSiteStatusCode";
  private static final String SITE_TYPE = "crossingSiteTypeCode";
  private static final String SPECIAL_ACCESS_CODE = "specialAccessRqmtCode";

  /* Associations, and the column read through each. */
  private static final String SITE = "site";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String ORG_UNIT = "orgUnit";
  private static final String ORG_UNIT_CODE = "orgUnitCode";
  private static final String TYPE_CLASS = "typeClass";
  private static final String CLIENT_NAME = "clientName";
  private static final String DESCRIPTION = "description";

  /* On FOREST_SERVICE_BRIDGE and FOREST_SERVICE_CULVERT. */
  private static final String OWNING_STRUCTURE = "crossingStructureId";
  private static final String SUPERSTRUCTURE_TYPE = "superstructureTypeCode";
  private static final String CURB_TYPE = "structureCurbTypeCode";
  private static final String CULVERT_TYPE = "engineeredCulvertTypeCode";

  private static final String YES = "Y";
  /** A portable superstructure in storage, which has no abutments to record. */
  private static final String IN_STORAGE = "S";
  private static final String RECREATION = "REC";
  private static final List<String> REVIEWED_STATUSES = List.of("RVD", "ACC");

  /** The type/classes that carry a bridge row, and those that carry a culvert row. */
  private static final List<String> BRIDGES = List.of("PB", "TB");
  private static final List<String> CULVERTS = List.of("CUL", "WLC");

  private static final String WILDCARD = "%";

  private StructureSearchSpecifications() {}

  /**
   * Builds the predicate for a set of criteria — everything matches when nothing is set — sorted by
   * a results column first when one was chosen.
   *
   * @param sortBy    the results column whose header was clicked, or null for legacy's order
   * @param direction which way; ignored when {@code sortBy} is null
   */
  public static Specification<CrossingStructureEntity> matching(
      StructureSearchCriteria criteria, StructureSortColumn sortBy, Sort.Direction direction) {
    return (root, query, builder) -> {
      boolean projecting = returnsEntities(query);
      From<?, ?> site = joinOrFetch(root, SITE, JoinType.LEFT, projecting);
      From<?, ?> roadSection = joinOrFetch(site, ROAD_SECTION, JoinType.LEFT, projecting);
      From<?, ?> orgUnit = joinOrFetch(site, ORG_UNIT, JoinType.LEFT, projecting);

      List<Predicate> predicates = new ArrayList<>();
      structureCriteria(builder, root, criteria, predicates);
      siteCriteria(builder, site, roadSection, criteria, predicates);
      bridgeAndCulvertCriteria(builder, query, root, criteria, predicates);
      maintainedBy(builder, query, site, criteria.primaryUserName()).ifPresent(predicates::add);
      if (Boolean.TRUE.equals(criteria.incomplete())) {
        predicates.add(incomplete(builder, query, root, site));
      }

      if (projecting) {
        fetchAndOrder(root, site, roadSection, orgUnit, query, builder, sortBy, direction);
      }

      return predicates.isEmpty()
          ? builder.conjunction()
          : builder.and(predicates.toArray(new Predicate[0]));
    };
  }

  /** The criteria on {@code CROSSING_STRUCTURE} itself. */
  private static void structureCriteria(
      CriteriaBuilder builder,
      Root<CrossingStructureEntity> root,
      StructureSearchCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, root.get(STRUCTURE_NAME), criteria.structureName())
        .ifPresent(predicates::add);
    equalsText(builder, root.get(TYPE_CLASS_CODE), criteria.structureTypeClassCode())
        .ifPresent(predicates::add);
    equalsText(builder, root.get(SPECIAL_EQUIPMENT_CODE), criteria.specialEquipmentCode())
        .ifPresent(predicates::add);
    integerRange(builder, root.get(YEAR_BUILT),
        criteria.yearBuiltStart(), criteria.yearBuiltEnd()).ifPresent(predicates::add);
    integerRange(builder, root.get(LOAD_RESTRICTION_YEAR),
        criteria.loadRestrictionYearStart(), criteria.loadRestrictionYearEnd())
        .ifPresent(predicates::add);
    integerRange(builder, root.get(REPLACEMENT_YEAR),
        criteria.replacementYearStart(), criteria.replacementYearEnd()).ifPresent(predicates::add);
    integerRange(builder, root.get(CLOSURE_YEAR),
        criteria.closureYearStart(), criteria.closureYearEnd()).ifPresent(predicates::add);

    if (Boolean.TRUE.equals(criteria.portableStructure())) {
      predicates.add(builder.equal(root.get(PORTABLE_IND), YES));
    }
    // Legacy adds ACTIVE_IND = 'Y' unless the box is ticked: archived structures are left out by
    // default, and the toggle lets them back in rather than asking for them alone.
    if (!Boolean.TRUE.equals(criteria.includeArchived())) {
      predicates.add(builder.equal(root.get(ACTIVE_IND), YES));
    }
    if (Boolean.TRUE.equals(criteria.downrated())) {
      predicates.add(downrated(builder, root));
    }
  }

  /** The criteria on the site the structure stands on now, and on its road. */
  private static void siteCriteria(
      CriteriaBuilder builder,
      From<?, ?> site,
      From<?, ?> roadSection,
      StructureSearchCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, site.get(SITE_ID), criteria.siteId()).ifPresent(predicates::add);
    contains(builder, site.get(FOREST_FILE_ID), criteria.forestFileId()).ifPresent(predicates::add);
    contains(builder, site.get(ROAD_SECTION_ID), criteria.roadSectionId())
        .ifPresent(predicates::add);
    contains(builder, site.get(CROSSING_NAME), criteria.crossingName()).ifPresent(predicates::add);
    contains(builder, roadSection.get(ROAD_SECTION_NAME), criteria.forestServiceRoad())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_STATUS), criteria.siteStatusCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(SITE_TYPE), criteria.siteTypeCode()).ifPresent(predicates::add);
    equalsText(builder, site.get(SPECIAL_ACCESS_CODE), criteria.specialAccessCode())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(CLIENT_NUMBER), criteria.clientNumber())
        .ifPresent(predicates::add);
    equalsText(builder, site.get(CLIENT_LOCATION), criteria.clientLocationCode())
        .ifPresent(predicates::add);
    equalsNumber(builder, site.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
    equalsNumber(builder, site.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
        .ifPresent(predicates::add);
    decimalRange(builder, site.get(KILOMETRES), criteria.kiloStart(), criteria.kiloEnd())
        .ifPresent(predicates::add);
    decimalRange(builder, site.get(USER_KM), criteria.userKmStart(), criteria.userKmEnd())
        .ifPresent(predicates::add);
  }

  /** Superstructure Type, Curb Type and Culvert Type — columns of the bridge or culvert row. */
  private static void bridgeAndCulvertCriteria(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<CrossingStructureEntity> root,
      StructureSearchCriteria criteria,
      List<Predicate> predicates) {
    if (StringUtils.hasText(criteria.superstructureTypeCode())) {
      predicates.add(builder.exists(bridge(query, builder, root, (bridge, b) -> b.equal(
          bridge.get(SUPERSTRUCTURE_TYPE), criteria.superstructureTypeCode().trim()))));
    }
    if (StringUtils.hasText(criteria.structureCurbTypeCode())) {
      predicates.add(builder.exists(bridge(query, builder, root, (bridge, b) -> b.equal(
          bridge.get(CURB_TYPE), criteria.structureCurbTypeCode().trim()))));
    }
    if (StringUtils.hasText(criteria.culvertTypeCode())) {
      predicates.add(builder.exists(culvert(query, builder, root, (culvert, b) -> b.equal(
          culvert.get(CULVERT_TYPE), criteria.culvertTypeCode().trim()))));
    }
  }

  /**
   * "Downrated Structure?" — legacy's {@code downloadRated}: built for more than it now carries, or
   * built to a rating and never rated since.
   */
  private static Predicate downrated(CriteriaBuilder builder, Root<CrossingStructureEntity> root) {
    return builder.or(
        builder.greaterThan(root.get(DESIGN_LOAD_RATING), root.get(CURRENT_LOAD_RATING)),
        builder.and(
            builder.isNotNull(root.get(DESIGN_LOAD_RATING)),
            builder.isNull(root.get(CURRENT_LOAD_RATING))));
  }

  /**
   * "Incomplete Data?" — a structure missing any of the values that make it complete.
   *
   * <p><b>Legacy's structure page rules</b> — {@code Structure}, {@code Bridge} and
   * {@code Culvert.isComplete()} — so this filter and the structure page's Outstanding list always
   * agree about a structure. Legacy's own search query disagreed with its page in four places;
   * the page wins (decided 2026-10-06, {@code cbr-structure-page.local.md}):
   *
   * <ul>
   *   <li><b>Abutments</b> are required unless a portable superstructure is in storage (status
   *       {@code S}). Legacy's search had it the other way round.</li>
   *   <li><b>Estimated Load Restriction (year)</b> is not required on a recreation site.</li>
   *   <li><b>The site's maintainer</b> is not checked — legacy's page has the check commented
   *       out.</li>
   *   <li><b>A load rating</b> is any manual rating or reviewed inspection, not a stored design
   *       rating.</li>
   * </ul>
   *
   * <p>The bridge and culvert halves read "a bridge type with no complete bridge row": a missing
   * row is incomplete either way, so {@code NOT EXISTS} of a complete row says the same thing.
   * {@code StructureDetailService} applies the same rules to one structure, by name.
   */
  private static Predicate incomplete(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<CrossingStructureEntity> root,
      From<?, ?> site) {
    Predicate loadRatingMissing = builder.and(
        builder.or(
            builder.isNull(root.get(LOAD_RATING_UNKNOWN)),
            builder.notEqual(root.get(LOAD_RATING_UNKNOWN), YES)),
        builder.not(builder.exists(manualRating(query, builder, root))),
        builder.not(builder.exists(reviewedInspection(query, builder, root))));

    Predicate loadRestrictionMissing = builder.and(
        builder.isNull(root.get(LOAD_RESTRICTION_YEAR)),
        builder.or(
            builder.isNull(site.get(SITE_TYPE)),
            builder.notEqual(site.get(SITE_TYPE), RECREATION)));

    Predicate bridgeIncomplete = builder.and(
        root.get(TYPE_CLASS_CODE).in(BRIDGES),
        builder.not(builder.exists(bridge(query, builder, root, (bridge, b) -> b.and(
            b.isNotNull(bridge.get("totalBridgeLength")),
            b.isNotNull(bridge.get("deckWidth")),
            b.isNotNull(bridge.get("runningSurfaceCode")),
            b.isNotNull(bridge.get("deckTypeCode")),
            b.isNotNull(bridge.get(SUPERSTRUCTURE_TYPE)),
            // Both abutments, unless the portable superstructure is in storage.
            b.or(
                b.equal(bridge.get("portableSuperstructureStatusCode"), IN_STORAGE),
                b.and(
                    b.isNotNull(bridge.get("rightAbutmentCode")),
                    b.isNotNull(bridge.get("leftAbutmentCode")))))))));

    Predicate culvertIncomplete = builder.and(
        root.get(TYPE_CLASS_CODE).in(CULVERTS),
        builder.not(builder.exists(culvert(query, builder, root, (culvert, b) -> b.and(
            b.isNotNull(culvert.get("culvertNumber")),
            b.isNotNull(culvert.get("culvertLength")),
            b.isNotNull(culvert.get("openingHeight")),
            b.isNotNull(culvert.get("openingWidth")),
            b.isNotNull(culvert.get("engineeredCulvertMaterialCode")),
            b.isNotNull(culvert.get(CULVERT_TYPE)))))));

    return builder.or(
        loadRatingMissing,
        loadRestrictionMissing,
        builder.isNull(root.get(NEXT_PLANNED_INSPECTION)),
        builder.isNull(root.get(STRUCTURE_SOURCE)),
        builder.isNull(root.get(STRUCTURE_SITE_ID)),
        bridgeIncomplete,
        culvertIncomplete);
  }

  /** A manual load rating on this structure — one with no inspection behind it. */
  private static Subquery<Long> manualRating(
      CriteriaQuery<?> query, CriteriaBuilder builder, Root<CrossingStructureEntity> root) {
    Subquery<Long> rows = query.subquery(Long.class);
    Root<StructureLoadRatingEntity> rating = rows.from(StructureLoadRatingEntity.class);
    return rows.select(rating.get("structureLoadRatingId")).where(
        builder.equal(rating.get(OWNING_STRUCTURE), root.get(STRUCTURE_ID)),
        builder.isNull(rating.get("inspectionId")));
  }

  /**
   * An inspection of this structure whose report was reviewed — current status {@code RVD} or
   * {@code ACC}, with a reviewer — which counts as a load rating whether or not it recorded one, as
   * legacy's {@code FIND_LOAD_RATINGS_BY_STRC_ID} counts it.
   */
  private static Subquery<Long> reviewedInspection(
      CriteriaQuery<?> query, CriteriaBuilder builder, Root<CrossingStructureEntity> root) {
    Subquery<Long> rows = query.subquery(Long.class);
    Root<StructureInspectionEntity> inspection = rows.from(StructureInspectionEntity.class);
    Join<?, ?> status = inspection.join("currentStatus");
    return rows.select(inspection.get("inspectionId")).where(
        builder.equal(inspection.get(OWNING_STRUCTURE), root.get(STRUCTURE_ID)),
        status.get("inspectionReportStatusCode").in(REVIEWED_STATUSES),
        builder.isNotNull(inspection.get("inspectionReviewerId")));
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

  /**
   * "Designated Maintainer" typed as a name — the sites whose client's name matches, as on Site
   * Search. A subquery, because the results read names separately and nothing else needs the join.
   */
  private static Optional<Predicate> maintainedBy(
      CriteriaBuilder builder, CriteriaQuery<?> query, From<?, ?> site, String name) {
    if (!StringUtils.hasText(name)) {
      return Optional.empty();
    }
    Subquery<String> clients = query.subquery(String.class);
    Root<ClientPublicEntity> client = clients.from(ClientPublicEntity.class);
    clients.select(client.get(CLIENT_NUMBER)).where(builder.like(
        builder.upper(client.get(CLIENT_NAME)),
        WILDCARD + name.trim().toUpperCase(Locale.ROOT) + WILDCARD));
    return Optional.of(site.get(CLIENT_NUMBER).in(clients));
  }

  /**
   * Fetches the type/class a results row displays, and orders the page: the chosen column first,
   * when there is one, then legacy's order to break its ties. Only when projecting — a count has
   * no fetch and no order.
   */
  private static void fetchAndOrder(
      Root<CrossingStructureEntity> root,
      From<?, ?> site,
      From<?, ?> roadSection,
      From<?, ?> orgUnit,
      CriteriaQuery<?> query,
      CriteriaBuilder builder,
      StructureSortColumn sortBy,
      Sort.Direction direction) {
    From<?, ?> typeClass = (From<?, ?>) root.fetch(TYPE_CLASS, JoinType.LEFT);
    List<Order> order = new ArrayList<>();
    if (sortBy != null) {
      for (Expression<?> key :
          sortKeys(sortBy, root, site, roadSection, orgUnit, typeClass, builder)) {
        order.add(direction == Sort.Direction.DESC ? builder.desc(key) : builder.asc(key));
      }
    }
    order.addAll(legacyOrder(root, site, roadSection, orgUnit, builder));
    query.orderBy(order);
  }

  /**
   * Legacy's order: district code, road, road section, kilometre — then the structure's own id, so
   * the order is total and paging cannot show a row twice or skip one. Follows a chosen column,
   * breaking its ties.
   */
  private static List<Order> legacyOrder(
      Root<CrossingStructureEntity> root,
      From<?, ?> site,
      From<?, ?> roadSection,
      From<?, ?> orgUnit,
      CriteriaBuilder builder) {
    return List.of(
        builder.asc(orgUnit.get(ORG_UNIT_CODE)),
        builder.asc(roadSection.get(ROAD_SECTION_NAME)),
        builder.asc(site.get(ROAD_SECTION_ID)),
        builder.asc(site.get(KILOMETRES)),
        builder.asc(root.get(STRUCTURE_ID)));
  }

  /**
   * What a results column sorts on — the value it displays, through the same left joins. Text
   * sorts without regard to case, as on Site Search.
   */
  private static List<Expression<?>> sortKeys(
      StructureSortColumn column,
      Root<CrossingStructureEntity> root,
      From<?, ?> site,
      From<?, ?> roadSection,
      From<?, ?> orgUnit,
      From<?, ?> typeClass,
      CriteriaBuilder builder) {
    return switch (column) {
      case STRUCTURE_NAME -> List.of(builder.upper(root.get(STRUCTURE_NAME)));
      case SITE_ID -> List.of(builder.upper(site.get(SITE_ID)));
      case DISTRICT -> List.of(orgUnit.get(ORG_UNIT_CODE));
      case FOREST_SERVICE_ROAD -> List.of(builder.upper(roadSection.get(ROAD_SECTION_NAME)));
      case KILOMETRES -> List.of(site.get(KILOMETRES));
      case CROSSING_NAME -> List.of(builder.upper(site.get(CROSSING_NAME)));
      case TYPE_CLASS -> List.of(builder.upper(typeClass.get(DESCRIPTION)));
      case PROJECT_FILE -> List.of(
          builder.upper(site.get(FOREST_FILE_ID)), builder.upper(site.get(ROAD_SECTION_ID)));
      case MAINTAINER -> List.of(site.get(CLIENT_NUMBER), site.get(CLIENT_LOCATION));
    };
  }
}
