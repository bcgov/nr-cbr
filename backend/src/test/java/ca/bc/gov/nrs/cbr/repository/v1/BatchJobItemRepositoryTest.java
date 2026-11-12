package ca.bc.gov.nrs.cbr.repository.v1;

import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.DONE;
import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.ERROR;
import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * The work-item queries against H2 — the JPQL, and the two native {@code FILE_COPY} statements
 * with {@code DBMS_LOB.GETLENGTH} aliased in {@code schema.sql}.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class BatchJobItemRepositoryTest {

  private static final String JOB = "FILE_COPY";

  @Autowired
  private BatchJobItemRepository repository;

  @Autowired
  private EntityManager entityManager;

  private void givenItem(String job, long id, BatchJobItemStatus status, LocalDateTime when) {
    entityManager.persist(BatchJobItemEntity.builder()
        .jobName(job).itemId(id).status(status).updateTimestamp(when).build());
    entityManager.flush();
  }

  private void givenFile(long fileId, byte[] content) {
    entityManager.createNativeQuery("""
        INSERT INTO THE.CROSSING_STRUCTURE_FILE_DETAIL (FILE_ID, CROSSING_STRUCTURE_ID)
        VALUES (?, 1)""").setParameter(1, fileId).executeUpdate();
    entityManager.createNativeQuery(
            "INSERT INTO THE.CROSSING_STRUCTURE_FILE (FILE_ID, STRUCTURE_FILE) VALUES (?, ?)")
        .setParameter(1, fileId).setParameter(2, content).executeUpdate();
  }

  private BatchJobItemStatus statusOf(String job, long id) {
    entityManager.clear();
    return repository.findById(new BatchJobItemEntity.Key(job, id)).orElseThrow().getStatus();
  }

  @Test
  @DisplayName("lists every file with bytes once, however often it fills")
  void insertsFileCopyItems() {
    givenFile(1, new byte[] {1});
    givenFile(2, new byte[0]);
    givenFile(3, new byte[] {3, 3});

    assertThat(repository.insertFileCopyItems()).isEqualTo(2);
    assertThat(repository.insertFileCopyItems()).isZero();

    givenFile(4, new byte[] {4});
    assertThat(repository.insertFileCopyItems()).isEqualTo(1);
    assertThat(repository.findItemIds(JOB, READY, -1, 10)).containsExactly(1L, 3L, 4L);
  }

  @Test
  @DisplayName("finds a job's READY ids above a point, lowest first, up to a limit")
  void findsItemIds() {
    LocalDateTime now = LocalDateTime.now();
    for (long id = 1; id <= 6; id++) {
      givenItem(JOB, id, id == 3 ? DONE : READY, now);
    }
    givenItem("OTHER", 7, READY, now);

    assertThat(repository.findItemIds(JOB, READY, -1, 3)).containsExactly(1L, 2L, 4L);
    assertThat(repository.findItemIds(JOB, READY, 4, 10)).containsExactly(5L, 6L);
    assertThat(repository.findHighestItemId(JOB, READY)).contains(6L);
    assertThat(repository.findHighestItemId("NONE", READY)).isEmpty();
  }

  @Test
  @DisplayName("records an item's outcome and counts the job's items by status and pace")
  void updatesAndCounts() {
    LocalDateTime now = LocalDateTime.now();
    givenItem(JOB, 1, READY, now.minusHours(2));
    givenItem(JOB, 2, READY, now.minusHours(2));
    givenItem(JOB, 3, DONE, now.minusHours(2));

    assertThat(repository.updateStatus(JOB, 1, DONE, now)).isEqualTo(1);
    assertThat(repository.updateStatus(JOB, 2, ERROR, now)).isEqualTo(1);

    assertThat(statusOf(JOB, 1)).isEqualTo(DONE);
    assertThat(repository.countByStatus(JOB))
        .extracting(BatchJobItemRepository.StatusCount::getStatus,
            BatchJobItemRepository.StatusCount::getTotal)
        .containsExactlyInAnyOrder(tuple(DONE, 2L), tuple(ERROR, 1L));
    assertThat(repository.countByJobNameAndStatusAndUpdateTimestampAfter(JOB, DONE,
        now.minusMinutes(15))).isEqualTo(1);
    assertThat(repository.findLastUpdateTimestamp(JOB)).isPresent();
    assertThat(repository.findLastUpdateTimestamp("NONE")).isEmpty();
  }

  @Test
  @DisplayName("counts as not copied every file with bytes that has no DONE item, listed or not")
  void countsFilesNotCopied() {
    givenFile(1, new byte[] {1});
    givenFile(2, new byte[] {2});
    givenFile(3, new byte[0]);
    repository.insertFileCopyItems();
    givenFile(4, new byte[] {4});
    repository.updateStatus(JOB, 1, DONE, LocalDateTime.now());
    repository.updateStatus(JOB, 2, ERROR, LocalDateTime.now());

    // 2 is in error, 4 was never listed; 3 has nothing to copy.
    assertThat(repository.countFilesNotCopied()).isEqualTo(2);
  }

  @Test
  @DisplayName("deletes a job's items below an id, and no other job's")
  void deletesBelow() {
    LocalDateTime now = LocalDateTime.now();
    for (long id = 1; id <= 5; id++) {
      givenItem(JOB, id, DONE, now);
      givenItem("OTHER", id, DONE, now);
    }

    assertThat(repository.deleteByJobNameAndItemIdBelow(JOB, 4)).isEqualTo(3);
    assertThat(repository.findLowestItemId(JOB)).contains(4L);
    assertThat(repository.findLowestItemId("OTHER")).contains(1L);
  }
}
