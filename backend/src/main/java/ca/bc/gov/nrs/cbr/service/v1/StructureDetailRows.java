package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignVehicleLoadCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredClvrtMaterialCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredCulvertTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.HeadwallLocationCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OpnBtomClvrtSubstrctreCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RecreationProjectEntity;
import ca.bc.gov.nrs.cbr.model.v1.RunningSurfaceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureSourceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCommentRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureReplacementXrefRepository;
import jakarta.persistence.EntityManager;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rows the Structure Detail Report (legacy CBR006) is made from: one structure's values, keyed
 * by the PDF template's field names — raw, as the database holds them, for the PDF and the CSV each
 * to format in their own way ({@link StructureDetailReportService}). Codes come with their
 * descriptions; comments and replacements come combined, as legacy combined them.
 *
 * <p>Legacy read them from {@code CBR_REPORTING.STRCTRE_DTL_RPT}, which left-joins the structure's
 * bridge and culvert records; a structure with more than one of either is a row per pair, and the
 * report prints in full for each, as legacy's did. Every code is read through the entity manager
 * by its key, as legacy left-joins each table: a code with no row there prints blank. A structure
 * with no type/class, or one its table lacks, has no row, as legacy's inner join gave it none —
 * the report prints its header only.
 *
 * <p>Where it differs from legacy:
 * <ul>
 *   <li><b>Current Load Rating</b> is the structure's stored current rating, the one the structure
 *       page shows, which every change of rating sets by legacy's own rule — rather than legacy's
 *       {@code FIND_CRNT_LD_RATING_BY_STRC_ID} worked out again.</li>
 *   <li><b>Comments</b> date each comment {@code yyyy-MM-dd}, not in Oracle's default timestamp
 *       format.</li>
 * </ul>
 */
@Component
@Transactional(readOnly = true)
public class StructureDetailRows {

  private static final String YES = "Y";

  /** The site type whose road is a recreation project's name. */
  private static final String RECREATION = "REC";

  /** How a comment's date prints in the Comments and Planned Inspection Comments. */
  private static final DateTimeFormatter COMMENT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final EntityManager entityManager;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final StructureCommentRepository comments;
  private final StructureReplacementXrefRepository replacements;
  private final StructureInventoryNames names;

  public StructureDetailRows(
      EntityManager entityManager,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      StructureCommentRepository comments,
      StructureReplacementXrefRepository replacements,
      StructureInventoryNames names) {
    this.entityManager = entityManager;
    this.bridges = bridges;
    this.culverts = culverts;
    this.comments = comments;
    this.replacements = replacements;
    this.names = names;
  }

  /**
   * The structure's rows: one for each pair of its bridge and culvert records, or one alone when
   * it has neither.
   *
   * @throws StructureNotFoundException when there is no such structure, or it is archived
   */
  public List<Map<String, Object>> rows(long structureId) {
    CrossingStructureEntity structure = structure(structureId);
    String typeClass = describe(StructureTypeClassCodeEntity.class,
        structure.getStructureTypeClassCode(), StructureTypeClassCodeEntity::getDescription);
    if (typeClass == null) {
      return List.of();
    }
    Map<String, Object> common = common(structure);
    common.put("STRUCTURE_TYPE_CLASS_DESC", typeClass);
    List<ForestServiceBridgeEntity> bridgeRecords = orNone(
        bridges.findByCrossingStructureIdOrderByForestServiceBridgeId(structureId));
    List<ForestServiceCulvertEntity> culvertRecords = orNone(
        culverts.findByCrossingStructureIdOrderByForestServiceCulvertId(structureId));
    List<ForestServiceBridgeSpanEntity> spans = bridges.findSpans(structureId);
    common.put("SPAN_LENGTHS", spans.stream()
        .map(span -> plain(span.getSpanLength()))
        .collect(Collectors.joining(", ")));
    Map<Long, Long> spanCounts = spans.stream().collect(Collectors.groupingBy(
        ForestServiceBridgeSpanEntity::getForestServiceBridgeId, Collectors.counting()));

    return bridgeRecords.stream()
        .flatMap(bridge -> culvertRecords.stream().map(culvert -> {
          Map<String, Object> row = new LinkedHashMap<>(common);
          if (bridge != null) {
            bridge(bridge, spanCounts, row);
          }
          if (culvert != null) {
            culvert(culvert, row);
          }
          return row;
        }))
        .toList();
  }

