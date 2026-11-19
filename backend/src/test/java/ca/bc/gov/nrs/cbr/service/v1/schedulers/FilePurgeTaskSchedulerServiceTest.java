package ca.bc.gov.nrs.cbr.service.v1.schedulers;

import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.DONE;
import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.ERROR;
import static ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository.StatusCount;
import ca.bc.gov.nrs.cbr.service.v1.FileCopyService;
import ca.bc.gov.nrs.cbr.service.v1.FilePurgeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code FILE_PURGE}'s scheduling: it lists nothing until {@code FILE_COPY}'s gate would pass, and
 * hands out its READY files as the copy does (the hand-out's own rules are proved by
 * {@code FileCopyTaskSchedulerServiceTest}).
 */
class FilePurgeTaskSchedulerServiceTest {

  private static final String COPY = FileCopyService.JOB_NAME;
  private static final String PURGE = FilePurgeService.JOB_NAME;

  private BatchJobItemRepository items;
  private FilePurgeService filePurgeService;
  private final List<Long> handedOut = new ArrayList<>();

  @BeforeEach
  void setUp() {
    items = mock(BatchJobItemRepository.class);
    filePurgeService = mock(FilePurgeService.class);
    when(filePurgeService.purgeFile(anyLong())).thenAnswer(call -> {
      handedOut.add(call.getArgument(0));
      return new CompletableFuture<BatchJobItemStatus>();
    });
    when(items.countByStatus(anyString())).thenReturn(List.of());
    when(items.countByJobNameAndStatusAndUpdateTimestampAfter(any(), eq(DONE), any()))
        .thenReturn(0L);
  }

  private static StatusCount count(BatchJobItemStatus status, long total) {
    return new StatusCount() {
      @Override
      public BatchJobItemStatus getStatus() {
        return status;
      }

      @Override
      public long getTotal() {
        return total;
      }
    };
  }

  private FilePurgeTaskSchedulerService service() {
    return new FilePurgeTaskSchedulerService(items, filePurgeService, 3, bound -> 0);
  }

  @Test
  @DisplayName("lists nothing while the copy has READY or ERROR files")
  void waitsForCopy() {
    when(items.countByStatus(COPY)).thenReturn(List.of(count(READY, 2), count(DONE, 8)));
    assertThat(service().fillAndReport()).isZero();

    when(items.countByStatus(COPY)).thenReturn(List.of(count(ERROR, 1), count(DONE, 9)));
    assertThat(service().fillAndReport()).isZero();

    verify(items, never()).insertFilePurgeItems();
    verify(items, never()).countFilesWithBytes();
  }

  @Test
  @DisplayName("lists nothing while a file with bytes has not been copied")
  void waitsForUncopiedFiles() {
    when(items.countByStatus(COPY)).thenReturn(List.of(count(DONE, 10)));
    when(items.countFilesNotCopied()).thenReturn(1L);

    assertThat(service().fillAndReport()).isZero();

    verify(items, never()).insertFilePurgeItems();
  }

  @Test
  @DisplayName("lists the copied files once the copy's gate would pass, and counts what is left "
      + "with bytes once nothing is READY")
  void listsOnceCopied() {
    when(items.countByStatus(COPY)).thenReturn(List.of(count(DONE, 10)));
    when(items.countFilesNotCopied()).thenReturn(0L);
    when(items.insertFilePurgeItems()).thenReturn(10);
    when(items.countByStatus(PURGE)).thenReturn(List.of(count(READY, 10)));

    assertThat(service().fillAndReport()).isEqualTo(10);
    verify(items, never()).countFilesWithBytes();

    // All purged: nothing READY, so the gate is counted.
    when(items.countByStatus(PURGE)).thenReturn(List.of(count(DONE, 10)));
    when(items.insertFilePurgeItems()).thenReturn(0);
    service().fillAndReport();
    verify(items).countFilesWithBytes();
  }

  @Test
  @DisplayName("hands out the READY files to the purge")
  void handsOut() {
    when(items.findHighestItemId(PURGE, READY)).thenReturn(Optional.of(5L));
    when(items.findItemIds(eq(PURGE), eq(READY), anyLong(), anyInt()))
        .thenReturn(List.of(1L, 2L, 3L));

    assertThat(service().findAndPurgeReadyFiles()).isEqualTo(3);
    assertThat(handedOut).containsExactly(1L, 2L, 3L);
  }
}
