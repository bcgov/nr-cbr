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
 * {@code THE.CROSSING_STRUCTURE_FILE_DETAIL} — an attachment's metadata.
 *
 * <p>Only the two columns the delete needs. The rest — filename, MIME type, description, the
 * attachment type code — arrives with the attachment screens.
 *
 * <p>{@link #inspectionId} is nullable: an attachment can hang off a structure rather than an
 * inspection, and those are not the ones an inspection delete removes.
 */
@Entity
@Table(name = "CROSSING_STRUCTURE_FILE_DETAIL", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "fileId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingStructureFileDetailEntity {

  @Id
  @Column(name = "FILE_ID")
  private Long fileId;

  @Column(name = "INSPECTION_ID")
  private Long inspectionId;
}