  /** The structure, if it is one the report is for: legacy's {@code ACTIVE_IND = 'Y'}. */
  private CrossingStructureEntity structure(long structureId) {
    CrossingStructureEntity structure = entityManager.find(CrossingStructureEntity.class, structureId);
    if (structure == null || !YES.equals(structure.getActiveInd())) {
      throw new StructureNotFoundException(structureId);
    }
    return structure;
  }

  /** What every row says: the structure, its site, its comments and its replacements. */
  private Map<String, Object> common(CrossingStructureEntity structure) {
    long id = structure.getCrossingStructureId();
    Map<String, Object> row = new HashMap<>();
    row.put("CROSSING_STRUCTURE_NAME", structure.getCrossingStructureName());
    row.put("CROSSING_SITE_ID", structure.getCrossingSiteId());
    row.put("BUILT_FOR", describe(BuiltByCodeEntity.class, structure.getBuiltByCode(),
        BuiltByCodeEntity::getDescription));
    row.put("YEAR_BUILT", structure.getYearBuilt());
    row.put("YEAR_FABRICATED", structure.getYearFabricated());
    row.put("ORIGINAL_REPLACEMENT_DATE", structure.getOriginalReplacementYear());
    row.put("INVENTORY_ADDED_YEAR", structure.getInventoryAddedYear());
    row.put("STRUCTURE_SOURCE", describe(StructureSourceCodeEntity.class,
        structure.getStructureSourceCode(), StructureSourceCodeEntity::getDescription));
    row.put("LOAD_RATING", structure.getCurrentLoadRating());
    row.put("DESIGN_LOAD_RATING", structure.getDesignLoadRating());
    row.put("DESIGN_VEHICLE_LOAD_CODE", structure.getDesignVehicleLoadCode());
    row.put("DESIGN_VEHICLE", describe(DesignVehicleLoadCodeEntity.class,
        structure.getDesignVehicleLoadCode(), DesignVehicleLoadCodeEntity::getDescription));
    row.put("AS_BUILT_INFO_PRESENT_IND", structure.getAsBuiltInfoPresentInd());
    row.put("INSTALLATION_COST", structure.getInstallationCost());
    row.put("MATERIAL_COST", structure.getMaterialCost());
    row.put("PORTABLE_STRUCTURE_IND", structure.getPortableStructureInd());
    row.put("ESTIMATED_CLOSURE_DATE", structure.getEstimatedClosureYear());
    row.put("LIGHT_VEHICLE_TRFFC_RPLCMNT_DT", structure.getLightVehicleReplacementYear());
    row.put("FULL_LOG_HAUL_TRFFC_RPLCMNT_DT", structure.getFullLogHaulReplacementYear());
    row.put("ESTIMATED_REPLACEMENT_COST", structure.getEstimatedReplacementCost());
    row.put("ESTIMATED_REPLACEMENT_COST_CMT", structure.getEstimatedReplacementCostCmt());
    row.put("NEXT_PLANNED_INSPECTION_DATE", structure.getNextPlannedInspectionDate());
    row.put("STRUCT_COMMENT", comments(id, "N"));
    row.put("PLANNED_COMMENT", comments(id, YES));
    row.put("REPLACES", structureIds(replacements.findByReplacesStructureNumber(id),
        StructureReplacementXrefEntity::getReplacedStructureNumber));
    row.put("REPLACED_BY", structureIds(replacements.findByReplacedStructureNumber(id),
        StructureReplacementXrefEntity::getReplacesStructureNumber));
    CrossingSiteEntity site = structure.getCrossingSiteId() == null
        ? null
        : entityManager.find(CrossingSiteEntity.class, structure.getCrossingSiteId());
    if (site != null) {
      site(site, row);
    }
    return row;
  }

