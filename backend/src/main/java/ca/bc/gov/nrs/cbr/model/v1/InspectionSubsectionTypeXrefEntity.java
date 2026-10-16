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
 * {@code THE.STRCTRE_TYP_INSPCTN_SBSCTN_XRF} — Which question groups a structure's Type/Class is inspected under.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_TYP_INSPCTN_SBSCTN_XRF", schema = "THE")
@IdClass(InspectionSubsectionTypeXrefEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionSubsectionTypeXrefEntity {

  @Id
  @Column(name = "STRUCTURE_INSPCTN_SUBSCTN_CODE", length = 10)
  private String subsectionCode;

  @Id
  @Column(name = "STRUCTURE_TYPE_CLASS_CODE", length = 10)
  private String typeClassCode;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String subsectionCode;
    private String typeClassCode;
  }
}
