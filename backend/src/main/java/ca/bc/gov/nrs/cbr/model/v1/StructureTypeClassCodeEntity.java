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
 * {@code THE.STRUCTURE_TYPE_CLASS_CODE} — what kind of thing a crossing structure is.
 *
 * <p>More load-bearing than most code tables. {@code CROSSING_STRUCTURE.STRUCTURE_TYPE_CLASS_CODE}
 * is the discriminator that decides which subtype table holds the structure's detail — a bridge
 * goes to {@code FOREST_SERVICE_BRIDGE}, a culvert to {@code FOREST_SERVICE_CULVERT} — and it also
 * drives which inspection subsections and which repair types apply (cbr-data-model.local.md §2).
 *
 * <p>Same shape, schema qualification and read-only treatment as
 * {@link CrossingSiteStatusCodeEntity}, which documents why each of those is the way it is.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_TYPE_CLASS_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureTypeClassCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureTypeClassCodeEntity {

  /** "Code representing a structure type or class." */
  @Id
  @Column(name = "STRUCTURE_TYPE_CLASS_CODE", unique = true, length = 10)
  private String structureTypeClassCode;

  /** "Text describing a structure type class code." */
  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  /**
   * "The date the data is effective and available as a valid structure type or class."
   *
   * <p>Filtered on, unlike the site-status and inspection-status lists — see
   * {@link ca.bc.gov.nrs.cbr.repository.v1.StructureTypeClassCodeRepository#findAllCurrentInDisplayOrder()},
   * which explains why legacy treats this one table differently from the others on the same form.
   */
  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  /** "The date the data expires and can no longer be used as a valid structure type or class." */
  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;

  /** "The date and time the content was last updated." */
  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
