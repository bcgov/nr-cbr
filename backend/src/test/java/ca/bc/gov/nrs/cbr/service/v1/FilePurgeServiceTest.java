package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@code FILE_PURGE}'s one-file purge against the file tables in H2, with the object store mocked:
 * a BLOB is emptied only when its object's ETag is its MD5, and kept whenever anything is short of
 * that.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class FilePurgeServiceTest {

  @Autowired
  private CrossingStructureFileRepository files;

  @Autowired
  private BatchJobItemRepository items;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private ObjectStorageService storage;
  private FilePurgeService service;

  @BeforeEach
  void setUp() {
    storage = mock(ObjectStorageService.class);
    when(storage.objectEtag(anyString())).thenReturn(Optional.empty());
    service = new FilePurgeService(files, items, storage, transactionManager);
  }

  private void givenFile(long fileId, byte[] content) {
    entityManager.createNativeQuery("""
            INSERT INTO THE.CROSSING_STRUCTURE_FILE_DETAIL (FILE_ID, CROSSING_STRUCTURE_ID)
            VALUES (?, 1)""")
        .setParameter(1, fileId).executeUpdate();
    entityManager.createNativeQuery(
            "INSERT INTO THE.CROSSING_STRUCTURE_FILE (FILE_ID, STRUCTURE_FILE) VALUES (?, ?)")
        .setParameter(1, fileId).setParameter(2, content).executeUpdate();
  }

  /** The file's bytes as stored now; empty when its row is gone. */
  private Optional<byte[]> stored(long fileId) {
    entityManager.flush();
    entityManager.clear();
    return files.findById(fileId).map(CrossingStructureFileEntity::getStructureFile);
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String etagOf(byte[] content) throws Exception {
    return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content))
        + "\"";
  }

  @Test
  @DisplayName("empties the BLOB, keeping its row, when the object's ETag is the bytes' MD5")
  void emptiesProvedCopy() throws Exception {
    byte[] content = bytes("JFIF bridge deck");
    givenFile(42, content);
    when(storage.objectEtag("docs/42")).thenReturn(Optional.of(etagOf(content)));

    assertThat(service.purge(42)).isEqualTo(BatchJobItemStatus.DONE);

    assertThat(stored(42)).get().extracting(blob -> blob.length).isEqualTo(0);
  }

  @Test
  @DisplayName("matches the ETag whatever its case")
  void etagCaseIgnored() throws Exception {
    byte[] content = bytes("upper-case etag");
    givenFile(43, content);
    when(storage.objectEtag("docs/43")).thenReturn(Optional.of(etagOf(content).toUpperCase()));

    assertThat(service.purge(43)).isEqualTo(BatchJobItemStatus.DONE);
    assertThat(stored(43)).get().extracting(blob -> blob.length).isEqualTo(0);
  }

  @Test
  @DisplayName("keeps the BLOB, as an error, when the file has no object")
  void keepsWithoutObject() {
    byte[] content = bytes("only copy");
    givenFile(44, content);

    assertThat(service.purge(44)).isEqualTo(BatchJobItemStatus.ERROR);

    assertThat(stored(44)).get().isEqualTo(content);
  }

  @Test
  @DisplayName("keeps the BLOB, as an error, when the object's ETag is not the bytes' MD5")
  void keepsOnMismatch() {
    byte[] content = bytes("these bytes");
    givenFile(45, content);
    when(storage.objectEtag("docs/45"))
        .thenReturn(Optional.of("\"00000000000000000000000000000000\""));

    assertThat(service.purge(45)).isEqualTo(BatchJobItemStatus.ERROR);

    assertThat(stored(45)).get().isEqualTo(content);
  }

  @Test
  @DisplayName("is done without asking the store when the BLOB is already empty or the file gone")
  void nothingToEmpty() {
    givenFile(46, new byte[0]);

    assertThat(service.purge(46)).isEqualTo(BatchJobItemStatus.DONE);
    assertThat(service.purge(47)).isEqualTo(BatchJobItemStatus.DONE);

    verify(storage, never()).objectEtag(anyString());
  }

  @Test
  @DisplayName("records the outcome on the file's item; the store failing is an ERROR, the BLOB "
      + "kept")
  void recordsOutcome() throws Exception {
    byte[] proved = bytes("proved");
    byte[] unreachable = bytes("unreachable");
    givenFile(48, proved);
    givenFile(49, unreachable);
    for (long id : new long[] {48, 49}) {
      entityManager.persist(BatchJobItemEntity.builder().jobName(FilePurgeService.JOB_NAME)
          .itemId(id).status(BatchJobItemStatus.READY).updateTimestamp(LocalDateTime.now())
          .build());
    }
    entityManager.flush();
    when(storage.objectEtag("docs/48")).thenReturn(Optional.of(etagOf(proved)));
    when(storage.objectEtag("docs/49")).thenThrow(S3Exception.builder().statusCode(503)
        .requestId("req-1")
        .awsErrorDetails(AwsErrorDetails.builder().errorCode("SlowDown").build()).build());

    assertThat(service.purgeFile(48).get()).isEqualTo(BatchJobItemStatus.DONE);
    assertThat(service.purgeFile(49).get()).isEqualTo(BatchJobItemStatus.ERROR);

    assertThat(stored(49)).get().isEqualTo(unreachable);
    assertThat(items.findById(new BatchJobItemEntity.Key(FilePurgeService.JOB_NAME, 48L)))
        .get().extracting(BatchJobItemEntity::getStatus).isEqualTo(BatchJobItemStatus.DONE);
    assertThat(items.findById(new BatchJobItemEntity.Key(FilePurgeService.JOB_NAME, 49L)))
        .get().extracting(BatchJobItemEntity::getStatus).isEqualTo(BatchJobItemStatus.ERROR);
  }
}
