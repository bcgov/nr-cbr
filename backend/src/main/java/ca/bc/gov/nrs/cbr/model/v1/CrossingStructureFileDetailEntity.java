package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
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
 * <p>Read by the structure page's Documents &amp; Photos tab, which also adds, edits and deletes
 * them, and by the deletes that clear a structure's or an inspection's attachments.
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
@Builder(toBuilder = true)
public class CrossingStructureFileDetailEntity {

  /**
   * From {@code THE.CROSSING_STRCTRE_FILE_DTL_SEQ}, as legacy's
   * {@code INSERT_FILE_ATTACHMENT_DETAIL} takes it; the file's bytes take the same id.
   * {@code allocationSize = 1} because the sequence increments by 1 and legacy draws from it too:
   * Hibernate's default of 50 would hand out ids it assumes are reserved and collide with legacy's.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "structureFileDetailSeq")
  @SequenceGenerator(name = "structureFileDetailSeq",
      sequenceName = "THE.CROSSING_STRCTRE_FILE_DTL_SEQ", allocationSize = 1)
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

  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
