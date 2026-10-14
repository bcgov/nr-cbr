package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.CROSSING_STRUCTURE_NAME_HIST} — a name a structure used to have, and when it changed.
 *
 * <p>The key is the old name and the change time ({@code CSNH_PK}); the table has no id column of
 * its own.
 *
 * <p>Written when the Information tab renames a structure, as legacy's {@code UPDATE_STRUCTURE}
 * wrote it: the old name, when, and who. Never changed once written ({@code @Immutable} still
 * inserts). A structure delete clears it, with native SQL in {@code StructureService.delete}.
 */
@Entity
@Immutable
@Table(name = "CROSSING_STRUCTURE_NAME_HIST", schema = "THE")
@IdClass(CrossingStructureNameHistEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingStructureNameHistEntity {

  @Id
  @Column(name = "OLD_CROSSING_STRUCTURE_NAME", length = 240)
  private String oldCrossingStructureName;

  @Id
  @Column(name = "CHANGE_TIMESTAMP")
  private LocalDateTime changeTimestamp;

  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  @Column(name = "CHANGE_USERID", length = 120)
  private String changeUserid;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String oldCrossingStructureName;
    private LocalDateTime changeTimestamp;
  }
}
