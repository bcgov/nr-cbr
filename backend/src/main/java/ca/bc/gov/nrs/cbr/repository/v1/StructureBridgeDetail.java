package ca.bc.gov.nrs.cbr.repository.v1;

import java.math.BigDecimal;

/**
 * A structure's bridge as the Structure Inventory – Detailed report prints it: its codes as their
 * descriptions, its length and deck width, where a portable superstructure is, and how many spans
 * it has.
 */
public record StructureBridgeDetail(
    Long crossingStructureId,
    String superstructureType,
    String deckType,
    String leftAbutment,
    String rightAbutment,
    String curbType,
    BigDecimal totalLength,
    String portableLocationDetail,
    Long spanCount,
    BigDecimal deckWidth,
    String runningSurface) {}
