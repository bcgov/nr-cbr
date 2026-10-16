package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;

/**
 * An inspection's Hydraulic Hazards, as edited on its page — legacy's tab of the same name.
 *
 * @param presentWaterDepth  Estimated Present Water Level Depth (m)
 * @param presentWaterWidth  Estimated Present Water Level Width (m)
 * @param highWaterClearance Estimated High Water Level Clearance (m)
 * @param comment            at most 2000
 */
public record HazardsUpdateRequest(
    BigDecimal presentWaterDepth,
    BigDecimal presentWaterWidth,
    BigDecimal highWaterClearance,
    Boolean highWater,
    Boolean debris,
    Boolean scour,
    Boolean ice,
    Boolean aggradation,
    String comment) {}
