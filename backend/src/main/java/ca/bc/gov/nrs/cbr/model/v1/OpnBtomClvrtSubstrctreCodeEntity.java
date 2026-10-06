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
 * {@code THE.OPN_BTOM_CLVRT_SUBSTRCTRE_CODE} — An open-bottom culvert's substructure.
 *
 * <p>Read only to decode a stored code on the structure page; the code and its description are all
 * that page needs.
 */
@Entity
@Immutable
@Table(name = "OPN_BTOM_CLVRT_SUBSTRCTRE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "opnBtomClvrtSubstrctreCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpnBtomClvrtSubstrctreCodeEntity {

  @Id
  @Column(name = "OPN_BTOM_CLVRT_SUBSTRCTRE_CODE", length = 10)
  private String opnBtomClvrtSubstrctreCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
