package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.INSPECTION_REPORT_STATUS_CODE} — where an inspection sits in its lifecycle.
 *
 * <p>Six codes, and they are the closest thing CBR has to a state machine: {@code OFL} offline,
 * {@code PRO} in progress, {@code SUB} submitted, {@code RVD} reviewed (terminal), {@code ACC}
 * accepted (a dead status that saving rewrites to {@code RVD}, still honoured by the outbound LRM
 * views) and {@code REJ} rejected (referenced but never set). The transitions are not enforced
 * anywhere — see cbr-workflows.local.md §1.
 *
 * <p><b>Not to be confused with {@code THE.INSPECTION_REPORT_STATUS}</b>, which is an entity table:
 * one row per transition, the only audit trail the workflow has. This is the code table it names.
 *
 * <p>Same shape, schema qualification and read-only treatment as
 * {@link CrossingSiteStatusCodeEntity}, which documents why each of those is the way it is.
 */
@Entity
@Immutable
@Table(name = "INSPECTION_REPORT_STATUS_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "inspectionReportStatusCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionReportStatusCodeEntity {

  /** "Code representing an inspection report status." */
  @Id
  @Column(name = "INSPECTION_REPORT_STATUS_CODE", unique = true, length = 10)
  private String inspectionReportStatusCode;

  /** "Text describing an inspection report status code." */
  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  /**
   * Mapped but not filtered on, which matters more here than elsewhere: {@code ACC} is expired and
   * still sits on inspections nobody has re-saved, so filtering would make exactly those rows
   * unfindable. See {@link ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusCodeRepository}.
   */
  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