  /** The General section's site fields. */
  private void site(CrossingSiteEntity site, Map<String, Object> row) {
    row.put("CROSSING_SITE_TYPE_CODE", site.getCrossingSiteTypeCode());
    row.put("CROSSING_NAME", site.getCrossingName());
    row.put("FOREST_SERVICE_ROAD", road(site));
    row.put("POINT_OF_COMMENCEMENT_DISTANCE", site.getPointOfCommencementDistance());
    row.put("FOREST_FILE_ID", site.getForestFileId());
    row.put("ROAD_SECTION_ID", site.getRoadSectionId());
    row.put("ORG_UNIT_NAME", orgUnit(site.getOrgUnitNo()));
    row.put("MANAGEMENT_AREA", orgUnit(site.getManagementOrgUnitNo()));
    if (site.getClientNumber() != null) {
      row.put("CLIENT_NAME", names.clientNames(List.of(site.getClientNumber()))
          .get(site.getClientNumber()));
    }
  }

  /** The Bridge section's fields. */
  private void bridge(
      ForestServiceBridgeEntity bridge, Map<Long, Long> spanCounts, Map<String, Object> row) {
    row.put("FOREST_SERVICE_BRIDGE_ID", bridge.getForestServiceBridgeId());
    row.put("SPAN_COUNT", spanCounts.getOrDefault(bridge.getForestServiceBridgeId(), 0L));
    row.put("NEEDLE_BEAM_IND", bridge.getNeedleBeamInd());
    row.put("TOTAL_BRIDGE_LENGTH", bridge.getTotalBridgeLength());
    row.put("SUPERSTRUCTURE", describe(SuperstructureTypeCodeEntity.class,
        bridge.getSuperstructureTypeCode(), SuperstructureTypeCodeEntity::getDescription));
    row.put("SUPERSTRUCTURE_COMMENT", bridge.getSuperstructureComment());
    row.put("DECK_WIDTH", bridge.getDeckWidth());
    row.put("DECK_TYPE", describe(DeckTypeCodeEntity.class, bridge.getDeckTypeCode(),
        DeckTypeCodeEntity::getDescription));
    row.put("DECK_TYPE_COMMENT", bridge.getDeckTypeComment());
    row.put("RUNNING_SURFACE", describe(RunningSurfaceCodeEntity.class,
        bridge.getRunningSurfaceCode(), RunningSurfaceCodeEntity::getDescription));
    row.put("LEFT_ABUTMENT", describe(AbutmentCodeEntity.class, bridge.getLeftAbutmentCode(),
        AbutmentCodeEntity::getDescription));
    row.put("RIGHT_ABUTMENT", describe(AbutmentCodeEntity.class, bridge.getRightAbutmentCode(),
        AbutmentCodeEntity::getDescription));
    row.put("ABUTMENT_COMMENT", bridge.getAbutmentComment());
    row.put("STRUCTURE_CURB_TYPE", describe(StructureCurbTypeCodeEntity.class,
        bridge.getStructureCurbTypeCode(), StructureCurbTypeCodeEntity::getDescription));
    row.put("STRUCTURE_CURB_TYPE_CMT", bridge.getStructureCurbTypeCmt());
  }

