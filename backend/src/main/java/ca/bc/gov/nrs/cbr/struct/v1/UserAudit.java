package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Who moved a repair or a monitor to a status, and when — legacy's "User Audits" on the Repairs and
 * Monitoring tabs.
 */
public record UserAudit(String userId, LocalDate date) {

  /** Who and when, or null when neither is recorded. */
  public static UserAudit of(String userId, LocalDateTime when) {
    if (userId == null && when == null) {
      return null;
    }
    return new UserAudit(userId, when == null ? null : when.toLocalDate());
  }
}
