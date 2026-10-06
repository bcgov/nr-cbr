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
 * {@code THE.LOAD_POSTING_SIGN_WRRNTD_CODE} — Whether load posting signs are warranted.
 *
 * <p>Read only to decode a stored code on the structure page; the code and its description are all
 * that page needs.
 */
@Entity
@Immutable
@Table(name = "LOAD_POSTING_SIGN_WRRNTD_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "loadPostingSignWrrntdCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadPostingSignWrrntdCodeEntity {

  @Id
  @Column(name = "LOAD_POSTING_SIGN_WRRNTD_CODE", length = 10)
  private String loadPostingSignWrrntdCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
