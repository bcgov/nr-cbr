package ca.bc.gov.nrs.cbr.schedulers;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * The purge against the work-item table in H2: which jobs' rows it deletes, and every reason it
 * leaves them.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class PurgeBatchJobItemsSchedulerTest {

  private static final String OFF = "-";

  @Autowired
  private BatchJobItemRepository items;

  @Autowired
  private EntityManager entityManager;

  private void givenRows(String job, long count, BatchJobItemStatus status, int daysAgo) {
    LocalDateTime when = LocalDateTime.now().minusDays(daysAgo);
    for (long id = 1; id <= count; id++) {
      entityManager.persist(BatchJobItemEntity.builder()
          .jobName(job).itemId(id).status(status).updateTimestamp(when).build());
    }
    entityManager.flush();
    entityManager.clear();
  }

  private long rows(String job) {
    return entityManager.createQuery(
            "SELECT COUNT(i) FROM BatchJobItemEntity i WHERE i.jobName = :job", Long.class)
        .setParameter("job", job).getSingleResult();
  }

  private PurgeBatchJobItemsScheduler purge(List<String> jobs) {
    return new PurgeBatchJobItemsScheduler(items, jobs, 30, OFF, OFF);
  }

  @Test
  @DisplayName("deletes a listed, finished job's rows once kept long enough — in chunks, and no "
      + "other job's")
  void deletesFinishedJob() {
    givenRows("FILE_COPY", 25_003, BatchJobItemStatus.DONE, 40);
    givenRows("OTHER", 3, BatchJobItemStatus.DONE, 40);

    assertThat(purge(List.of("FILE_COPY")).purge())
        .containsEntry("FILE_COPY", "25003 rows deleted");

    assertThat(rows("FILE_COPY")).isZero();
    assertThat(rows("OTHER")).isEqualTo(3);
  }

  @Test
  @DisplayName("leaves a job with READY rows: it has not finished")
  void keepsUnfinished() {
    givenRows("FILE_COPY", 2, BatchJobItemStatus.DONE, 40);
    items.updateStatus("FILE_COPY", 2, BatchJobItemStatus.READY, LocalDateTime.now().minusDays(40));

    assertThat(purge(List.of("FILE_COPY")).purge())
        .containsEntry("FILE_COPY", "skipped, 1 READY rows");
    assertThat(rows("FILE_COPY")).isEqualTo(2);
  }

  @Test
  @DisplayName("leaves a job with ERROR rows: they are still to be looked into")
  void keepsErrors() {
    givenRows("FILE_COPY", 2, BatchJobItemStatus.DONE, 40);
    items.updateStatus("FILE_COPY", 1, BatchJobItemStatus.ERROR, LocalDateTime.now().minusDays(40));

    assertThat(purge(List.of("FILE_COPY")).purge())
        .containsEntry("FILE_COPY", "skipped, 1 ERROR rows");
    assertThat(rows("FILE_COPY")).isEqualTo(2);
  }

  @Test
  @DisplayName("keeps a job's rows for the retention period after its last change")
  void keepsRecent() {
    givenRows("FILE_COPY", 2, BatchJobItemStatus.DONE, 40);
    items.updateStatus("FILE_COPY", 2, BatchJobItemStatus.DONE, LocalDateTime.now().minusDays(5));

    assertThat(purge(List.of("FILE_COPY")).purge().get("FILE_COPY"))
        .startsWith("skipped, last changed ").endsWith("(kept 30 days)");
    assertThat(rows("FILE_COPY")).isEqualTo(2);
  }

  @Test
  @DisplayName("leaves FILE_COPY while either of its crons is set")
  void keepsRunningJob() {
    givenRows("FILE_COPY", 2, BatchJobItemStatus.DONE, 40);

    assertThat(new PurgeBatchJobItemsScheduler(items, List.of("FILE_COPY"), 30, OFF,
        "0/2 * * * * *").purge()).containsEntry("FILE_COPY", "skipped, still switched on");
    assertThat(new PurgeBatchJobItemsScheduler(items, List.of("FILE_COPY"), 30,
        "0 */15 * * * *", OFF).purge()).containsEntry("FILE_COPY", "skipped, still switched on");
    assertThat(rows("FILE_COPY")).isEqualTo(2);
  }

  @Test
  @DisplayName("touches no job that is not listed, and says so for a listed job with no rows")
  void onlyListed() {
    givenRows("FILE_COPY", 2, BatchJobItemStatus.DONE, 40);

    assertThat(purge(List.of("FILE_PURGE")).purge())
        .containsExactlyEntriesOf(Map.of("FILE_PURGE", "no rows"));
    assertThat(purge(List.of()).purge()).isEmpty();
    assertThat(rows("FILE_COPY")).isEqualTo(2);
  }
}
