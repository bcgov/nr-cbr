package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.CROSSING_SITE_STATUS_XREF} — the display order of the site statuses.
 *
 * <p>The table comment is the whole of it: <i>"This table holds the relationship between the
 * crossing site status code and its display order for dropdown ordering."</i> It is a sibling of
 * the code table rather than a column on it, so it is mapped as its own entity and joined
 * explicitly; there is no association on
 * {@link CrossingSiteStatusCodeEntity}, which keeps the code table free of a dependency on a
 * presentation concern.
 *
 * <p>{@code DISPLAY_ORDER} is nullable and the row itself is optional, which is load-bearing —
 * see {@link ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteStatusCodeRepository}.
 */
@Entity
@Immutable
@Table(name = "CROSSING_SITE_STATUS_XREF", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "crossingSiteStatusCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingSiteStatusXrefEntity {

  @Id
  @Column(name = "CROSSING_SITE_STATUS_CODE", unique = true, length = 10)
  private String crossingSiteStatusCode;

  @Column(name = "DISPLAY_ORDER")
  private Integer displayOrder;
}
