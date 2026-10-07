package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.CROSSING_STRUCTURE_FILE_DETAIL} — an attachment's metadata.
 *
 * <p>Read by the structure page's Documents &amp; Photos tab, and by the deletes that clear a
 * structure's or an inspection's attachments. The audit columns are not mapped; nothing reads them
 * yet.
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

  /**
   * The structure this belongs to. Mapped for one reason: a structure with any of these cannot be
   * deleted — see {@code StructureService.delete}.
   */
  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  /** The name the file was uploaded under, extension included. */
  @Column(name = "FILENAME", length = 255)
  private String filename;

  /** When the photo was taken or the document made — not when it was uploaded. */
  @Column(name = "FILE_CREATE_DATE")
  private LocalDateTime fileCreateDate;

  @Column(name = "FILE_MIME_TYPE_CODE", length = 10)
  private String fileMimeTypeCode;

  /** {@code EFILE_EXTENSION_CODE}, e.g. {@code JPG}. */
  @Column(name = "EFILE_EXTENSION_CODE", length = 10)
  private String efileExtensionCode;

  /** {@code FILE_ATTACHMENT_TYPE_CODE} — what the file shows or is. */
  @Column(name = "FILE_ATTACHMENT_TYPE_CODE", length = 10)
  private String fileAttachmentTypeCode;

  @Column(name = "DESCRIPTION", length = 2000)
  private String description;
}
