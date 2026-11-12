package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileDetailRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@code FILE_COPY}'s one-file copy against the file tables in H2, with the object store mocked:
 * what goes where, when a copy counts as done, and when nothing is uploaded at all.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class FileCopyServiceTest {

  @Autowired
  private CrossingStructureFileRepository files;

  @Autowired
  private CrossingStructureFileDetailRepository details;

  @Autowired
  private BatchJobItemRepository items;

  @Autowired
  private EntityManager entityManager;

  private ObjectStorageService storage;
  private FileCopyService service;

  @BeforeEach
  void setUp() {
    storage = mock(ObjectStorageService.class);
    when(storage.objectEtag(anyString())).thenReturn(Optional.empty());
    service = new FileCopyService(files, details, items, storage);
  }

  private void givenFile(long fileId, String filename, String extension, byte[] content) {
    entityManager.createNativeQuery("""
            INSERT INTO THE.CROSSING_STRUCTURE_FILE_DETAIL
              (FILE_ID, CROSSING_STRUCTURE_ID, FILENAME, EFILE_EXTENSION_CODE) VALUES (?, 1, ?, ?)""")
        .setParameter(1, fileId).setParameter(2, filename).setParameter(3, extension)
        .executeUpdate();
    entityManager.createNativeQuery(
            "INSERT INTO THE.CROSSING_STRUCTURE_FILE (FILE_ID, STRUCTURE_FILE) VALUES (?, ?)")
        .setParameter(1, fileId).setParameter(2, content).executeUpdate();
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] md5(byte[] content) throws Exception {
    return MessageDigest.getInstance("MD5").digest(content);
  }

  private static String etagOf(byte[] content) throws Exception {
    return "\"" + HexFormat.of().formatHex(md5(content)) + "\"";
  }

  @Test
  @DisplayName("copies the bytes to the file's key, typed by its name, with its MD5 — done when the "
      + "ETag matches")
  void copies() throws Exception {
    byte[] content = bytes("JFIF bridge deck");
    givenFile(42, "Deck.JPG", "jpg", content);
    when(storage.putObjectWithMd5(anyString(), anyString(), any(), any()))
        .thenReturn(etagOf(content));

    assertThat(service.copy(42)).isEqualTo(BatchJobItemStatus.DONE);

    verify(storage).putObjectWithMd5("docs/42", "image/jpeg", content, md5(content));
    verify(storage, never()).deleteObject(anyString());
  }

  @Test
  @DisplayName("uploads nothing when the store already holds the file with the right ETag")
  void alreadyCopied() throws Exception {
    byte[] content = bytes("already there");
    givenFile(43, "a.jpg", "jpg", content);
    when(storage.objectEtag("docs/43")).thenReturn(Optional.of(etagOf(content)));

    assertThat(service.copy(43)).isEqualTo(BatchJobItemStatus.DONE);

    verify(storage, never()).putObjectWithMd5(anyString(), anyString(), any(), any());
  }

  @Test
  @DisplayName("uploads again over an object whose ETag is not the file's MD5")
  void replacesWrongObject() throws Exception {
    byte[] content = bytes("the right bytes");
    givenFile(44, "a.jpg", "jpg", content);
    when(storage.objectEtag("docs/44")).thenReturn(Optional.of("\"0000\""));
    when(storage.putObjectWithMd5(anyString(), anyString(), any(), any()))
        .thenReturn(etagOf(content));

    assertThat(service.copy(44)).isEqualTo(BatchJobItemStatus.DONE);

    verify(storage).putObjectWithMd5(eq("docs/44"), anyString(), eq(content), any());
  }

  @Test
  @DisplayName("types a file with no usable name by its extension code, else as octet-stream")
  void typesByExtensionThenOctetStream() {
    givenFile(5, null, "pdf", bytes("pdf"));
    givenFile(6, "notes", null, bytes("??"));
    when(storage.putObjectWithMd5(anyString(), anyString(), any(), any())).thenReturn("\"x\"");

    service.copy(5);
    service.copy(6);

    verify(storage).putObjectWithMd5(eq("docs/5"), eq("application/pdf"), any(), any());
    verify(storage).putObjectWithMd5(eq("docs/6"), eq("application/octet-stream"), any(), any());
  }

  @Test
  @DisplayName("is an error when the stored object's ETag is not the bytes' MD5")
  void mismatchIsError() {
    givenFile(7, "a.jpg", "jpg", bytes("seven"));
    when(storage.putObjectWithMd5(anyString(), anyString(), any(), any()))
        .thenReturn("\"00000000000000000000000000000000\"");

    assertThat(service.copy(7)).isEqualTo(BatchJobItemStatus.ERROR);
  }

  @Test
  @DisplayName("is an error when the gateway refuses the file")
  void refusalIsError() {
    givenFile(8, "a.jpg", "jpg", bytes("eight"));
    when(storage.putObjectWithMd5(anyString(), anyString(), any(), any())).thenThrow(
        S3Exception.builder().statusCode(403).requestId("req-1")
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("SlowDown").build()).build());

    assertThat(service.copy(8)).isEqualTo(BatchJobItemStatus.ERROR);
  }

  @Test
  @DisplayName("is done with nothing to copy when the file was deleted or emptied since listed")
  void goneIsDone() {
    givenFile(9, "a.jpg", "jpg", new byte[0]);

    assertThat(service.copy(9)).isEqualTo(BatchJobItemStatus.DONE);
    assertThat(service.copy(10)).isEqualTo(BatchJobItemStatus.DONE);

    verify(storage, never()).putObjectWithMd5(anyString(), anyString(), any(), any());
  }

  @Test
  @DisplayName("removes the object again when the file is deleted while it is being copied")
  void deletedDuringCopy() throws Exception {
    byte[] content = bytes("eleven");
    givenFile(11, "a.jpg", "jpg", content);
    String etag = etagOf(content);
    when(storage.putObjectWithMd5(anyString(), anyString(), any(), any())).thenAnswer(put -> {
      entityManager.createNativeQuery("DELETE FROM THE.CROSSING_STRUCTURE_FILE WHERE FILE_ID = 11")
          .executeUpdate();
      entityManager.createNativeQuery(
          "DELETE FROM THE.CROSSING_STRUCTURE_FILE_DETAIL WHERE FILE_ID = 11").executeUpdate();
      entityManager.clear();
      return etag;
    });

    assertThat(service.copy(11)).isEqualTo(BatchJobItemStatus.DONE);

    verify(storage).deleteObject("docs/11");
  }

  @Test
  @DisplayName("records the outcome on the file's item, an unexpected failure as ERROR")
  void recordsOutcome() throws Exception {
    givenFile(12, "a.jpg", "jpg", bytes("twelve"));
    givenFile(13, "b.jpg", "jpg", bytes("thirteen"));
    for (long id : new long[] {12, 13}) {
      entityManager.persist(BatchJobItemEntity.builder().jobName(FileCopyService.JOB_NAME)
          .itemId(id).status(BatchJobItemStatus.READY).updateTimestamp(LocalDateTime.now())
          .build());
    }
    entityManager.flush();
    when(storage.putObjectWithMd5(eq("docs/12"), anyString(), any(), any()))
        .thenReturn(etagOf(bytes("twelve")));
    when(storage.objectEtag("docs/13")).thenThrow(new IllegalStateException("gateway down"));

    assertThat(service.copyFile(12).get()).isEqualTo(BatchJobItemStatus.DONE);
    assertThat(service.copyFile(13).get()).isEqualTo(BatchJobItemStatus.ERROR);

    entityManager.clear();
    assertThat(items.findById(new BatchJobItemEntity.Key(FileCopyService.JOB_NAME, 12L)))
        .get().extracting(BatchJobItemEntity::getStatus).isEqualTo(BatchJobItemStatus.DONE);
    assertThat(items.findById(new BatchJobItemEntity.Key(FileCopyService.JOB_NAME, 13L)))
        .get().extracting(BatchJobItemEntity::getStatus).isEqualTo(BatchJobItemStatus.ERROR);
  }
}
