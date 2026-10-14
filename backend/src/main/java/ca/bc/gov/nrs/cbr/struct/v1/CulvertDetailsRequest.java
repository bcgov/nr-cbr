package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;

/**
 * An edit to the Details tab's Culvert card — legacy's culvert rows, with the structure's Portable
 * Structure.
 *
 * @param portable             {@code CROSSING_STRUCTURE.PORTABLE_STRUCTURE_IND}
 * @param culvertNumber        required once it has a value
 * @param lengthMetres         required once it has a value
 * @param gradient             {@code SLOPE}
 * @param culvertTypeCode      required once it has a value; ignored on a Wood Log Culvert, which is
 *                             always BOX
 * @param materialCode         required once it has a value
 * @param materialComment      required for Other
 * @param inletCoverDepthMm    whole millimetres
 * @param outletCoverDepthMm   whole millimetres
 * @param openingHeightMm      whole millimetres; required once it has a value
 * @param openingWidthMm       whole millimetres; required once it has a value
 * @param headwallLocationCode optional
 * @param openBottomCode       required for an open-bottom (OPN) culvert; ignored on a Wood Log
 *                             Culvert
 */
public record CulvertDetailsRequest(
    Boolean portable,
    Integer culvertNumber,
    BigDecimal lengthMetres,
    BigDecimal gradient,
    String culvertTypeCode,
    String materialCode,
    String materialComment,
    Long inletCoverDepthMm,
    Long outletCoverDepthMm,
    Long openingHeightMm,
    Long openingWidthMm,
    String headwallLocationCode,
    String openBottomCode) {}