  /** The Culvert section's fields. */
  private void culvert(ForestServiceCulvertEntity culvert, Map<String, Object> row) {
    row.put("FOREST_SERVICE_CULVERT_ID", culvert.getForestServiceCulvertId());
    row.put("CULVERT_NUMBER", culvert.getCulvertNumber());
    row.put("CULVERT_LENGTH", culvert.getCulvertLength());
    row.put("SLOPE", culvert.getSlope());
    row.put("CULVERT_TYPE", describe(EngineeredCulvertTypeCodeEntity.class,
        culvert.getEngineeredCulvertTypeCode(), EngineeredCulvertTypeCodeEntity::getDescription));
    row.put("CULVERT_MATERIAL", describe(EngineeredClvrtMaterialCodeEntity.class,
        culvert.getEngineeredCulvertMaterialCode(),
        EngineeredClvrtMaterialCodeEntity::getDescription));
    row.put("CULVERT_MATERIAL_COMMENT", culvert.getCulvertMaterialComment());
    row.put("INLET_COVER_DEPTH", culvert.getInletCoverDepth());
    row.put("OUTLET_COVER_DEPTH", culvert.getOutletCoverDepth());
    row.put("OPENING_HEIGHT", culvert.getOpeningHeight());
    row.put("OPENING_WIDTH", culvert.getOpeningWidth());
    row.put("HEADWALL_LOCATION", describe(HeadwallLocationCodeEntity.class,
        culvert.getHeadwallLocationCode(), HeadwallLocationCodeEntity::getDescription));
    row.put("OPN_BOTTOM_CLVRT_SUB", describe(OpnBtomClvrtSubstrctreCodeEntity.class,
        culvert.getOpnBtomClvrtSubstrctreCode(), OpnBtomClvrtSubstrctreCodeEntity::getDescription));
  }

  /**
   * The site's road: a recreation site's project name, any other site's road section name —
   * legacy's {@code FOREST_SERVICE_ROAD}.
   */
  private String road(CrossingSiteEntity site) {
    if (site.getForestFileId() == null) {
      return null;
    }
    if (RECREATION.equals(site.getCrossingSiteTypeCode())) {
      RecreationProjectEntity project =
          entityManager.find(RecreationProjectEntity.class, site.getForestFileId());
      return project == null ? null : project.getProjectName();
    }
    if (site.getRoadSectionId() == null) {
      return null;
    }
    CbrRoadSectionEntity section = entityManager.find(CbrRoadSectionEntity.class,
        new CbrRoadSectionEntity.Key(site.getForestFileId(), site.getRoadSectionId()));
    return section == null ? null : section.getRoadSectName();
  }

  /** An org unit's name as legacy reads it, from {@code CBR_ORG_UNIT}. */
  private String orgUnit(Long orgUnitNo) {
    return describe(CbrOrgUnitEntity.class, orgUnitNo, CbrOrgUnitEntity::getOrgUnitName);
  }

  /**
   * One kind of comment, newest first, one to a line — legacy's {@code GET_COMBINED_COMMENTS}:
   * who entered it, when it was last updated, and what it says.
   */
  private String comments(long structureId, String planned) {
    return comments.findByKind(structureId, planned).stream()
        .map(StructureDetailRows::comment)
        .collect(Collectors.joining("\n"));
  }

  /** A comment as the reports print it: who entered it, when it was last updated, what it says. */
  static String comment(StructureCommentEntity comment) {
    String updated = comment.getUpdateTimestamp() == null
        ? ""
        : comment.getUpdateTimestamp().format(COMMENT_DATE);
    return comment.getEntryUserid() + " on " + updated + ": "
        + Objects.toString(comment.getStructureComment(), "");
  }

  /** The other structures in a replacement, by their internal id as legacy prints them. */
  private static String structureIds(
      List<StructureReplacementXrefEntity> links,
      Function<StructureReplacementXrefEntity, Long> other) {
    return links.stream()
        .map(other)
        .filter(Objects::nonNull)
        .sorted()
        .map(Object::toString)
        .collect(Collectors.joining(", "));
  }

  /** A code's description by its key, or nothing when unset or not in its table. */
  private <E, K> String describe(Class<E> table, K key, Function<E, String> description) {
    if (key == null) {
      return null;
    }
    E found = entityManager.find(table, key);
    return found == null ? null : description.apply(found);
  }

  /** The records, or a single "none" when there are none: a left join's one row of nulls. */
  private static <T> List<T> orNone(List<T> records) {
    return records.isEmpty() ? Collections.singletonList(null) : records;
  }
}
