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
 * {@code THE.CROSSING_SITE_STATUS_CODE} — the statuses a crossing site can hold.
 *
 * <p>One of CBR's 37 code tables, all of which share this shape: the code as the primary key, a
 * description, an {@code EFFECTIVE_DATE}/{@code EXPIRY_DATE} range and an update stamp
 * (cbr-data-model.local.md §1).
 *
 * <p><b>Schema-qualified.</b> The application connects as {@code PROXY_FSA_CBR_READ_WRITE_USER},
 * which owns nothing — it holds {@code SELECT} on {@code THE}'s objects through
 * {@code FSA_CBR_READ_WRITE_ROLE}. Without {@code schema = "THE"} Hibernate resolves the table in
 * the proxy's own schema and the query fails with ORA-00942 (cbr-auth-and-roles.local.md §8).
 *
 * <p>Two deliberate departures from the EDUC-STUDENT-ASSESSMENT-API entities this follows:
 *
 * <ul>
 *   <li><b>{@code @Immutable} rather than {@code @DynamicUpdate}.</b> CBR does not own this table
 *       and the role carries no DML on it; the codes are maintained by the DBA migrations in
 *       nr-mof-db. Marking it immutable both says so and lets Hibernate skip dirty-checking on
 *       rows that are held in a cache for the life of the pod. That is also why there are getters
 *       but no setters — a setter on a row that can never be flushed is a misleading affordance.
 *   </li>
 *   <li><b>{@code equals}/{@code hashCode} over the code alone</b>, not over every field. The code
 *       is the primary key and a natural one; including the mutable-in-principle description and
 *       timestamps would make two loads of the same row compare unequal after a DBA edit.</li>
 * </ul>
 */
@Entity
@Immutable
@Table(name = "CROSSING_SITE_STATUS_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "crossingSiteStatusCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingSiteStatusCodeEntity {

  /** "Code representing a crossing site status." */
  @Id
  @Column(name = "CROSSING_SITE_STATUS_CODE", unique = true, length = 10)
  private String crossingSiteStatusCode;

  /** "Text describing a crossing site status code." */
  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  /**
   * "The date the data is effective and available as a valid site status."
   *
   * <p>Mapped but not filtered on — see
   * {@link ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteStatusCodeRepository#findAllInDisplayOrder()}
   * for why the search form deliberately offers expired statuses too.
   */
  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  /** "The date the data expires and can no longer be used as a valid site status." */
  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;

  /** "The date and time the content was last updated." */
  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
