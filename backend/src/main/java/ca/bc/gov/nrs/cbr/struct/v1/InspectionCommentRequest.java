package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The inspector's comments on an inspection, as edited on its page.
 *
 * @param comment at most 4000
 */
public record InspectionCommentRequest(String comment) {}
