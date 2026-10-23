package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDateTime;

/**
 * The bulletin, as shown and as edited.
 *
 * @param message   the message, as HTML kept to a safe set of tags; empty when there is none
 * @param updatedBy who last saved it, or {@code null} when there is no bulletin
 * @param updatedAt when, or {@code null}
 */
public record BulletinResponse(String message, String updatedBy, LocalDateTime updatedAt) {}
