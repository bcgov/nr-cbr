package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * The current codes the Details tab's Load Rating Details offers, in one answer.
 *
 * @param loadPostingSigns {@code LOAD_POSTING_SIGN_WRRNTD_CODE}, by description
 * @param designVehicles   {@code DESIGN_VEHICLE_LOAD_CODE}, by description, each with the design
 *                         load rating it fixes
 * @param reasons          {@code STRCTRE_LOAD_RATING_RSN_CODE}, by description, each with the
 *                         changes of rating it fits
 */
public record LoadRatingCodesResponse(
    List<CodeOptionResponse> loadPostingSigns,
    List<DesignVehicle> designVehicles,
    List<Reason> reasons) {

  /**
   * A design vehicle.
   *
   * @param designLoadRating the tonnes it fixes, or null when the rating is typed instead
   */
  public record DesignVehicle(String code, String description, Long designLoadRating) {}

  /**
   * A load rating reason.
   *
   * @param deltas the changes of rating it is offered for when adding — {@code INC}, {@code DEC},
   *               {@code NOC}, or {@code UNDEF} for any
   */
  public record Reason(String code, String description, List<String> deltas) {}
}
