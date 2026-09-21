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
 * {@code THE.STRUCTURE_REPAIR} — A repair raised by an inspection.
 *
 * <p><b>Only the two columns an inspection delete needs.</b> The table is wide and the rest of it
 * arrives with the screens that read it; mapping columns nothing reads would be mapping them on the
 * chance something might.
 */
@Entity
@Table(name = "STRUCTURE_REPAIR", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "repairId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureRepairEntity {

  @Id
  @Column(name = "REPAIR_ID")
  private Long repairId;

  @Column(name = "INSPECTION_ID")
  private Long inspectionId;
}
