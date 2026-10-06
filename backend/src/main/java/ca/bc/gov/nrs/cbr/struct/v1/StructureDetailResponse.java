package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One structure, for its page — {@code GET /api/v1/structures/{structureId}}: the header and the
 * Details tab of legacy's structure page ({@code cbr-structure-page.local.md}).
 *
 * <p>The frontend's {@code structureResponse.ts} is the other half of this contract and names every
 * field the same way. Every code arrives with its description; a value with nothing stored is null,
 * where legacy's page invented one (a next inspection a year out, "UNK" signs, a culvert frequency
 * of 3).
 */
public record StructureDetailResponse(
    String id,
    String structureName,
    /* False once archived — ACTIVE_IND = 'N'. */
    boolean active,
    CodeValue typeClass,
    Site site,
    Common details,
    /* Present for a bridge, null otherwise. */
    Bridge bridge,
    /* Present for a culvert, null otherwise. */
    Culvert culvert,
    /* Newest first. */
    List<Comment> comments,
    LoadRating loadRating,
    /* Structures this one replaced. */
    List<StructureRef> replaced,
    /* Structures that replaced this one. */
    List<StructureRef> replacedBy,
    Replacement replacement,
    /* Empty when the structure is complete. */
    List<OutstandingItem> outstanding) {

  /** A stored code and what it means; both null when nothing is stored. */
  public record CodeValue(String code, String description) {

    public static final CodeValue NONE = new CodeValue(null, null);
  }

  /** Another structure, as a link target. */
  public record StructureRef(String id, String structureName) {}

  /** The part of the page an outstanding item belongs to. */
  public enum Section { SITE, DETAILS, INSPECTIONS }

  /** One reason the structure counts as incomplete. */
  public record OutstandingItem(Section section, String label) {}

  /** The site the structure stands on — the header above the tabs. */
  public record Site(
      String siteId,
      String siteTypeCode,
      CodeValue siteStatus,
      CodeValue inspectionStatus,
      /* Forest District, or Recreation District on a recreation site. */
      String districtName,
      String managementAreaName,
      String forestServiceRoad,
      /* A recreation site's project name. */
      String projectName,
      String crossingName,
      String forestFileId,
      String roadSectionId,
      String kilometres,
      String maintainerLabel) {}

  /** Fields every structure has. */
  public record Common(
      CodeValue builtBy,
      Integer yearFabricated,
      Integer yearBuilt,
      Integer inventoryAddedYear,
      CodeValue source,
      Integer endOfDesignLifeYear,
      boolean asBuiltInfoOnFile,
      Long installationCost,
      Long materialCost,
      boolean portable) {}

  /** {@code FOREST_SERVICE_BRIDGE}. */
  public record Bridge(
      long spanCount,
      boolean needleBeams,
      BigDecimal lengthMetres,
      CodeValue superstructure,
      String superstructureComment,
      CodeValue deckType,
      String deckTypeComment,
      BigDecimal deckWidthMetres,
      CodeValue runningSurface,
      CodeValue curbType,
      String curbTypeComment,
      CodeValue leftAbutment,
      CodeValue rightAbutment,
      String abutmentComment) {}

  /** {@code FOREST_SERVICE_CULVERT}. */
  public record Culvert(
      Integer culvertNumber,
      /* Active culverts on the site — the "of N" after the number. */
      long culvertsOnSite,
      BigDecimal lengthMetres,
      BigDecimal gradient,
      CodeValue culvertType,
      CodeValue material,
      String materialComment,
      Long inletCoverDepthMm,
      Long outletCoverDepthMm,
      Long openingHeightMm,
      Long openingWidthMm,
      CodeValue headwallLocation,
      CodeValue openBottomSubstructure) {}

  /** A general comment on the structure. */
  public record Comment(String id, String text, String userId, LocalDateTime timestamp) {}

  /** One row of the load rating history. */
  public record LoadRatingEntry(
      String id,
      BigDecimal rating,
      CodeValue reason,
      String reasonComment,
      LocalDate date,
      String userId,
      LocalDate reviewedDate,
      LoadRatingStatus status,
      String inspectionId,
      boolean current) {}

  /** Where a load rating came from. */
  public enum LoadRatingStatus { MANUAL, REVIEWED }

  public record LoadRating(
      BigDecimal currentRating,
      /* Newest first. */
      List<LoadRatingEntry> history,
      CodeValue loadPostingSigns,
      boolean reviewRequired,
      CodeValue designVehicle,
      String designVehicleComment,
      BigDecimal designLoadRating) {}

  public record Replacement(
      Integer estimatedClosureYear,
      Integer estimatedReplacementYear,
      Integer estimatedLoadRestrictionYear,
      Long estimatedReplacementCost,
      String replacementCostComment) {}
}
