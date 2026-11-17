package ca.bc.gov.nrs.cbr.service.v1.schedulers;

import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.service.v1.FileCopyService;
import ca.bc.gov.nrs.cbr.service.v1.FilePurgeService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * What {@code FilePurgeScheduler}'s two steps do: hand {@code READY} files to
 * {@link FilePurgeService#purgeFile}, as {@code FILE_COPY} hands out its own (see
 * {@link ReadyItemsHandOut}); and list the files to purge, then log progress and the gate.
 *
 * <p><b>Copy first.</b> The fill adds nothing until {@code FILE_COPY}'s gate would pass — no
 * {@code READY} or {@code ERROR} copy items and no file with bytes left uncopied — and then lists
 * only the files the copy has {@code DONE}. While it waits it says so, with the copy's counts.
 * Files already listed are still purged: each was copied, and each is proved again before it is
 * emptied.
 *
 * <p>DELETE-AFTER-FILE-MIGRATION, with {@link FilePurgeService}.
 */
@Service
public class FilePurgeTaskSchedulerService {

  private static final Logger log = LoggerFactory.getLogger(FilePurgeTaskSchedulerService.class);

  private static final String JOB_NAME = FilePurgeService.JOB_NAME;

  private final BatchJobItemRepository items;
  private final ReadyItemsHandOut handOut;

  @Autowired
  public FilePurgeTaskSchedulerService(BatchJobItemRepository items,
      FilePurgeService filePurgeService,
      @Value("${number.files.process.purge}") int numberOfFilesToProcess) {
    this(items, filePurgeService, numberOfFilesToProcess,
        bound -> ThreadLocalRandom.current().nextLong(bound + 1));
  }

  FilePurgeTaskSchedulerService(BatchJobItemRepository items, FilePurgeService filePurgeService,
      int numberOfFilesToProcess, LongUnaryOperator randomStart) {
    this.items = items;
    this.handOut = new ReadyItemsHandOut(JOB_NAME, items, filePurgeService::purgeFile,
        numberOfFilesToProcess, randomStart);
  }

  /**
   * Hands out up to {@code number.files.process.purge} less those in flight; returns the number
   * handed out.
   */
  public int findAndPurgeReadyFiles() {
    return handOut.handOutReady();
  }

  /**
   * Lists the files to purge once the copy's gate would pass, then logs one progress line — and,
   * once nothing is {@code READY}, the gate line: {@code PASS} only with no errors and no file
   * left with bytes.
   *
   * @return the files listed now
   */
  public int fillAndReport() {
    JobProgress copy = JobProgress.of(items, FileCopyService.JOB_NAME);
    long notCopied = copy.ready() == 0 && copy.error() == 0 ? items.countFilesNotCopied() : -1;
    boolean copied = notCopied == 0;
    int added = 0;
    if (copied) {
      added = items.insertFilePurgeItems();
    } else {
      log.info("{}: waiting for {}'s gate — READY={}, ERROR={}{}; no files listed", JOB_NAME,
          FileCopyService.JOB_NAME, copy.ready(), copy.error(),
          notCopied < 0 ? "" : ", files with bytes not copied=" + notCopied);
    }
    JobProgress progress = JobProgress.report(items, JOB_NAME, added);
    if (copied && progress.ready() == 0) {
      long withBytes = items.countFilesWithBytes();
      log.info("{} GATE: {} — DONE={}, ERROR={}, files with bytes left={}", JOB_NAME,
          progress.error() == 0 && withBytes == 0 ? "PASS" : "FAIL", progress.done(),
          progress.error(), withBytes);
    }
    return added;
  }
}
