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
 * {@code THE.STRCTRE_INSPECTION_TYPE_CODE} — what kind of inspection was carried out, e.g.
 * {@code ROUT} for a routine one.
 *
 * <p>Held on {@code STRUCTURE_INSPECTION.STRCTRE_INSPECTION_TYPE_CODE} and read by the outbound LRM
 * views (cbr-data-model.local.md §3), so the codes are part of an integration contract and not only
 * a label.
 *
 * <p>The class keeps Oracle's abbreviated name rather than expanding it to
 * {@code StructureInspectionType…}: unlike {@code STRUCTURE_INSPCTN_STATUS_CODE}, where the short
 * form hides that the status belongs to a site's structures, nothing is lost here, and the name
 * matching the table is worth more than four characters of English.
 *
 * <p>Same shape, schema qualification and read-only treatment as
 * {@link CrossingSiteStatusCodeEntity}, which documents why each of those is the way it is.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_INSPECTION_TYPE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "strctreInspectionTypeCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrctreInspectionTypeCodeEntity {

  @Id
  @Column(name = "STRCTRE_INSPECTION_TYPE_CODE", unique = true, length = 10)
  private String strctreInspectionTypeCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  /**
   * Mapped but not filtered on — the legacy search form asks for this list with the expiry filter
   * switched off, because inspections recorded years ago still carry types that have since been
   * retired. See
   * {@link ca.bc.gov.nrs.cbr.repository.v1.StrctreInspectionTypeCodeRepository}.
   */
  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
