package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.DocumentNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.model.v1.FileAttachmentTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import ca.bc.gov.nrs.cbr.service.v1.StructureDocumentsService.DocumentFile;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse.Document;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

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

  @BeforeEach
  void setUp() {
    for (String entity : List.of("CrossingStructureFileEntity", "CrossingStructureFileDetailEntity",
        "FileAttachmentTypeCodeEntity", "StructureInspectionEntity", "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    givenStructure(7L);
    givenStructure(8L);
    entityManager.persist(FileAttachmentTypeCodeEntity.builder()
        .fileAttachmentTypeCode("PHOTO").description("Photograph").build());
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

  private void givenDocument(long id, long structureId, Long inspectionId, String filename,
      LocalDateTime created) {
    entityManager.persist(CrossingStructureFileDetailEntity.builder()
        .fileId(id).crossingStructureId(structureId).inspectionId(inspectionId)
        .filename(filename).fileCreateDate(created).fileMimeTypeCode("M")
        .efileExtensionCode(filename.substring(filename.lastIndexOf('.') + 1).toUpperCase())
        .fileAttachmentTypeCode("PHOTO").description("Deck from the north abutment").build());
    entityManager.persist(CrossingStructureFileEntity.builder()
        .fileId(id).structureFile(("bytes of " + filename).getBytes()).build());
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
    givenDocument(1L, 7L, 10L, "old.jpg", LocalDateTime.of(2020, 6, 1, 9, 0));
    givenDocument(2L, 7L, 11L, "a.jpg", LocalDateTime.of(2023, 6, 1, 9, 0));
    givenDocument(3L, 7L, 11L, "b.jpg", LocalDateTime.of(2023, 6, 2, 9, 0));
    givenDocument(4L, 7L, null, "plan.pdf", LocalDateTime.of(2010, 1, 1, 0, 0));

    assertThat(documents(7L)).extracting(Document::id).containsExactly("4", "3", "2", "1");
  }

  @Test
  @DisplayName("carries the details the tab shows, the attachment type decoded")
  void details() {
    givenInspection(11L, 7L, LocalDate.of(2023, 6, 1));
    givenDocument(2L, 7L, 11L, "deck.jpg", LocalDateTime.of(2023, 6, 2, 9, 30));

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
    givenDocument(1L, 8L, null, "other.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));

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
    givenDocument(1L, 7L, null, "deck.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    givenDocument(2L, 7L, null, "plan.pdf", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(7L);

    DocumentFile photo = service.file(7L, 1L);
    DocumentFile plan = service.file(7L, 2L);

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
    givenDocument(1L, 7L, null, "report.docx", LocalDateTime.of(2020, 1, 1, 0, 0));
    givenDocument(2L, 7L, null, "drawing.svg", LocalDateTime.of(2020, 1, 1, 0, 0));
    givenDocument(3L, 7L, null, "notes.html", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(7L);

    for (long id : List.of(1L, 2L, 3L)) {
      DocumentFile file = service.file(7L, id);
      assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
      assertThat(file.inline()).isFalse();
    }
  }

  @Test
  @DisplayName("refuses a file asked for under a structure it does not belong to")
  void fileOfAnotherStructure() {
    givenDocument(1L, 8L, null, "other.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(8L);

    assertThatThrownBy(() -> service.file(7L, 1L)).isInstanceOf(DocumentNotFoundException.class);
    assertThatThrownBy(() -> service.file(7L, 99L)).isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  @DisplayName("deletes files by id in one statement, without loading them")
  void bulkDelete() {
    givenDocument(1L, 7L, null, "a.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    givenDocument(2L, 7L, null, "b.jpg", LocalDateTime.of(2020, 1, 1, 0, 0));
    documents(7L);

    files.deleteByFileIdIn(List.of(1L));

    assertThat(files.findAll()).extracting(CrossingStructureFileEntity::getFileId)
        .containsExactly(2L);
  }
}
