package ca.bc.gov.nrs.cbr.service.v1.schedulers;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.service.v1.FileCopyService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/**
 * What {@code FileCopyScheduler}'s two steps do — EDUC-STUDENT-ASSESSMENT-API's
 * {@code EventTaskSchedulerAsyncService} for {@code FILE_COPY}.
 *
 * <p><b>Handing out files.</b> As EDUC hands loaded students to NATS, this hands {@code READY}
 * files to {@link FileCopyService#copyFile}, which runs on the {@code fileCopyTaskExecutor}, and
 * returns at once. A file stays {@code READY} until its copy finishes, so a later firing would hand
 * it out again; two guards keep that rare, and {@link FileCopyService}'s check against the store
 * makes it cheap when it happens:
 * <ul>
 *   <li><b>in flight on this pod</b> — files handed out and not yet finished are skipped, and no
 *   more than {@code number.files.process.copy} are in flight at once;</li>
 *   <li><b>a cursor per pod</b> — each pod starts at a random id and works upward, wrapping at the
 *   end. The lock moves between pods from one firing to the next, and without this every pod would
 *   take the same lowest ids.</li>
 * </ul>
 *
 * <p>DELETE-AFTER-FILE-MIGRATION, with {@link FileCopyService}.
 */
@Service
public class FileCopyTaskSchedulerService {

  private static final Logger log = LoggerFactory.getLogger(FileCopyTaskSchedulerService.class);

  private static final String JOB_NAME = FileCopyService.JOB_NAME;

  /** The progress line's pace is counted over this window. */
  private static final Duration PACE_WINDOW = Duration.ofMinutes(15);

  private final BatchJobItemRepository items;
  private final FileCopyService fileCopyService;
  private final int numberOfFilesToProcess;
  /** A random start in {@code [0, bound]}; replaced in tests. */
  private final LongUnaryOperator randomStart;

  private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
  /** The last id this pod handed out; null until its first firing with work to do. */
  private Long cursor;

  @Autowired
  public FileCopyTaskSchedulerService(BatchJobItemRepository items,
      FileCopyService fileCopyService,
      @Value("${number.files.process.copy}") int numberOfFilesToProcess) {
    this(items, fileCopyService, numberOfFilesToProcess,
        bound -> ThreadLocalRandom.current().nextLong(bound + 1));
  }

  FileCopyTaskSchedulerService(BatchJobItemRepository items, FileCopyService fileCopyService,
      int numberOfFilesToProcess, LongUnaryOperator randomStart) {
    this.items = items;
    this.fileCopyService = fileCopyService;
    this.numberOfFilesToProcess = numberOfFilesToProcess;
    this.randomStart = randomStart;
  }

  /**
   * Hands out up to {@code number.files.process.copy} less those in flight; returns the number
   * handed out.
   */
  public int findAndCopyReadyFiles() {
    int free = numberOfFilesToProcess - inFlight.size();
    if (free <= 0) {
      log.debug("{}: {} files in flight, none handed out", JOB_NAME, inFlight.size());
      return 0;
    }
    if (cursor == null) {
      cursor = startingCursor();
    }
    if (cursor == null) {
      return 0;
    }
    int handedOut = 0;
    for (long id : nextReady(free)) {
      HandOut outcome = handOut(id);
      if (outcome == HandOut.EXECUTOR_FULL) {
        break;
      }
      if (outcome == HandOut.HANDED_OUT) {
        cursor = id;
        handedOut++;
      }
    }
    if (handedOut > 0) {
      log.debug("{}: {} files handed out, {} in flight", JOB_NAME, handedOut, inFlight.size());
    }
    return handedOut;
  }

  /** What became of one file offered to the copy executor. */
  private enum HandOut { HANDED_OUT, ALREADY_IN_FLIGHT, EXECUTOR_FULL }

  /** A random point among the READY ids, just below it; null when nothing is READY. */
  private Long startingCursor() {
    return items.findHighestItemId(JOB_NAME, BatchJobItemStatus.READY)
        .map(highest -> randomStart.applyAsLong(highest) - 1)
        .orElse(null);
  }

  /** Up to {@code free} READY ids after the cursor, wrapping round to the lowest past the last. */
  private List<Long> nextReady(int free) {
    List<Long> ids = new ArrayList<>(
        items.findItemIds(JOB_NAME, BatchJobItemStatus.READY, cursor, free));
    if (ids.size() < free && cursor >= 0) {
      items.findItemIds(JOB_NAME, BatchJobItemStatus.READY, -1, free + inFlight.size()).stream()
          .filter(id -> !ids.contains(id))
          .limit((long) free - ids.size())
          .forEach(ids::add);
    }
    return ids;
  }

  /**
   * Hands one file to the copy executor, unless it is already in flight on this pod; it leaves
   * flight when its copy finishes, saved or not.
   */
  private HandOut handOut(long id) {
    if (!inFlight.add(id)) {
      return HandOut.ALREADY_IN_FLIGHT;
    }
    try {
      fileCopyService.copyFile(id).whenComplete((status, failed) -> {
        inFlight.remove(id);
        if (failed != null) {
          log.error("{}: file {}'s outcome could not be saved; it stays READY", JOB_NAME, id,
              failed);
        }
      });
      return HandOut.HANDED_OUT;
    } catch (TaskRejectedException full) {
      inFlight.remove(id);
      log.warn("{}: the copy executor is full; the rest wait for the next firing", JOB_NAME);
      return HandOut.EXECUTOR_FULL;
    }
  }

  /**
   * Lists the files added since, then logs one progress line — and, once nothing is
   * {@code READY}, the gate line: {@code PASS} only with no errors and every file with bytes
   * {@code DONE}.
   */
  public void fillAndReport() {
    int added = items.insertFileCopyItems();
    Map<BatchJobItemStatus, Long> counts = new EnumMap<>(BatchJobItemStatus.class);
    for (BatchJobItemStatus status : BatchJobItemStatus.values()) {
      counts.put(status, 0L);
    }
    items.countByStatus(JOB_NAME).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
    long ready = counts.get(BatchJobItemStatus.READY);
    long done = counts.get(BatchJobItemStatus.DONE);
    long error = counts.get(BatchJobItemStatus.ERROR);
    long total = ready + done + error;
    long pace = items.countByJobNameAndStatusAndUpdateTimestampAfter(JOB_NAME,
        BatchJobItemStatus.DONE, LocalDateTime.now(ZoneId.systemDefault()).minus(PACE_WINDOW));
    log.info("{}: READY={} DONE={} ERROR={} ({}% done; {} added now; {} done in the last 15 min{})",
        JOB_NAME, ready, done, error, total == 0 ? 100 : done * 100 / total, added, pace,
        pace == 0 || ready == 0 ? "" : "; about " + eta(ready, pace) + " left");
    if (ready == 0) {
      long notCopied = items.countFilesNotCopied();
      log.info("{} GATE: {} — DONE={}, ERROR={}, files with bytes not copied={}", JOB_NAME,
          error == 0 && notCopied == 0 ? "PASS" : "FAIL", done, error, notCopied);
    }
  }

  private static String eta(long ready, long donePerWindow) {
    Duration left = Duration.ofSeconds(ready * PACE_WINDOW.toSeconds() / donePerWindow);
    return left.toHours() > 0
        ? left.toHours() + " h " + left.toMinutesPart() + " min"
        : Math.max(1, left.toMinutes()) + " min";
  }
}
