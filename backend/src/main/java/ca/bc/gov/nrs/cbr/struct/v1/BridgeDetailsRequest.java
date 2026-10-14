package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;

/**
 * An edit to the Details tab's Bridge card — legacy's bridge rows, with the structure's Type/Class
 * and Portable Structure, whose rules check the bridge's own fields.
 *
 * @param typeClassCode         {@code PB} Durable Bridge or {@code TB} Non-Durable Bridge
 * @param portable              {@code CROSSING_STRUCTURE.PORTABLE_STRUCTURE_IND}
 * @param needleBeams           {@code NEEDLE_BEAM_IND}
 * @param totalBridgeLength     metres, at least the spans' total; required once it has a value
 * @param superstructureTypeCode required once it has a value
 * @param superstructureComment required for Other
 * @param deckTypeCode          required once it has a value
 * @param deckTypeComment       required for Other
 * @param deckWidth             metres; required once it has a value
 * @param runningSurfaceCode    required once it has a value
 * @param curbTypeCode          required for a Durable Bridge
 * @param curbTypeComment       required for Other
 * @param leftAbutmentCode      required once it has a value
 * @param rightAbutmentCode     required once it has a value
 * @param abutmentComment       one for both, required when either is Other
 */
public record BridgeDetailsRequest(
    String typeClassCode,
    Boolean portable,
    Boolean needleBeams,
    BigDecimal totalBridgeLength,
    String superstructureTypeCode,
    String superstructureComment,
    String deckTypeCode,
    String deckTypeComment,
    BigDecimal deckWidth,
    String runningSurfaceCode,
    String curbTypeCode,
    String curbTypeComment,
    String leftAbutmentCode,
    String rightAbutmentCode,
    String abutmentComment) {}
