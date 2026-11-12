package ca.bc.gov.nrs.cbr.schedulers;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository.StatusCount;
import ca.bc.gov.nrs.cbr.service.v1.FileCopyService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes finished jobs' rows from {@code THE.CBR_BATCH_JOB_ITEM} — EDUC-STUDENT-ASSESSMENT-API's
 * {@code PurgeOldSagaRecordsScheduler}, with the conditions CBR's jobs need.
 *
 * <p>A job's rows go only when it is listed in {@code purge.batch.job.items.jobs} and:
 * <ul>
 *   <li>it is switched off — a running job's fill could add rows between the check and the delete;</li>
 *   <li>it has no {@code READY} rows — it has finished;</li>
 *   <li>it has no {@code ERROR} rows — they are still to be looked into or retried;</li>
 *   <li>its rows last changed more than {@code purge.batch.job.items.after.days} ago.</li>
 * </ul>
 *
 * <p>Listing a job is a person's decision: no job's own rows can say they are no longer needed.
 * {@code FILE_COPY}'s are what its gate counts and what {@code FILE_PURGE} is filled from.
 */
@Component
public class PurgeBatchJobItemsScheduler {

  private static final Logger log = LoggerFactory.getLogger(PurgeBatchJobItemsScheduler.class);

  /** Cron value meaning "never" — a job whose crons are all this is switched off. */
  private static final String DISABLED = "-";

  /** Ids per delete statement, each committed. */
  private static final long CHUNK = 10_000;

  private final BatchJobItemRepository items;
  private final List<String> jobs;
  private final int afterDays;
  /** The jobs switched on now, by their crons. */
  private final Set<String> switchedOn;

  public PurgeBatchJobItemsScheduler(BatchJobItemRepository items,
      @Value("${purge.batch.job.items.jobs}") List<String> jobs,
      @Value("${purge.batch.job.items.after.days}") int afterDays,
      @Value("${scheduled.jobs.fill.file.copy.items.cron}") String fillFileCopyCron,
      @Value("${scheduled.jobs.copy.files.cron}") String copyFilesCron) {
    this.items = items;
    this.jobs = List.copyOf(jobs);
    this.afterDays = afterDays;
    this.switchedOn = DISABLED.equals(fillFileCopyCron) && DISABLED.equals(copyFilesCron)
        ? Set.of()
        : Set.of(FileCopyService.JOB_NAME);
  }

  @Scheduled(cron = "${scheduled.jobs.purge.batch.job.items.cron}")
  @SchedulerLock(name = "PURGE_BATCH_JOB_ITEMS",
      lockAtLeastFor = "${scheduled.jobs.purge.batch.job.items.cron.lockAtLeastFor}",
      lockAtMostFor = "${scheduled.jobs.purge.batch.job.items.cron.lockAtMostFor}")
  public void purgeBatchJobItems() {
    LockAssert.assertLocked();
    purge();
  }

  /** Each listed job and what became of it: rows deleted, or why not. */
  Map<String, String> purge() {
    Map<String, String> outcomes = new LinkedHashMap<>();
    for (String job : jobs) {
      String outcome = purge(job);
      log.info("PURGE_BATCH_JOB_ITEMS: {} — {}", job, outcome);
      outcomes.put(job, outcome);
    }
    return outcomes;
  }

  private String purge(String job) {
    if (switchedOn.contains(job)) {
      return "skipped, still switched on";
    }
    Optional<LocalDateTime> lastChange = items.findLastUpdateTimestamp(job);
    if (lastChange.isEmpty()) {
      return "no rows";
    }
    Map<BatchJobItemStatus, Long> counts = items.countByStatus(job).stream()
        .collect(Collectors.toMap(StatusCount::getStatus, StatusCount::getTotal));
    long ready = counts.getOrDefault(BatchJobItemStatus.READY, 0L);
    long error = counts.getOrDefault(BatchJobItemStatus.ERROR, 0L);
    if (ready > 0) {
      return "skipped, " + ready + " READY rows";
    }
    if (error > 0) {
      return "skipped, " + error + " ERROR rows";
    }
    if (lastChange.get().isAfter(LocalDateTime.now().minusDays(afterDays))) {
      return "skipped, last changed " + lastChange.get().toLocalDate() + " (kept " + afterDays
          + " days)";
    }
    long deleted = 0;
    Optional<Long> from = items.findLowestItemId(job);
    while (from.isPresent()) {
      deleted += items.deleteByJobNameAndItemIdBelow(job, from.get() + CHUNK);
      from = items.findLowestItemId(job);
    }
    return deleted + " rows deleted";
  }
}
