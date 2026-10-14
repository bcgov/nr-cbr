package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * The current codes the Details tab's Culvert card offers, in one answer.
 *
 * @param culvertTypes            {@code ENGINEERED_CULVERT_TYPE_CODE}, by description
 * @param materials               {@code ENGINEERED_CLVRT_MATERIAL_CODE}, by description
 * @param headwallLocations       {@code HEADWALL_LOCATION_CODE}, by description
 * @param openBottomSubstructures {@code OPN_BTOM_CLVRT_SUBSTRCTRE_CODE}, by description
 */
public record CulvertCodesResponse(
    List<CodeOptionResponse> culvertTypes,
    List<CodeOptionResponse> materials,
    List<CodeOptionResponse> headwallLocations,
    List<CodeOptionResponse> openBottomSubstructures) {}
