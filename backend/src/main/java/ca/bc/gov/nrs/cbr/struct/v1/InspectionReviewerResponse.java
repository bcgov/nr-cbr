package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One option of Inspection Search's Reviewed By select: a user holding
 * {@code CBR_INSPECTION_REVIEWER}.
 *
 * @param userId      the submitted value, as CBR stores user IDs — {@code IDIR\JSMITH}. The search
 *                    finds the inspections reviewed under it.
 * @param displayName "Last, First", as legacy labels a reviewer
 */
public record InspectionReviewerResponse(String userId, String displayName) {}
