package ca.bc.gov.nrs.cbr.specification.v1;

import java.math.BigDecimal;

/**
 * One structure as the Structure Inventory reports read it: the values a line prints and no more —
 * not the structure and site entities, whose ninety-odd columns a report would read only to drop.
 *
 * <p>The bridge or culvert length and the routine inspection are read beside it, a batch of
 * structures at a time. The road name is LRMOPS's, looked up afterwards (2026-11-25).
 */
public record StructureInventoryLine(
    Long crossingStructureId,
    String crossingStructureName,
    String structureTypeClassCode,
    String portableStructureInd,
    BigDecimal currentLoadRating,
    Integer yearBuilt,
    Integer fullLogHaulReplacementYear,
    String crossingSiteId,
    String forestFileId,
    String roadSectionId,
    String crossingName,
    BigDecimal pointOfCommencementDistance,
    String capitalRoadInd) {}
