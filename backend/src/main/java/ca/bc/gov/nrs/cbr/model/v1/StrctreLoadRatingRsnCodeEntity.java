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
 * {@code THE.STRCTRE_LOAD_RATING_RSN_CODE} — Why a load rating was recorded.
 *
 * <p>Read only to decode a stored code on the structure page; the code and its description are all
 * that page needs.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_LOAD_RATING_RSN_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "strctreLoadRatingRsnCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrctreLoadRatingRsnCodeEntity {

  @Id
  @Column(name = "STRCTRE_LOAD_RATING_RSN_CODE", length = 6)
  private String strctreLoadRatingRsnCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
