package ca.bc.gov.nrs.cbr.service.v1.schedulers;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;

/**
 * Hands a job's {@code READY} items to its {@code @Async} worker, fire-and-forget, shared by
 * {@code FILE_COPY} and {@code FILE_PURGE}. One per job per pod.
 *
 * <p>An item stays {@code READY} until its worker finishes, so a later firing would hand it out
 * again; two guards keep that rare, and each worker's own check makes it cheap when it happens:
 * <ul>
 *   <li><b>in flight on this pod</b> — items handed out and not yet finished are skipped, and no
 *   more than the cap are in flight at once;</li>
 *   <li><b>a cursor per pod</b> — each pod starts at a random id and works upward, wrapping at the
 *   end. The lock moves between pods from one firing to the next, and without this every pod would
 *   take the same lowest ids.</li>
 * </ul>
 *
 * <p>DELETE-AFTER-FILE-MIGRATION, with the two jobs.
 */
final class ReadyItemsHandOut {

  private static final Logger log = LoggerFactory.getLogger(ReadyItemsHandOut.class);

  private final String jobName;
  private final BatchJobItemRepository items;
  private final LongFunction<CompletableFuture<BatchJobItemStatus>> worker;
  private final int cap;
  /** A random start in {@code [0, bound]}; replaced in tests. */
  private final LongUnaryOperator randomStart;

  private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
  /** The last id this pod handed out; null until its first firing with work to do. */
  private Long cursor;

  /**
   * @param worker      starts one item's work on its executor; the future completes once the
   *                    outcome is saved, exceptionally if it could not be (the item stays READY)
   * @param cap         items in flight on this pod at most
   */
  ReadyItemsHandOut(String jobName, BatchJobItemRepository items,
      LongFunction<CompletableFuture<BatchJobItemStatus>> worker, int cap,
      LongUnaryOperator randomStart) {
    this.jobName = jobName;
    this.items = items;
    this.worker = worker;
    this.cap = cap;
    this.randomStart = randomStart;
  }

  /** Hands out up to the cap less those in flight; returns the number handed out. */
  int handOutReady() {
    int free = cap - inFlight.size();
    if (free <= 0) {
      log.debug("{}: {} items in flight, none handed out", jobName, inFlight.size());
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
      log.debug("{}: {} items handed out, {} in flight", jobName, handedOut, inFlight.size());
    }
    return handedOut;
  }

  /** What became of one item offered to the executor. */
  private enum HandOut { HANDED_OUT, ALREADY_IN_FLIGHT, EXECUTOR_FULL }

  /** A random point among the READY ids, just below it; null when nothing is READY. */
  private Long startingCursor() {
    return items.findHighestItemId(jobName, BatchJobItemStatus.READY)
        .map(highest -> randomStart.applyAsLong(highest) - 1)
        .orElse(null);
  }

  /** Up to {@code free} READY ids after the cursor, wrapping round to the lowest past the last. */
  private List<Long> nextReady(int free) {
    List<Long> ids = new ArrayList<>(
        items.findItemIds(jobName, BatchJobItemStatus.READY, cursor, free));
    if (ids.size() < free && cursor >= 0) {
      items.findItemIds(jobName, BatchJobItemStatus.READY, -1, free + inFlight.size()).stream()
          .filter(id -> !ids.contains(id))
          .limit((long) free - ids.size())
          .forEach(ids::add);
    }
    return ids;
  }

  /**
   * Hands one item to the executor, unless it is already in flight on this pod; it leaves flight
   * when its work finishes, saved or not.
   */
  private HandOut handOut(long id) {
    if (!inFlight.add(id)) {
      return HandOut.ALREADY_IN_FLIGHT;
    }
    try {
      worker.apply(id).whenComplete((status, failed) -> {
        inFlight.remove(id);
        if (failed != null) {
          log.error("{}: item {}'s outcome could not be saved; it stays READY", jobName, id,
              failed);
        }
      });
      return HandOut.HANDED_OUT;
    } catch (TaskRejectedException full) {
      inFlight.remove(id);
      log.warn("{}: the executor is full; the rest wait for the next firing", jobName);
      return HandOut.EXECUTOR_FULL;
    }
  }
}
