package ca.bc.gov.nrs.cbr.service.v1.schedulers;

import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.DONE;
import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.service.v1.FileCopyService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

/**
 * Handing out files: the cap on files in flight, the per-pod cursor and its wrap, and what happens
 * when a copy finishes or the executor is full. The repository is a stand-in over a sorted set of
 * READY ids; copies finish only when the test completes their futures.
 */
class FileCopyTaskSchedulerServiceTest {

  private static final String JOB = FileCopyService.JOB_NAME;

  private final TreeSet<Long> ready = new TreeSet<>();
  private final Map<Long, CompletableFuture<BatchJobItemStatus>> copying = new HashMap<>();
  private final List<Long> handedOut = new ArrayList<>();
  private BatchJobItemRepository items;
  private FileCopyService fileCopyService;

  @BeforeEach
  void setUp() {
    items = mock(BatchJobItemRepository.class);
    when(items.findHighestItemId(JOB, READY))
        .thenAnswer(call -> Optional.ofNullable(ready.isEmpty() ? null : ready.last()));
    when(items.findItemIds(eq(JOB), eq(READY), anyLong(), anyInt())).thenAnswer(call -> {
      long after = call.getArgument(2);
      int limit = call.getArgument(3);
      return ready.tailSet(after, false).stream().limit(limit).toList();
    });
    fileCopyService = mock(FileCopyService.class);
    when(fileCopyService.copyFile(anyLong())).thenAnswer(call -> {
      long id = call.getArgument(0);
      handedOut.add(id);
      CompletableFuture<BatchJobItemStatus> future = new CompletableFuture<>();
      copying.put(id, future);
      return future;
    });
  }

  private void givenReady(long from, long to) {
    for (long id = from; id <= to; id++) {
      ready.add(id);
    }
  }

  /** The copy of {@code id} finishes: no longer READY, and out of flight. */
  private void finish(long id) {
    ready.remove(id);
    copying.remove(id).complete(DONE);
  }

  private FileCopyTaskSchedulerService service(int batch, long start) {
    return new FileCopyTaskSchedulerService(items, fileCopyService, batch, bound -> start);
  }

  @Test
  @DisplayName("hands out READY files from its random start, at most the batch in flight")
  void handsOutFromRandomStart() {
    givenReady(1, 10);
    FileCopyTaskSchedulerService service = service(3, 5);

    assertThat(service.findAndCopyReadyFiles()).isEqualTo(3);
    assertThat(handedOut).containsExactly(5L, 6L, 7L);

    // Nothing finished: the batch is still in flight, so nothing more goes out.
    assertThat(service.findAndCopyReadyFiles()).isZero();
  }

  @Test
  @DisplayName("hands out more as copies finish, carrying on past the last file handed out")
  void carriesOn() {
    givenReady(1, 10);
    FileCopyTaskSchedulerService service = service(3, 5);
    service.findAndCopyReadyFiles();

    finish(5);
    finish(6);
    assertThat(service.findAndCopyReadyFiles()).isEqualTo(2);

    assertThat(handedOut).containsExactly(5L, 6L, 7L, 8L, 9L);
  }

  @Test
  @DisplayName("wraps round to the lowest READY files past the end, never one still in flight")
  void wraps() {
    givenReady(1, 6);
    FileCopyTaskSchedulerService service = service(3, 5);
    service.findAndCopyReadyFiles();
    assertThat(handedOut).containsExactly(5L, 6L, 1L);

    finish(5);
    finish(6);
    service.findAndCopyReadyFiles();

    // 1 is still being copied, so the wrap takes 2 and 3.
    assertThat(handedOut).containsExactly(5L, 6L, 1L, 2L, 3L);
  }

  @Test
  @DisplayName("hands a file out again once its outcome could not be saved")
  void retriesUnsaved() {
    givenReady(1, 1);
    FileCopyTaskSchedulerService service = service(3, 1);
    service.findAndCopyReadyFiles();

    copying.remove(1L).completeExceptionally(new IllegalStateException("database gone"));
    service.findAndCopyReadyFiles();

    assertThat(handedOut).containsExactly(1L, 1L);
  }

  @Test
  @DisplayName("stops handing out when the executor is full, and keeps the refused file READY")
  void stopsWhenFull() {
    givenReady(1, 3);
    when(fileCopyService.copyFile(2L)).thenThrow(new TaskRejectedException("full"));
    FileCopyTaskSchedulerService service = service(3, 1);

    assertThat(service.findAndCopyReadyFiles()).isEqualTo(1);
    verify(fileCopyService, never()).copyFile(3L);

    // The refused file was not left in flight: it goes out on a later firing.
    doAnswer(call -> new CompletableFuture<>()).when(fileCopyService).copyFile(2L);
    service.findAndCopyReadyFiles();
    verify(fileCopyService, times(2)).copyFile(2L);
  }

  @Test
  @DisplayName("does nothing while the job has no READY files")
  void nothingReady() {
    assertThat(service(3, 0).findAndCopyReadyFiles()).isZero();
    verify(fileCopyService, never()).copyFile(anyLong());
  }

  @Test
  @DisplayName("fills, and counts the files not copied only once nothing is READY")
  void fillsAndReports() {
    when(items.countByStatus(JOB)).thenReturn(List.of());
    when(items.countByJobNameAndStatusAndUpdateTimestampAfter(eq(JOB), eq(DONE), any()))
        .thenReturn(0L);
    FileCopyTaskSchedulerService service = service(3, 0);

    service.fillAndReport();

    verify(items).insertFileCopyItems();
    verify(items).countFilesNotCopied();
  }
}
