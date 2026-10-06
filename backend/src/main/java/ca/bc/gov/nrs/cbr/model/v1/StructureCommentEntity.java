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
 * {@code THE.STRUCTURE_COMMENT} — a comment on a structure.
 *
 * <p><b>Minimal and read-only:</b> the key and the column that ties it to its parent, nothing else.
 * Nothing reads this table yet; a structure delete clears it, with native SQL in
 * {@code StructureService.delete}. Mapped so the test schema has the table without hand-written
 * DDL. The structure screens will map the rest when they arrive.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_COMMENT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureCommentId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureCommentEntity {

  @Id
  @Column(name = "STRUCTURE_COMMENT_ID")
  private Long structureCommentId;

  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;
}
