package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDate;

/**
 * A P.Eng's review of an inspection — legacy's Accept dialog, which marks it Reviewed.
 *
 * @param loadRating               the inspection's load rating, GVW tonnes, a whole number from 0
 *                                 to 999999
 * @param reasonCode               {@code STRCTRE_LOAD_RATING_RSN_CODE}, one that fits the change
 *                                 from the structure's current rating
 * @param reasonComment            the reason's words — required for Other, kept only with it
 * @param nextRoutineDate          the next planned routine inspection; a routine inspection's only
 * @param routineFrequencyYears    years between routine inspections, 1 to 6
 * @param loadRatingReviewRequired the structure's Load Rating Review Required
 */
public record InspectionReviewRequest(
    Long loadRating,
    String reasonCode,
    String reasonComment,
    LocalDate nextRoutineDate,
    Integer routineFrequencyYears,
    boolean loadRatingReviewRequired) {}
