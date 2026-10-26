package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.AuditRecordLockedException;
import ca.bc.gov.nrs.cbr.exception.DocumentNotFoundException;
import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.model.v1.FileAttachmentTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileDetailRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.FileAttachmentTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.FileMimeTypeXrefRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse.Document;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * A structure's documents and photos — the structure page's Documents &amp; Photos tab, legacy's
 * {@code documentTab.jsp} fed by {@code FIND_FILE_DTLS_BY_STRCTRE} — and the file behind each.
 *
 * <p>Lists, opens, adds, edits and deletes them. Oracle holds what each document is and whose; its
 * file is in the object store at {@link ObjectStorageService#fileKey}. Files added before the move
 * are still in {@code CROSSING_STRUCTURE_FILE}'s BLOB until they are migrated, and are read from
 * there when the bucket has no object for them.
 */
@Service
public class StructureDocumentsService {

  /**
   * What may open in the browser. Anything else is sent as a plain download: the page opens files
   * from its own origin, where an uploaded HTML or SVG file could run script.
   */
  private static final Set<MediaType> SHOWN_INLINE = Set.of(
      MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.APPLICATION_PDF);

  private static final Logger log = LoggerFactory.getLogger(StructureDocumentsService.class);

  /** Legacy's {@code max-file-storage-size} preference, 10 MB — here for every file, JPEGs too. */
  public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
  /** {@code FILENAME VARCHAR2(255 BYTE)}. */
  private static final int NAME_MAX = 255;
  /** {@code DESCRIPTION VARCHAR2(2000 BYTE)}. */
  private static final int TEXT_MAX = 2000;
  /** The type whose file must be described — legacy's {@code FileAttachment.OTHER_CODE}. */
  private static final String OTHER = "OTH";
  private static final String PDF = "PDF";
  private static final String TYPE = "attachmentTypeCode";
  private static final String DESCRIPTION = "description";
  static final String NOT_STORED = "The file could not be stored. Try again shortly.";
  /**
   * How an inspection's audit record's description starts — what marks it as one, it being of
   * type Other like any document. Reserved: no one else's description may start so.
   */
  public static final String AUDIT_RECORD = "Audit record";
  private static final String RESERVED =
      "Descriptions starting \"" + AUDIT_RECORD + "\" are kept for inspection audit records.";

  private final CrossingStructureRepository structures;
  private final CrossingStructureFileDetailRepository details;
  private final CrossingStructureFileRepository files;
  private final FileAttachmentTypeCodeRepository attachmentTypes;
  private final StructureInspectionRepository inspections;
  private final FileMimeTypeXrefRepository mimeTypes;
  private final LoggedUserHelper loggedUser;
  private final VirusScanner virusScanner;
  private final ObjectStorageService storage;
  private final EntityManager entityManager;

  public StructureDocumentsService(
      CrossingStructureRepository structures,
      CrossingStructureFileDetailRepository details,
      CrossingStructureFileRepository files,
      FileAttachmentTypeCodeRepository attachmentTypes,
      StructureInspectionRepository inspections,
      FileMimeTypeXrefRepository mimeTypes,
      LoggedUserHelper loggedUser,
      VirusScanner virusScanner,
      ObjectStorageService storage,
      EntityManager entityManager) {
    this.structures = structures;
    this.details = details;
    this.files = files;
    this.attachmentTypes = attachmentTypes;
    this.inspections = inspections;
    this.mimeTypes = mimeTypes;
    this.loggedUser = loggedUser;
    this.virusScanner = virusScanner;
    this.storage = storage;
    this.entityManager = entityManager;
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
      return other instanceof DocumentFile(
              String otherName, MediaType otherType, boolean otherInline, byte[] otherContent)
          && inline == otherInline
          && Objects.equals(filename, otherName)
          && Objects.equals(mediaType, otherType)
          && Arrays.equals(content, otherContent);
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
        row.getFilename(),
        isAuditRecord(row));
  }

  /** Whether it is an inspection's audit record: Other, described as one. */
  static boolean isAuditRecord(CrossingStructureFileDetailEntity row) {
    return OTHER.equals(row.getFileAttachmentTypeCode()) && reserved(row.getDescription());
  }

  private static boolean reserved(String description) {
    return description != null && description.strip().toLowerCase(Locale.ROOT)
        .startsWith(AUDIT_RECORD.toLowerCase(Locale.ROOT));
  }

  /**
   * One file's bytes, found only through the structure it belongs to: from the object store, or —
   * for a file added before the move and not yet migrated — from its BLOB.
   *
   * <p>Only a missing object falls back to the BLOB; the store failing otherwise is an error, not
   * an empty file.
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
    byte[] content = storage.getObjectBytesIfPresent(ObjectStorageService.fileKey(fileId))
        .or(() -> files.findById(fileId)
            .map(CrossingStructureFileEntity::getStructureFile)
            .filter(blob -> blob.length > 0))
        .orElseThrow(() -> {
          log.warn("Document {} of structure {} has no object and no BLOB", fileId, structureId);
          return new DocumentNotFoundException(structureId, fileId);
        });

    MediaType mediaType = mediaType(detail);
    boolean inline = SHOWN_INLINE.contains(mediaType);
    return new DocumentFile(
        detail.getFilename() == null ? "document-" + fileId : detail.getFilename(),
        inline ? mediaType : MediaType.APPLICATION_OCTET_STREAM,
        inline,
        content);
  }

  /**
   * Adds one file to the structure — legacy's Add on the Documents &amp; Photos tab
   * ({@code UploadAction}, {@code CBR.INSERT_FILE_ATTACHMENT_DETAIL} and
   * {@code SET_FILE_ATTACHMENT}): the structure's own from its page, no inspection, as legacy's tab
   * adds; from an inspection's page, the inspection's, as legacy's uploader is called with it.
   *
   * <p>The file comes with its details: a type ("Photo View"), the date it was made, and a
   * description, required for Other. At most 10 MB and of an extension in
   * {@code FILE_MIME_TYPE_XREF}, its name at most 255 bytes. JPEGs are stored as sent; legacy
   * re-encoded them to a fixed width.
   *
   * <p>Once its details pass, the file is scanned for viruses, and refused on the {@code file} field
   * when it is infected or the scanner cannot check it — before anything is stored. The bytes
   * scanned are the bytes stored.
   *
   * <p>The rows are written first — the details, and an empty BLOB for the file row legacy's schema
   * requires — then the file is put in the object store at its id's key, all in one transaction:
   * a put that fails undoes the rows, and rows that fail to commit delete the object.
   *
   * <p>One file a call: the page sends legacy's batch of up to fifteen one at a time, so a request
   * stays under the frontend WAF's body limit. As legacy, which committed file by file, a batch can
   * therefore be partly stored; the page keeps the files that were not.
   *
   * @param inspectionId the inspection it was added from, or null from the structure page
   * @return the new document's id
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   with a message for each field at fault, keyed {@code file},
   *     {@code attachmentTypeCode}, {@code createdDate} or {@code description}
   */
  @Transactional
  public String upload(long structureId, Long inspectionId, MultipartFile part,
      DocumentDetailsRequest request) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    String mimeCode = validateUpload(part, request);
    byte[] content = bytes(part);
    virusScanner.refusal(content).ifPresent(refusal -> {
      // Not the refusal's text: it carries clamd's reply about the uploaded bytes.
      log.warn("Refused a document for structure {} on its virus scan", structureId);
      throw new FieldValidationException("Document cannot be added", Map.of("file", refusal));
    });

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    String name = part.getOriginalFilename().trim();
    CrossingStructureFileDetailEntity detail = CrossingStructureFileDetailEntity.builder()
        .crossingStructureId(structureId)
        .inspectionId(inspectionId)
        .filename(name)
        .fileCreateDate(request.createdDate().atStartOfDay())
        .efileExtensionCode(extension(name))
        .fileMimeTypeCode(mimeCode)
        .fileAttachmentTypeCode(trimmed(request.attachmentTypeCode()))
        .description(trimmed(request.description()))
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build();
    try {
      store(detail, content);
    } catch (SdkException failed) {
      throw new FieldValidationException("Document cannot be added",
          Map.of("file", NOT_STORED));
    }
    // Ids only: the file name is the browser's, and could carry line breaks into the log.
    log.info("Added document {} to structure {}", detail.getFileId(), structureId);
    return String.valueOf(detail.getFileId());
  }

  /**
   * Stores an inspection's audit record ({@link InspectionAuditRecorder}): its report PDF as one of
   * its documents, of type Other, described as an audit record — so it is never edited or deleted.
   * Made by CBR, not uploaded, so not scanned for viruses.
   *
   * @param description starting {@link #AUDIT_RECORD}
   * @return the new document's id
   * @throws SdkException when the object store refuses it, the rows then rolled back
   */
  @Transactional
  public long addAuditRecord(long structureId, long inspectionId, String filename,
      String description, byte[] pdf) {
    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    CrossingStructureFileDetailEntity detail = CrossingStructureFileDetailEntity.builder()
        .crossingStructureId(structureId)
        .inspectionId(inspectionId)
        .filename(filename)
        .fileCreateDate(now)
        .efileExtensionCode(PDF)
        .fileMimeTypeCode(mimeTypes.findMimeTypeCodes(PDF).stream().findFirst().orElse(null))
        .fileAttachmentTypeCode(OTHER)
        .description(description)
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build();
    store(detail, pdf);
    return detail.getFileId();
  }

  /**
   * Writes the rows — the details, and an empty BLOB for the file row legacy's schema requires —
   * then puts the file in the object store at its id's key, in the caller's transaction: a put
   * that fails undoes the rows, and rows that fail to commit delete the object.
   */
  private void store(CrossingStructureFileDetailEntity detail, byte[] content) {
    entityManager.persist(detail);
    files.insertEmpty(detail.getFileId());
    String key = ObjectStorageService.fileKey(detail.getFileId());
    try {
      storage.putObject(key, mediaType(detail).toString(), content);
    } catch (SdkException failed) {
      log.error("Could not store document {} of structure {} at {}", detail.getFileId(),
          detail.getCrossingStructureId(), key, failed);
      throw failed;
    }
    storage.deleteOnRollback(key);
  }

  /**
   * Changes a document's type and description — legacy's edit in its viewer
   * ({@code CBR.UPDATE_FILE_ATTACHMENT_DETAIL}), with who changed it and when. The file, its name
   * and its date are kept. As legacy, a document an inspection brought is edited here too; an
   * audit record is not.
   *
   * @throws DocumentNotFoundException   if the structure has no such document
   * @throws AuditRecordLockedException  if it is an inspection's audit record
   * @throws FieldValidationException    with a message for each field at fault
   */
  @Transactional
  public void update(long structureId, long fileId, DocumentUpdateRequest request) {
    CrossingStructureFileDetailEntity detail = changeable(structureId, fileId);
    Map<String, String> errors = new LinkedHashMap<>();
    checkDetails(errors, request.attachmentTypeCode(), request.description());
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Document cannot be saved", errors);
    }
    details.save(detail.toBuilder()
        .fileAttachmentTypeCode(trimmed(request.attachmentTypeCode()))
        .description(trimmed(request.description()))
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated document {} of structure {}", fileId, structureId);
  }

  /**
   * Deletes a document and its file — legacy's delete icon ({@code CBR.DELETE_FILE_ATTACHMENT}):
   * the file row first, which the foreign key requires, then the details, then — once that commits
   * — the object. As legacy, a document an inspection brought is deleted here too; an audit
   * record is not.
   *
   * @throws DocumentNotFoundException   if the structure has no such document
   * @throws AuditRecordLockedException  if it is an inspection's audit record
   */
  @Transactional
  public void delete(long structureId, long fileId) {
    changeable(structureId, fileId);
    files.deleteByFileIdIn(List.of(fileId));
    details.deleteById(fileId);
    storage.deleteAfterCommit(List.of(ObjectStorageService.fileKey(fileId)));
    log.info("Deleted document {} of structure {}", fileId, structureId);
  }

  /** The structure's document, once it is known not to be an audit record. */
  private CrossingStructureFileDetailEntity changeable(long structureId, long fileId) {
    CrossingStructureFileDetailEntity detail = details.findById(fileId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId))
        .orElseThrow(() -> new DocumentNotFoundException(structureId, fileId));
    if (isAuditRecord(detail)) {
      throw new AuditRecordLockedException(fileId);
    }
    return detail;
  }

  /**
   * Checks the file and its details before anything is stored.
   *
   * @return the file's MIME type code
   */
  private String validateUpload(MultipartFile part, DocumentDetailsRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    String mimeCode = checkFile(errors, part);
    if (request.createdDate() == null) {
      errors.put("createdDate", "Photo/Doc Created is required.");
    }
    checkDetails(errors, request.attachmentTypeCode(), request.description());
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Document cannot be added", errors);
    }
    return mimeCode;
  }

  /**
   * One file's name, size and type.
   *
   * @return its MIME type code, or null when it is refused
   */
  private String checkFile(Map<String, String> errors, MultipartFile part) {
    String key = "file";
    String name = part.getOriginalFilename() == null ? "" : part.getOriginalFilename().trim();
    if (name.isEmpty()) {
      errors.put(key, "The file has no name.");
      return null;
    }
    if (name.getBytes(StandardCharsets.UTF_8).length > NAME_MAX) {
      errors.put(key, "The file name can be at most " + NAME_MAX + " characters.");
      return null;
    }
    if (part.isEmpty()) {
      errors.put(key, name + " is empty.");
      return null;
    }
    if (part.getSize() > MAX_FILE_BYTES) {
      errors.put(key, name + " is larger than 10 MB.");
      return null;
    }
    String extension = extension(name);
    List<String> codes = extension == null ? List.of() : mimeTypes.findMimeTypeCodes(extension);
    if (codes.isEmpty()) {
      errors.put(key, extension == null
          ? name + " has no file type."
          : "Files of type ." + extension.toLowerCase(Locale.ROOT) + " cannot be added.");
      return null;
    }
    return codes.getFirst();
  }

  /**
   * A type ("Photo View") that exists, and a description when it is Other — not one an audit
   * record's could be mistaken for.
   */
  private void checkDetails(Map<String, String> errors, String typeCode, String text) {
    String type = trimmed(typeCode);
    String description = trimmed(text);
    if (type == null) {
      errors.put(TYPE, "Photo View is required.");
    } else if (!attachmentTypes.existsById(type)) {
      errors.put(TYPE, "Photo View is not one of the listed types.");
    }
    if (description == null) {
      if (OTHER.equals(type)) {
        errors.put(DESCRIPTION, "Description is required for this Photo View.");
      }
    } else if (reserved(description)) {
      errors.put(DESCRIPTION, RESERVED);
    } else if (description.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX) {
      // Bytes, because the column is declared in bytes and an accented character costs two.
      errors.put(DESCRIPTION, "Description can be at most " + TEXT_MAX + " characters.");
    }
  }

  /** The text after the last dot, upper case, as legacy's {@code EFILE_EXTENSION_CODE}. */
  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 || dot == name.length() - 1
        ? null
        : name.substring(dot + 1).toUpperCase(Locale.ROOT);
  }

  private static byte[] bytes(MultipartFile part) {
    try {
      return part.getBytes();
    } catch (IOException unreadable) {
      throw new UncheckedIOException("Could not read " + part.getOriginalFilename(), unreadable);
    }
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
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
