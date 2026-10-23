package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.DocumentNotFoundException;
import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.model.v1.FileAttachmentTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.FileMimeTypeXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.service.v1.StructureDocumentsService.DocumentFile;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse.Document;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** The Documents &amp; Photos tab's data and files, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureDocumentsService.class)
class StructureDocumentsServiceTest {

  @Autowired
  private StructureDocumentsService service;

  @Autowired
  private CrossingStructureFileRepository files;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  /** Passes every file unless a case says otherwise: Mockito's empty Optional. */
  @MockitoBean
  private VirusScanner virusScanner;

  /** A bucket in memory: what is put can be read back; anything else is missing. */
  @MockitoBean
  private ObjectStorageService storage;

  private final Map<String, byte[]> bucket = new HashMap<>();
  private final Map<String, String> bucketTypes = new HashMap<>();

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    doAnswer(put -> {
      bucket.put(put.getArgument(0), put.getArgument(2));
      bucketTypes.put(put.getArgument(0), put.getArgument(1));
      return null;
    }).when(storage).putObject(anyString(), anyString(), any());
    when(storage.getObjectBytesIfPresent(anyString()))
        .thenAnswer(get -> bucket.get(get.<String>getArgument(0)));
    for (String entity : List.of("CrossingStructureFileEntity", "CrossingStructureFileDetailEntity",
        "FileAttachmentTypeCodeEntity", "FileMimeTypeXrefEntity", "StructureInspectionEntity",
        "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    givenStructure(7L);
    givenStructure(8L);
    entityManager.persist(FileAttachmentTypeCodeEntity.builder()
        .fileAttachmentTypeCode("PHOTO").description("Photograph").build());
    entityManager.persist(FileAttachmentTypeCodeEntity.builder()
        .fileAttachmentTypeCode("OTH").description("Other").build());
    for (String extension : List.of("JPG", "PDF")) {
      entityManager.persist(new FileMimeTypeXrefEntity(
          new FileMimeTypeXrefEntity.Key(extension, extension)));
    }
  }

  private void givenStructure(long id) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName("B" + id).structureTypeClassCode("TB")
        .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
  }

