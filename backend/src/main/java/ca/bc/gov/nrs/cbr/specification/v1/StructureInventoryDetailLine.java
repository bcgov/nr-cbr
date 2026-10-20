package ca.bc.gov.nrs.cbr.specification.v1;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One structure as the Structure Inventory – Detailed report reads it: what its PDF block and its
 * CSV row show of the structure and its site — the block's codes already as their descriptions, the
 * CSV's further codes and org units as they are stored, named afterwards. The bridge, the culvert
 * and the routine inspections are read beside it, a batch of structures at a time.
 */
public record StructureInventoryDetailLine(
    Long crossingStructureId,
    String crossingStructureName,
    String typeClassDescription,
    String portableStructureInd,
    BigDecimal designLoadRating,
    BigDecimal currentLoadRating,
    Integer yearBuilt,
    String loadRatingUnknownIndicator,
    String crossingSiteId,
    String siteStatusDescription,
    BigDecimal pointOfCommencementDistance,
    BigDecimal userKm,
    String forestFileId,
    String roadSectionId,
    String districtName,
    String roadSectName,
    String crossingName,
    String capitalRoadInd,
    // The CSV's further columns
    String designVehicleLoadCode,
    String builtByCode,
    LocalDate nextPlannedInspectionDate,
    Integer lightVehicleReplacementYear,
    Long estimatedReplacementCost,
    Integer estimatedClosureYear,
    Integer fullLogHaulReplacementYear,
    String structureSourceCode,
    String clientNumber,
    String crossingSiteTypeCode,
    BigDecimal longitude,
    BigDecimal latitude,
    Long managementOrgUnitNo,
    Long businessAreaOrgUnitNo,
    Long regionOrgUnitNo,
    String structureInspectionStatusCode) {}
