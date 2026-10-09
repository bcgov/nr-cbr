package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A structure comment's text, to add or to change one.
 *
 * @param comment required, at most 2000 bytes
 */
public record CommentRequest(String comment) {}
