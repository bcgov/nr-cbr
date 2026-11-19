package ca.bc.gov.nrs.cbr.schedulers;

import ca.bc.gov.nrs.cbr.service.v1.schedulers.FilePurgeTaskSchedulerService;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code FILE_PURGE}'s two steps, as {@link FileCopyScheduler}'s: each on a cron, under a ShedLock
 * lock so that one pod at a time runs it, delegating at once.
 *
 * <p>Both crons default to {@code -}, Spring's "never": emptying BLOBs cannot be undone, so it runs
 * only where and when someone switches it on — {@code oc set env} on that environment's backend.
 *
 * <p>DELETE-AFTER-FILE-MIGRATION.
 */
@Component
public class FilePurgeScheduler {

  private final FilePurgeTaskSchedulerService taskSchedulerService;

  public FilePurgeScheduler(FilePurgeTaskSchedulerService taskSchedulerService) {
    this.taskSchedulerService = taskSchedulerService;
  }

  /** Lists the copied files once the copy's gate would pass, and logs progress and the gate. */
  @Scheduled(cron = "${scheduled.jobs.fill.file.purge.items.cron}")
  @SchedulerLock(name = "FILL_FILE_PURGE_ITEMS",
      lockAtLeastFor = "${scheduled.jobs.fill.file.purge.items.cron.lockAtLeastFor}",
      lockAtMostFor = "${scheduled.jobs.fill.file.purge.items.cron.lockAtMostFor}")
  public void fillFilePurgeItems() {
    LockAssert.assertLocked();
    taskSchedulerService.fillAndReport();
  }

  /** Hands the next READY files to the purge executor. */
  @Scheduled(cron = "${scheduled.jobs.purge.files.cron}")
  @SchedulerLock(name = "PURGE_FILES",
      lockAtLeastFor = "${scheduled.jobs.purge.files.cron.lockAtLeastFor}",
      lockAtMostFor = "${scheduled.jobs.purge.files.cron.lockAtMostFor}")
  public void purgeFiles() {
    LockAssert.assertLocked();
    taskSchedulerService.findAndPurgeReadyFiles();
  }
}
