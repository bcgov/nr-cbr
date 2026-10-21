package ca.bc.gov.nrs.cbr.repository.v1;

import java.math.BigDecimal;

/**
 * A structure's culvert as the Structure Inventory – Detailed CSV writes it: its type and material
 * as their descriptions, its dimensions, its gradient and its cover depths.
 */
public record StructureCulvertDetail(
    Long crossingStructureId,
    String culvertType,
    String culvertMaterial,
    Long openingHeight,
    Long openingWidth,
    BigDecimal culvertLength,
    BigDecimal slope,
    Long inletCoverDepth,
    Long outletCoverDepth,
    String openBottomSubstructure) {}