  private void givenInspection(long id, long structureId, LocalDate date) {
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(id).crossingStructureId(structureId).inspectionDate(date).build());
  }

  /** A document and its bytes; its id, from the sequence, is returned. */
  private long givenDocument(long structureId, Long inspectionId, String filename,
      LocalDateTime created) {
    CrossingStructureFileDetailEntity detail = CrossingStructureFileDetailEntity.builder()
        .crossingStructureId(structureId).inspectionId(inspectionId)
        .filename(filename).fileCreateDate(created).fileMimeTypeCode("M")
        .efileExtensionCode(filename.substring(filename.lastIndexOf('.') + 1).toUpperCase())
        .fileAttachmentTypeCode("PHOTO").description("Deck from the north abutment").build();
    entityManager.persist(detail);
    entityManager.persist(CrossingStructureFileEntity.builder()
        .fileId(detail.getFileId()).structureFile(("bytes of " + filename).getBytes()).build());
    return detail.getFileId();
  }

  private List<Document> documents(long structureId) {
    entityManager.flush();
    entityManager.clear();
    return service.findByStructure(structureId).documents();
  }

  @Test
  @DisplayName("lists the structure's own files first, then each inspection's, newest inspection "
      + "first and newest file first within it, as FIND_FILE_DTLS_BY_STRCTRE orders them")
  void legacyOrder() {
    givenInspection(10L, 7L, LocalDate.of(2020, 6, 1));
    givenInspection(11L, 7L, LocalDate.of(2023, 6, 1));
    givenDocument(7L, 10L, "old.jpg", LocalDateTime.of(2020, 6, 1, 9, 0));
    givenDocument(7L, 11L, "a.jpg", LocalDateTime.of(2023, 6, 1, 9, 0));
    givenDocument(7L, 11L, "b.jpg", LocalDateTime.of(2023, 6, 2, 9, 0));
    givenDocument(7L, null, "plan.pdf", LocalDateTime.of(2010, 1, 1, 0, 0));

    assertThat(documents(7L)).extracting(Document::filename)
        .containsExactly("plan.pdf", "b.jpg", "a.jpg", "old.jpg");
  }

  @Test
  @DisplayName("carries the details the tab shows, the attachment type decoded")
  void details() {
    givenInspection(11L, 7L, LocalDate.of(2023, 6, 1));
    givenDocument(7L, 11L, "deck.jpg", LocalDateTime.of(2023, 6, 2, 9, 30));

    Document document = documents(7L).getFirst();

    assertThat(document.inspectionId()).isEqualTo("11");
    assertThat(document.inspectionDate()).isEqualTo(LocalDate.of(2023, 6, 1));
    assertThat(document.attachmentType()).isEqualTo(new CodeValue("PHOTO", "Photograph"));
    assertThat(document.created()).isEqualTo(LocalDate.of(2023, 6, 2));
    assertThat(document.extension()).isEqualTo("JPG");
    assertThat(document.description()).isEqualTo("Deck from the north abutment");
    assertThat(document.filename()).isEqualTo("deck.jpg");
  }

  @Test
  @DisplayName("lists only this structure's files")
  void onlyThisStructure() {
    givenDocument(8L, null, "other.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));

    assertThat(documents(7L)).isEmpty();
  }

  @Test
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.findByStructure(404L))
        .isInstanceOf(StructureNotFoundException.class);
  }

  @Test
  @DisplayName("sends a photo or a PDF to be shown, under its own type")
  void showsImagesAndPdfs() {
    long deck = givenDocument(7L, null, "deck.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    long drawing = givenDocument(7L, null, "plan.pdf", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(7L);

    DocumentFile photo = service.file(7L, deck);
    DocumentFile plan = service.file(7L, drawing);

    assertThat(photo.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
    assertThat(photo.inline()).isTrue();
    assertThat(photo.filename()).isEqualTo("deck.jpg");
    assertThat(new String(photo.content())).isEqualTo("bytes of deck.jpg");
    assertThat(plan.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(plan.inline()).isTrue();
  }

  @Test
  @DisplayName("sends anything else as a plain download, so no uploaded page can run as this site")
  void downloadsEverythingElse() {
    List<Long> ids = List.of(
        givenDocument(7L, null, "report.docx", LocalDateTime.of(2020, 1, 1, 0, 0)),
        givenDocument(7L, null, "drawing.svg", LocalDateTime.of(2020, 1, 1, 0, 0)),
        givenDocument(7L, null, "notes.html", LocalDateTime.of(2020, 1, 1, 0, 0)));
    documents(7L);

    for (long id : ids) {
      DocumentFile file = service.file(7L, id);
      assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
      assertThat(file.inline()).isFalse();
    }
  }

  @Test
  @DisplayName("refuses a file asked for under a structure it does not belong to")
  void fileOfAnotherStructure() {
    long id = givenDocument(8L, null, "other.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(8L);

    assertThatThrownBy(() -> service.file(7L, id)).isInstanceOf(DocumentNotFoundException.class);
    assertThatThrownBy(() -> service.file(7L, id + 1000))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  @DisplayName("compares files by their contents, and prints their size rather than their bytes")
  void documentFileByContents() {
    DocumentFile one = new DocumentFile("a.jpg", MediaType.IMAGE_JPEG, true, new byte[] {1, 2});
    DocumentFile same = new DocumentFile("a.jpg", MediaType.IMAGE_JPEG, true, new byte[] {1, 2});
    DocumentFile other = new DocumentFile("a.jpg", MediaType.IMAGE_JPEG, true, new byte[] {9});

    assertThat(one).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
    assertThat(one.toString()).contains("content=2 bytes");
  }

  @Test
  @DisplayName("deletes files by id in one statement, without loading them")
  void bulkDelete() {
    long first = givenDocument(7L, null, "a.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    long second = givenDocument(7L, null, "b.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(7L);

    files.deleteByFileIdIn(List.of(first));

    assertThat(files.findAll()).extracting(CrossingStructureFileEntity::getFileId)
        .containsExactly(second);
  }

  private static MultipartFile part(String name, int bytes) {
    return new MockMultipartFile("file", name, "application/octet-stream", new byte[bytes]);
  }

  private static DocumentDetailsRequest row(String type, String description) {
    return new DocumentDetailsRequest(type, LocalDate.of(2026, 9, 1), description);
  }

  private CrossingStructureFileDetailEntity storedDetail(long id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(CrossingStructureFileDetailEntity.class, id);
  }

  private Map<String, String> uploadRefused(MultipartFile part, DocumentDetailsRequest row) {
    try {
      service.upload(7L, null, part, row);
    } catch (FieldValidationException refused) {
      return refused.getFieldErrors();
    }
    throw new AssertionError("the upload was stored");
  }

  @Test
  @DisplayName("adds the file as the structure's own, with its details, bytes and who added it")
  void uploads() {
    MultipartFile photo = new MockMultipartFile("file", " deck.jpg ", "image/jpeg",
        "jpeg bytes".getBytes());
    String id = service.upload(7L, null, photo, row(" PHOTO ", "  North abutment.  "));

    CrossingStructureFileDetailEntity detail = storedDetail(Long.parseLong(id));
    assertThat(detail.getCrossingStructureId()).isEqualTo(7L);
    assertThat(detail.getInspectionId()).isNull();
    assertThat(detail.getFilename()).isEqualTo("deck.jpg");
    assertThat(detail.getEfileExtensionCode()).isEqualTo("JPG");
    assertThat(detail.getFileMimeTypeCode()).isEqualTo("JPG");
    assertThat(detail.getFileAttachmentTypeCode()).isEqualTo("PHOTO");
    assertThat(detail.getFileCreateDate()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
    assertThat(detail.getDescription()).isEqualTo("North abutment.");
    assertThat(detail.getEntryUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(detail.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(new String(service.file(7L, Long.parseLong(id)).content()))
        .isEqualTo("jpeg bytes");
  }

  @Test
  @DisplayName("gives each file its own id, its extension read from its name")
  void uploadsEachFile() {
    String photo = service.upload(7L, null, part("deck.jpg", 10), row("PHOTO", null));
    String plan = service.upload(7L, null, part("plan.PDF", 10), row("OTH", "As-built plan"));

    assertThat(plan).isNotEqualTo(photo);
    assertThat(storedDetail(Long.parseLong(plan)).getEfileExtensionCode()).isEqualTo("PDF");
    assertThat(documents(7L)).hasSize(2);
  }

  @Test
  @DisplayName("refuses a file of an unlisted type, over 10 MB, or without an extension")
  void refusesFiles() {
    assertThat(uploadRefused(part("notes.exe", 10), row("PHOTO", null)))
        .containsExactly(Map.entry("file", "Files of type .exe cannot be added."));
    assertThat(uploadRefused(
        part("big.jpg", (int) StructureDocumentsService.MAX_FILE_BYTES + 1), row("PHOTO", null)))
        .containsExactly(Map.entry("file", "big.jpg is larger than 10 MB."));
    assertThat(uploadRefused(part("noext", 10), row("PHOTO", null)))
        .containsExactly(Map.entry("file", "noext has no file type."));
    assertThat(uploadRefused(part("empty.jpg", 0), row("PHOTO", null)))
        .containsExactly(Map.entry("file", "empty.jpg is empty."));
    assertThat(documents(7L)).isEmpty();
  }

  @Test
  @DisplayName("needs a known Photo View, a created date, and a description for Other")
  void refusesDetails() {
    assertThat(uploadRefused(part("a.jpg", 10), row(" ", null)))
        .containsExactly(Map.entry("attachmentTypeCode", "Photo View is required."));
    assertThat(uploadRefused(part("b.jpg", 10), row("NOPE", null)))
        .containsExactly(
            Map.entry("attachmentTypeCode", "Photo View is not one of the listed types."));
    assertThat(uploadRefused(part("c.jpg", 10), row("OTH", " ")))
        .containsExactly(Map.entry("description", "Description is required for this Photo View."));
    assertThat(uploadRefused(part("d.jpg", 10),
        new DocumentDetailsRequest("PHOTO", null, "é".repeat(1001))))
        .containsExactly(
            Map.entry("createdDate", "Photo/Doc Created is required."),
            Map.entry("description", "Description can be at most 2000 characters."));
    assertThat(documents(7L)).isEmpty();
  }

  @Test
  @DisplayName("scans the bytes it stores, and stores nothing the scanner refuses, beside the file")
  void scans() {
    byte[] bytes = "jpeg bytes".getBytes();
    MultipartFile photo = new MockMultipartFile("file", "deck.jpg", "image/jpeg", bytes);
    String id = service.upload(7L, null, photo, row("PHOTO", null));
    verify(virusScanner).refusal(bytes);
    assertThat(service.file(7L, Long.parseLong(id)).content()).isEqualTo(bytes);

    when(virusScanner.refusal(any()))
        .thenReturn(Optional.of("This file was rejected: a virus was detected (Eicar-Test)."));
    assertThat(uploadRefused(part("eicar.jpg", 68), row("PHOTO", null))).containsExactly(
        Map.entry("file", "This file was rejected: a virus was detected (Eicar-Test)."));
    assertThat(documents(7L)).hasSize(1);
  }

  @Test
  @DisplayName("does not scan a file whose details are refused")
  void scansOnlyWhatPasses() {
    uploadRefused(part("notes.exe", 10), row("PHOTO", null));
    uploadRefused(part("a.jpg", 10), row(" ", null));

    verifyNoInteractions(virusScanner);
  }

  @Test
  @DisplayName("puts the file in the bucket at its id's key, under its type, with an empty BLOB")
  void storesInTheBucket() {
    byte[] bytes = "%PDF-1.7".getBytes();
    long id = Long.parseLong(service.upload(7L, null,
        new MockMultipartFile("file", "plan.pdf", "application/pdf", bytes), row("OTH", "Plan")));

    assertThat(bucket).containsOnlyKeys("docs/" + id);
    assertThat(bucket.get("docs/" + id)).isEqualTo(bytes);
    assertThat(bucketTypes.get("docs/" + id)).isEqualTo("application/pdf");
    verify(storage).deleteOnRollback("docs/" + id);
    entityManager.flush();
    entityManager.clear();
    assertThat(files.findById(id)).get()
        .extracting(CrossingStructureFileEntity::getStructureFile).isEqualTo(new byte[0]);
  }

  @Test
  @DisplayName("refuses the file beside it when the bucket cannot take it")
  void bucketRefuses() {
    doThrow(S3Exception.builder().message("Service Unavailable").statusCode(503).build())
        .when(storage).putObject(anyString(), anyString(), any());

    assertThat(uploadRefused(part("deck.jpg", 10), row("PHOTO", null)))
        .containsExactly(Map.entry("file", StructureDocumentsService.NOT_STORED));
    verify(storage, never()).deleteOnRollback(anyString());
  }

  @Test
  @DisplayName("reads a file from the bucket before its BLOB, and the BLOB only when there is none")
  void readsTheBucketFirst() {
    long migrated = givenDocument(7L, null, "deck.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    long notYet = givenDocument(7L, null, "pier.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    bucket.put("docs/" + migrated, "bytes from the bucket".getBytes());
    documents(7L);

    assertThat(new String(service.file(7L, migrated).content()))
        .isEqualTo("bytes from the bucket");
    assertThat(new String(service.file(7L, notYet).content())).isEqualTo("bytes of pier.jpg");
  }

  @Test
  @DisplayName("finds no file when neither the bucket nor the BLOB has it")
  void neitherHasIt() {
    long id = givenDocument(7L, null, "deck.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    entityManager.flush();
    files.deleteByFileIdIn(List.of(id));
    files.insertEmpty(id);

    assertThatThrownBy(() -> service.file(7L, id)).isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  @DisplayName("lets the bucket failing through, rather than read it as a file not yet migrated")
  void bucketFails() {
    long id = givenDocument(7L, null, "deck.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(7L);
    when(storage.getObjectBytesIfPresent("docs/" + id))
        .thenThrow(S3Exception.builder().message("Access Denied").statusCode(403).build());

    assertThatThrownBy(() -> service.file(7L, id)).isInstanceOf(S3Exception.class);
  }

  @Test
  @DisplayName("adds nothing to a structure that does not exist")
  void uploadMissingStructure() {
    MultipartFile part = part("a.jpg", 1);
    DocumentDetailsRequest row = row("PHOTO", null);

    assertThatThrownBy(() -> service.upload(404L, null, part, row))
        .isInstanceOf(StructureNotFoundException.class);
  }

  @Test
  @DisplayName("edits the type and description, keeping the file, and records who did it — an "
      + "inspection's document too, as legacy")
  void updates() {
    givenInspection(11L, 7L, LocalDate.of(2023, 6, 1));
    long id = givenDocument(7L, 11L, "deck.jpg", LocalDateTime.of(2023, 6, 2, 9, 30));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, id, new DocumentUpdateRequest("OTH", " Deck, from the south. "));

    CrossingStructureFileDetailEntity detail = storedDetail(id);
    assertThat(detail.getFileAttachmentTypeCode()).isEqualTo("OTH");
    assertThat(detail.getDescription()).isEqualTo("Deck, from the south.");
    assertThat(detail.getFilename()).isEqualTo("deck.jpg");
    assertThat(detail.getFileCreateDate()).isEqualTo(LocalDateTime.of(2023, 6, 2, 9, 30));
    assertThat(detail.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("refuses an edit with a field at fault, or of another structure's document")
  void updateRefused() {
    long id = givenDocument(7L, null, "deck.jpg", LocalDateTime.of(2023, 6, 2, 9, 30));
    entityManager.flush();
    entityManager.clear();
    DocumentUpdateRequest other = new DocumentUpdateRequest("OTH", null);
    DocumentUpdateRequest fine = new DocumentUpdateRequest("PHOTO", null);

    assertThatThrownBy(() -> service.update(7L, id, other))
        .isInstanceOfSatisfying(FieldValidationException.class, refused ->
            assertThat(refused.getFieldErrors()).containsOnlyKeys("description"));
    assertThatThrownBy(() -> service.update(8L, id, fine))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  @DisplayName("deletes a document and its file, only through its own structure")
  void deletes() {
    long gone = givenDocument(7L, null, "a.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    long kept = givenDocument(7L, null, "b.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.delete(8L, gone))
        .isInstanceOf(DocumentNotFoundException.class);
    service.delete(7L, gone);

    assertThat(documents(7L)).extracting(Document::id).containsExactly(String.valueOf(kept));
    assertThat(files.findAll()).extracting(CrossingStructureFileEntity::getFileId)
        .containsExactly(kept);
    verify(storage).deleteAfterCommit(List.of("docs/" + gone));
    verify(storage, never()).deleteAfterCommit(List.of("docs/" + kept));
  }
}
