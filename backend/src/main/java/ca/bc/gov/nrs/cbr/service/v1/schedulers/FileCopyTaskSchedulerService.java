package ca.bc.gov.nrs.cbr.service.v1.schedulers;

import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.service.v1.FileCopyService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * What {@code FileCopyScheduler}'s two steps do — EDUC-STUDENT-ASSESSMENT-API's
 * {@code EventTaskSchedulerAsyncService} for {@code FILE_COPY}.
 *
 * <p><b>Handing out files.</b> As EDUC hands loaded students to NATS, this hands {@code READY}
 * files to {@link FileCopyService#copyFile}, which runs on the {@code fileCopyTaskExecutor}, and
 * returns at once — see {@link ReadyItemsHandOut} for the guards against handing a file out twice.
 * {@link FileCopyService}'s check against the store makes a second hand-out cheap when it happens.
 *
 * <p>DELETE-AFTER-FILE-MIGRATION, with {@link FileCopyService}.
 */
@Service
public class FileCopyTaskSchedulerService {

  private static final Logger log = LoggerFactory.getLogger(FileCopyTaskSchedulerService.class);

  private static final String JOB_NAME = FileCopyService.JOB_NAME;

  private final BatchJobItemRepository items;
  private final ReadyItemsHandOut handOut;

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
    this.handOut = new ReadyItemsHandOut(JOB_NAME, items, fileCopyService::copyFile,
        numberOfFilesToProcess, randomStart);
  }

  /**
   * Hands out up to {@code number.files.process.copy} less those in flight; returns the number
   * handed out.
   */
  public int findAndCopyReadyFiles() {
    return handOut.handOutReady();
  }

  /**
   * Lists the files added since, then logs one progress line — and, once nothing is
   * {@code READY}, the gate line: {@code PASS} only with no errors and every file with bytes
   * {@code DONE}.
   */
  public void fillAndReport() {
    int added = items.insertFileCopyItems();
    JobProgress progress = JobProgress.report(items, JOB_NAME, added);
    if (progress.ready() == 0) {
      long notCopied = items.countFilesNotCopied();
      log.info("{} GATE: {} — DONE={}, ERROR={}, files with bytes not copied={}", JOB_NAME,
          progress.error() == 0 && notCopied == 0 ? "PASS" : "FAIL", progress.done(),
          progress.error(), notCopied);
    }
  }
}
