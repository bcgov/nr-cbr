package ca.bc.gov.nrs.cbr.schedulers;

import ca.bc.gov.nrs.cbr.service.v1.schedulers.FileCopyTaskSchedulerService;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code FILE_COPY}'s two steps, as EDUC-STUDENT-ASSESSMENT-API's {@code EventTaskScheduler}: each
 * on a cron, under a ShedLock lock so that one pod at a time runs it, delegating at once.
 *
 * <p>Both crons default to {@code -}, Spring's "never": the copy is switched on by setting them
 * with {@code oc set env}, not in the deployment template, so the next deploy switches it off.
 *
 * <p>DELETE-AFTER-FILE-MIGRATION.
 */
@Component
public class FileCopyScheduler {

  private final FileCopyTaskSchedulerService taskSchedulerService;

  public FileCopyScheduler(FileCopyTaskSchedulerService taskSchedulerService) {
    this.taskSchedulerService = taskSchedulerService;
  }

  /** Lists the files added since, and logs the progress and gate lines. */
  @Scheduled(cron = "${scheduled.jobs.fill.file.copy.items.cron}")
  @SchedulerLock(name = "FILL_FILE_COPY_ITEMS",
      lockAtLeastFor = "${scheduled.jobs.fill.file.copy.items.cron.lockAtLeastFor}",
      lockAtMostFor = "${scheduled.jobs.fill.file.copy.items.cron.lockAtMostFor}")
  public void fillFileCopyItems() {
    LockAssert.assertLocked();
    taskSchedulerService.fillAndReport();
  }

  /** Hands the next READY files to the copy executor. */
  @Scheduled(cron = "${scheduled.jobs.copy.files.cron}")
  @SchedulerLock(name = "COPY_FILES",
      lockAtLeastFor = "${scheduled.jobs.copy.files.cron.lockAtLeastFor}",
      lockAtMostFor = "${scheduled.jobs.copy.files.cron.lockAtMostFor}")
  public void copyFiles() {
    LockAssert.assertLocked();
    taskSchedulerService.findAndCopyReadyFiles();
  }
}
