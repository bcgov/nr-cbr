package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.DocumentNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.model.v1.FileAttachmentTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileDetailRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.FileAttachmentTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A structure's documents and photos — the structure page's Documents &amp; Photos tab, legacy's
 * {@code documentTab.jsp} fed by {@code FIND_FILE_DTLS_BY_STRCTRE} — and the file behind each.
 *
 * <p>Read-only for now; legacy's Add (Level 2) and Delete come with the page's editing. The bytes
 * are read from {@code CROSSING_STRUCTURE_FILE}; once they move to object storage only
 * {@link #file} changes.
 */
@Service
public class StructureDocumentsService {

  /**
   * What may open in the browser. Anything else is sent as a plain download: the page opens files
   * from its own origin, where an uploaded HTML or SVG file could run script.
   */
  private static final Set<MediaType> SHOWN_INLINE = Set.of(
      MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.APPLICATION_PDF);

  private final CrossingStructureRepository structures;
  private final CrossingStructureFileDetailRepository details;
  private final CrossingStructureFileRepository files;
  private final FileAttachmentTypeCodeRepository attachmentTypes;
  private final StructureInspectionRepository inspections;

  public StructureDocumentsService(
      CrossingStructureRepository structures,
      CrossingStructureFileDetailRepository details,
      CrossingStructureFileRepository files,
      FileAttachmentTypeCodeRepository attachmentTypes,
      StructureInspectionRepository inspections) {
    this.structures = structures;
    this.details = details;
    this.files = files;
    this.attachmentTypes = attachmentTypes;
    this.inspections = inspections;
  }

  /**
   * One file, ready to send: its name, what it is, and whether the browser may show it.
   *
   * <p>Equality and {@code toString} by the file's contents rather than the array's identity, as a
   * record would otherwise have them; {@code toString} gives the size, not the bytes.
   */
  public record DocumentFile(
      String filename, MediaType mediaType, boolean inline, byte[] content) {

    @Override
    public boolean equals(Object other) {
      return other instanceof DocumentFile file
          && inline == file.inline
          && Objects.equals(filename, file.filename)
          && Objects.equals(mediaType, file.mediaType)
          && Arrays.equals(content, file.content);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hash(filename, mediaType, inline) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
      return "DocumentFile[filename=" + filename + ", mediaType=" + mediaType + ", inline="
          + inline + ", content=" + (content == null ? "null" : content.length + " bytes") + "]";
    }
  }

  /**
   * The structure's documents in legacy's order — {@code INSPECTION_ID DESC} with Oracle's nulls
   * first, so the structure's own come first; then inspection date, then the newest file, then id.
   *
   * <p>Legacy inner-joins the attachment type, so a file whose code has no row would vanish there.
   * Here it stays, its code shown without a description.
   *
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public StructureDocumentsResponse findByStructure(long structureId) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    List<CrossingStructureFileDetailEntity> rows = details.findByCrossingStructureId(structureId);

    Map<Long, LocalDate> inspectionDates = inspections
        .findAllById(rows.stream()
            .map(CrossingStructureFileDetailEntity::getInspectionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList())
        .stream()
        .filter(inspection -> inspection.getInspectionDate() != null)
        .collect(Collectors.toMap(
            StructureInspectionEntity::getInspectionId,
            StructureInspectionEntity::getInspectionDate));
    Map<String, String> typeDescriptions = attachmentTypes
        .findAllById(rows.stream()
            .map(CrossingStructureFileDetailEntity::getFileAttachmentTypeCode)
            .filter(Objects::nonNull)
            .distinct()
            .toList())
        .stream()
        .collect(Collectors.toMap(
            FileAttachmentTypeCodeEntity::getFileAttachmentTypeCode,
            FileAttachmentTypeCodeEntity::getDescription));

    Comparator<CrossingStructureFileDetailEntity> legacyOrder = Comparator
        .comparing(CrossingStructureFileDetailEntity::getInspectionId,
            Comparator.nullsFirst(Comparator.<Long>reverseOrder()))
        .thenComparing(row -> inspectionDates.get(row.getInspectionId()),
            Comparator.nullsFirst(Comparator.<LocalDate>reverseOrder()))
        .thenComparing(CrossingStructureFileDetailEntity::getFileCreateDate,
            Comparator.nullsFirst(Comparator.<LocalDateTime>reverseOrder()))
        .thenComparing(CrossingStructureFileDetailEntity::getFileId);

    return new StructureDocumentsResponse(rows.stream()
        .sorted(legacyOrder)
        .map(row -> document(row, inspectionDates, typeDescriptions))
        .toList());
  }

  private static Document document(CrossingStructureFileDetailEntity row,
      Map<Long, LocalDate> inspectionDates, Map<String, String> typeDescriptions) {
    String type = row.getFileAttachmentTypeCode();
    return new Document(
        String.valueOf(row.getFileId()),
        row.getInspectionId() == null ? null : String.valueOf(row.getInspectionId()),
        inspectionDates.get(row.getInspectionId()),
        type == null ? CodeValue.NONE : new CodeValue(type, typeDescriptions.get(type)),
        row.getFileCreateDate() == null ? null : row.getFileCreateDate().toLocalDate(),
        row.getEfileExtensionCode(),
        row.getDescription(),
        row.getFilename());
  }

  /**
   * One file's bytes, found only through the structure it belongs to.
   *
   * <p>The type is read from the file's name, then its extension code; legacy sent its MIME type
   * code, a ten-character code rather than a media type.
   *
   * @throws DocumentNotFoundException if the structure has no such document, or its bytes are gone
   */
  @Transactional(readOnly = true)
  public DocumentFile file(long structureId, long fileId) {
    CrossingStructureFileDetailEntity detail = details.findById(fileId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId))
        .orElseThrow(() -> new DocumentNotFoundException(structureId, fileId));
    byte[] content = files.findById(fileId)
        .map(CrossingStructureFileEntity::getStructureFile)
        .orElseThrow(() -> new DocumentNotFoundException(structureId, fileId));

    MediaType mediaType = mediaType(detail);
    boolean inline = SHOWN_INLINE.contains(mediaType);
    return new DocumentFile(
        detail.getFilename() == null ? "document-" + fileId : detail.getFilename(),
        inline ? mediaType : MediaType.APPLICATION_OCTET_STREAM,
        inline,
        content);
  }

  private static MediaType mediaType(CrossingStructureFileDetailEntity detail) {
    return MediaTypeFactory.getMediaType(detail.getFilename())
        .or(() -> detail.getEfileExtensionCode() == null
            ? Optional.<MediaType>empty()
            : MediaTypeFactory.getMediaType("file." + detail.getEfileExtensionCode()))
        .map(type -> new MediaType(type.getType(), type.getSubtype()))
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
  }
}
