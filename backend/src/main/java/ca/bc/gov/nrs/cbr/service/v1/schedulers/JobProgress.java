package ca.bc.gov.nrs.cbr.service.v1.schedulers;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job's items by status, and the progress line its fill logs every run — shared by
 * {@code FILE_COPY} and {@code FILE_PURGE}. DELETE-AFTER-FILE-MIGRATION.
 */
record JobProgress(long ready, long done, long error) {

  private static final Logger log = LoggerFactory.getLogger(JobProgress.class);

  /** The progress line's pace is counted over this window. */
  private static final Duration PACE_WINDOW = Duration.ofMinutes(15);

  /** The job's counts now, without logging. */
  static JobProgress of(BatchJobItemRepository items, String jobName) {
    Map<BatchJobItemStatus, Long> counts = new EnumMap<>(BatchJobItemStatus.class);
    for (BatchJobItemStatus status : BatchJobItemStatus.values()) {
      counts.put(status, 0L);
    }
    items.countByStatus(jobName).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
    return new JobProgress(counts.get(BatchJobItemStatus.READY),
        counts.get(BatchJobItemStatus.DONE), counts.get(BatchJobItemStatus.ERROR));
  }

  /**
   * The job's counts now, logged as one line: counts, share done, how many the fill just added,
   * the pace over the last 15 minutes and, from it, the time left.
   */
  static JobProgress report(BatchJobItemRepository items, String jobName, int added) {
    JobProgress progress = of(items, jobName);
    long total = progress.ready + progress.done + progress.error;
    long pace = items.countByJobNameAndStatusAndUpdateTimestampAfter(jobName,
        BatchJobItemStatus.DONE, LocalDateTime.now(ZoneId.systemDefault()).minus(PACE_WINDOW));
    log.info("{}: READY={} DONE={} ERROR={} ({}% done; {} added now; {} done in the last 15 min{})",
        jobName, progress.ready, progress.done, progress.error,
        total == 0 ? 100 : progress.done * 100 / total, added, pace,
        pace == 0 || progress.ready == 0 ? "" : "; about " + eta(progress.ready, pace) + " left");
    return progress;
  }

  private static String eta(long ready, long donePerWindow) {
    Duration left = Duration.ofSeconds(ready * PACE_WINDOW.toSeconds() / donePerWindow);
    return left.toHours() > 0
        ? left.toHours() + " h " + left.toMinutesPart() + " min"
        : Math.max(1, left.toMinutes()) + " min";
  }
}
