package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * The current codes the Details tab's Bridge card offers, in one answer — five short lists the card
 * always needs together.
 *
 * @param superstructureTypes {@code SUPERSTRUCTURE_TYPE_CODE}, by description
 * @param deckTypes           {@code DECK_TYPE_CODE}, by description
 * @param runningSurfaces     {@code RUNNING_SURFACE_CODE}, by description
 * @param curbTypes           {@code STRUCTURE_CURB_TYPE_CODE}, by description
 * @param abutments           {@code ABUTMENT_CODE}, by description — for both abutments
 */
public record BridgeCodesResponse(
    List<CodeOptionResponse> superstructureTypes,
    List<CodeOptionResponse> deckTypes,
    List<CodeOptionResponse> runningSurfaces,
    List<CodeOptionResponse> curbTypes,
    List<CodeOptionResponse> abutments) {}
