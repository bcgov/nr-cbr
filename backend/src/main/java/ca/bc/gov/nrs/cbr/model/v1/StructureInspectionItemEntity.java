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

/**
 * {@code THE.STRUCTURE_INSPECTION_ITEM} — One answered question of a filled-in inspection form — the inspection form engine's leaf row.
 *
 * <p><b>Only the two columns an inspection delete needs.</b> The table is wide and the rest of it
 * arrives with the screens that read it; mapping columns nothing reads would be mapping them on the
 * chance something might.
 */
@Entity
@Table(name = "STRUCTURE_INSPECTION_ITEM", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureInspectionItemId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureInspectionItemEntity {

  @Id
  @Column(name = "STRUCTURE_INSPECTION_ITEM_ID")
  private Long structureInspectionItemId;

  @Column(name = "INSPECTION_ID")
  private Long inspectionId;
}
