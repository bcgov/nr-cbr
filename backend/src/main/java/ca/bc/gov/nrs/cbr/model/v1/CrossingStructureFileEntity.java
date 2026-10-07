package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.CROSSING_STRUCTURE_FILE} — the bytes of an attachment.
 *
 * <p>Split from {@link CrossingStructureFileDetailEntity} so the BLOB is not dragged into metadata
 * queries (cbr-data-model.local.md §5). Loaded only to open one file from the Documents &amp;
 * Photos tab. The delete is a bulk statement — see {@code CrossingStructureFileRepository} — so
 * clearing attachments never loads their bytes.
 *
 * <p>The foreign key runs <em>from</em> here <em>to</em> the detail row, which is why a delete has
 * to take this one first.
 */
@Entity
@Table(name = "CROSSING_STRUCTURE_FILE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "fileId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingStructureFileEntity {

  @Id
  @Column(name = "FILE_ID")
  private Long fileId;

  /** The file itself. */
  @Lob
  @Column(name = "STRUCTURE_FILE")
  @ToString.Exclude
  private byte[] structureFile;
}
