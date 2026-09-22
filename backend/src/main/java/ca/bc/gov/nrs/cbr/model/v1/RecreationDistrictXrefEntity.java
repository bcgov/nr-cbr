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
 * {@code THE.RECREATION_DISTRICT_XREF} — which recreation districts a project file belongs to.
 *
 * <p>The one thing that narrows the district list on a recreation site: where a crossing takes its
 * district from the road, a recreation site takes its <em>choices</em> from the project file and
 * the user picks among them. Legacy reads the same table through
 * {@code CBR_GENERAL.FIND_RECREATION_DISTRICTS}.
 *
 * <p>Joined on the district's <b>code</b> rather than its number — that is how the cross-reference
 * is keyed, and how the legacy procedure joins it.
 */
@Entity
@Immutable
@Table(name = "RECREATION_DISTRICT_XREF", schema = "THE")
@IdClass(RecreationDistrictXrefEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode(of = {"forestFileId", "recreationDistrictCode"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecreationDistrictXrefEntity {

  @Id
  @Column(name = "FOREST_FILE_ID", length = 10)
  private String forestFileId;

  @Id
  @Column(name = "RECREATION_DISTRICT_CODE", length = 4)
  private String recreationDistrictCode;

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    private String forestFileId;
    private String recreationDistrictCode;
  }
}
