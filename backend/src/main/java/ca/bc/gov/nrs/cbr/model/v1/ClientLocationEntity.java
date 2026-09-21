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
 * {@code THE.CLIENT_LOCATION} — one address at which a Forest Client operates.
 *
 * <p><b>The second half of a Designated Maintainer.</b> {@code CROSSING_SITE} records its
 * maintainer as the pair {@code (CLIENT_NUMBER, CLIENT_LOCN_CODE)} — the pair {@code CRS_CL_FK1}
 * constrains — so the client alone does not identify one. A client averages two locations and may
 * have up to 99; location {@code 00} is always its permanent address.
 *
 * <p><b>Four columns of twenty-seven.</b> The same restraint {@link ClientPublicEntity} shows: this
 * table carries phone numbers, an email address, a postal address and a free-text comment, none of
 * which a search filter needs. What is mapped is the key, plus the two columns that let a user tell
 * one of a client's locations from another —
 * {@code CLIENT_LOCN_NAME}, which the schema reserves for "corporate division names and joint
 * venture names", and {@code CITY}.
 *
 * <p><b>{@code LOCN_EXPIRED_IND} is deliberately not mapped, and not filtered on.</b> Legacy does
 * not filter it either — {@code CBR.GET_CLIENT_BASE_SELECT} has no {@code WHERE} clause of its own.
 * The reason it does not matter here is stronger than precedent, though: the lookup only ever
 * returns pairs that a site actually names (see {@link
 * ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository}). If a site records an expired
 * location, a user searching for that site still has to be able to find it.
 */
@Entity
@Immutable
@Table(name = "CLIENT_LOCATION", schema = "THE")
@IdClass(ClientLocationEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode(of = {"clientNumber", "clientLocnCode"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientLocationEntity {

  @Id
  @Column(name = "CLIENT_NUMBER", length = 8)
  private String clientNumber;

  /** {@code 00} is the client itself; {@code 01} upwards are its other divisions and addresses. */
  @Id
  @Column(name = "CLIENT_LOCN_CODE", length = 2)
  private String clientLocnCode;

  /** A division or joint-venture name. Frequently null — most clients have one location. */
  @Column(name = "CLIENT_LOCN_NAME", length = 40)
  private String clientLocnName;

  /** Never null, which is what makes it the reliable half of a suggestion's label. */
  @Column(name = "CITY", length = 30)
  private String city;

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    private String clientNumber;
    private String clientLocnCode;
  }
}
