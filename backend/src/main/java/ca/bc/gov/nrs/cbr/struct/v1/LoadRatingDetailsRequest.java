package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An edit to the fields of the Details tab's Load Rating Details.
 *
 * @param loadPostingSignsCode {@code LOAD_POSTING_SIGN_WRRNTD_CODE}, required; Level 2
 * @param reviewRequired       {@code LOAD_RATING_UNKNOWN_INDICATOR}; Level 1
 * @param designVehicleCode    {@code DESIGN_VEHICLE_LOAD_CODE}; Level 2
 * @param designVehicleComment required for {@code OTHER}; a P.Eng's, as legacy
 * @param designLoadRating     tonnes, at least 1; set from the vehicle when it fixes one
 */
public record LoadRatingDetailsRequest(
    String loadPostingSignsCode,
    Boolean reviewRequired,
    String designVehicleCode,
    String designVehicleComment,
    Long designLoadRating) {}
