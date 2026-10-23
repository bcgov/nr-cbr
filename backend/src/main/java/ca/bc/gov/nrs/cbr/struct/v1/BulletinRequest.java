package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A new bulletin message, as HTML. Checked by the service, as the other forms are, so each fault is
 * shown beside the field.
 *
 * @param message the message
 */
public record BulletinRequest(String message) {}
