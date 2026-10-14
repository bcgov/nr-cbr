package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.STRCTRE_LOAD_RATING_RSN_XREF} — which reasons fit which change of load rating: an
 * increase ({@code INC}), a decrease ({@code DEC}), no change ({@code NOC}), or any ({@code UNDEF}),
 * as legacy's {@code FIND_LOAD_RATING_REASONS} reads it for the Add list.
 *
 * <p>The table has no key of its own; the pair is used as one.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_LOAD_RATING_RSN_XREF", schema = "THE")
@IdClass(LoadRatingReasonXrefEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadRatingReasonXrefEntity {

  @Id
  @Column(name = "STRCTRE_LOAD_RATING_RSN_CODE", length = 10)
  private String reasonCode;

  @Id
  @Column(name = "STRCTRE_LOAD_RATING_DLTA_CODE", length = 10)
  private String deltaCode;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String reasonCode;
    private String deltaCode;
  }
}
