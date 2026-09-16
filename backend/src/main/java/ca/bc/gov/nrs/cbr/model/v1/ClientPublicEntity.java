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
 * {@code THE.V_CLIENT_PUBLIC} — a Forest Client, seen through the public view.
 *
 * <p><b>The view, not {@code FOREST_CLIENT}</b>, which the legacy site search joins. Two reasons,
 * and they agree:
 *
 * <ul>
 *   <li>{@code FSA_CBR_READ_WRITE_ROLE} is granted {@code V_CLIENT_PUBLIC} and
 *       <em>not</em> {@code FOREST_CLIENT}, so the legacy join is not available to this
 *       application at all.</li>
 *   <li>The view exists to keep personal data out of queries that do not need it. Legacy selects
 *       {@code LEGAL_FIRST_NAME} and {@code LEGAL_MIDDLE_NAME} from the table; the one column the
 *       search needs is {@code CLIENT_NAME}, which the view carries. nr-frep made the same
 *       substitution deliberately.</li>
 * </ul>
 *
 * <p><b>It hides columns, not clients.</b> The definition is {@code SELECT six columns FROM
 * forest_client} with no {@code WHERE} — every row, and its own comment says so: "A view to provide
 * a subset of columns from FOREST_CLIENT". What it withholds is the identifying detail on the
 * individuals that table also holds: birthdate, client identification, registry numbers. Of the six
 * it does expose, only the two used here are mapped.
 */
@Entity
@Immutable
@Table(name = "V_CLIENT_PUBLIC", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "clientNumber")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientPublicEntity {

  @Id
  @Column(name = "CLIENT_NUMBER", length = 8)
  private String clientNumber;

  /** "Designated Maintainer" on the search form — the criterion is a partial match on this. */
  @Column(name = "CLIENT_NAME")
  private String clientName;
}
