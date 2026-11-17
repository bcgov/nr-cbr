package ca.bc.gov.nrs.cbr.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.service.v1.schedulers.FileCopyTaskSchedulerService;
import ca.bc.gov.nrs.cbr.service.v1.schedulers.FilePurgeTaskSchedulerService;
import java.io.IOException;
import java.util.List;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * The schedulers against the real {@code application.yml}: every placeholder in their annotations
 * resolves; deployed, the file copy's two crons are on and both purges' are off; the local profile
 * schedules nothing; and set crons schedule all five behind the ShedLock proxy.
 */
class SchedulersConfigurationTest {

  @Configuration
  @EnableScheduling
  @EnableSchedulerLock(defaultLockAtMostFor = "1s")
  static class Scheduling {
  }

  private static final String PROFILE = "spring.config.activate.on-profile";

  /**
   * The real {@code application.yml}'s documents: the plain ones, and with {@code local} the local
   * profile's ahead of them, as Boot ranks an active profile's document above the rest.
   */
  private static ApplicationContextRunner withApplicationYml(boolean local) {
    return new ApplicationContextRunner().withInitializer(started -> {
      List<PropertySource<?>> documents;
      try {
        documents = new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
      } catch (IOException missing) {
        throw new IllegalStateException(missing);
      }
      MutablePropertySources sources = started.getEnvironment().getPropertySources();
      documents.stream()
          .filter(document -> "local".equals(document.getProperty(PROFILE)))
          .filter(document -> local)
          .forEach(sources::addLast);
      documents.stream()
          .filter(document -> document.getProperty(PROFILE) == null)
          .forEach(sources::addLast);
    });
  }

  private final ApplicationContextRunner context = configured(withApplicationYml(false));

  private static ApplicationContextRunner configured(ApplicationContextRunner runner) {
    return runner
      .withBean(LockProvider.class, () -> mock(LockProvider.class))
      .withBean(BatchJobItemRepository.class, () -> mock(BatchJobItemRepository.class))
      .withBean(FileCopyTaskSchedulerService.class,
          () -> mock(FileCopyTaskSchedulerService.class))
      .withBean(FilePurgeTaskSchedulerService.class,
          () -> mock(FilePurgeTaskSchedulerService.class))
      .withUserConfiguration(Scheduling.class, FileCopyScheduler.class, FilePurgeScheduler.class,
          PurgeBatchJobItemsScheduler.class);
  }

  private static int scheduledTasks(ApplicationContext started) {
    return started.getBeansOfType(ScheduledTaskHolder.class).values().stream()
        .mapToInt(holder -> holder.getScheduledTasks().size()).sum();
  }

  @Test
  @DisplayName("deployed, runs the file copy's fill and copy — not the file purge, nor the "
      + "work-item purge")
  void fileCopyOnByDefault() {
    context.run(started -> {
      assertThat(started).hasNotFailed();
      assertThat(started.getEnvironment().getProperty("scheduled.jobs.fill.file.copy.items.cron"))
          .isEqualTo("0 */15 * * * *");
      assertThat(started.getEnvironment().getProperty("scheduled.jobs.copy.files.cron"))
          .isEqualTo("0/2 * * * * *");
      assertThat(scheduledTasks(started)).isEqualTo(2);
    });
  }

  @Test
  @DisplayName("schedules nothing under the local profile")
  void offLocally() {
    configured(withApplicationYml(true)).run(started -> {
      assertThat(started).hasNotFailed();
      assertThat(scheduledTasks(started)).isZero();
    });
  }

  @Test
  @DisplayName("an env var of \"-\" stops the copy in one environment")
  void offByEnvironment() {
    context.withPropertyValues("SCHEDULED_JOBS_FILL_FILE_COPY_ITEMS_CRON=-",
            "SCHEDULED_JOBS_COPY_FILES_CRON=-")
        .run(started -> assertThat(scheduledTasks(started)).isZero());
  }

  @Test
  @DisplayName("schedules the copy's two, the file purge's two and the work-item purge, each "
      + "behind its lock, once their crons are set")
  void onWhenCronsSet() {
    context.withPropertyValues(
            "SCHEDULED_JOBS_FILL_FILE_COPY_ITEMS_CRON=0 */15 * * * *",
            "SCHEDULED_JOBS_COPY_FILES_CRON=0/2 * * * * *",
            "SCHEDULED_JOBS_FILL_FILE_PURGE_ITEMS_CRON=0 */15 * * * *",
            "SCHEDULED_JOBS_PURGE_FILES_CRON=0/2 * * * * *",
            "SCHEDULED_JOBS_PURGE_BATCH_JOB_ITEMS_CRON=0 0 2 * * *",
            "PURGE_BATCH_JOB_ITEMS_JOBS=FILE_COPY,FILE_PURGE")
        .run(started -> {
          assertThat(started).hasNotFailed();
          assertThat(scheduledTasks(started)).isEqualTo(5);
          assertThat(AopUtils.isAopProxy(started.getBean(FileCopyScheduler.class))).isTrue();
          assertThat(AopUtils.isAopProxy(started.getBean(FilePurgeScheduler.class))).isTrue();
          assertThat(AopUtils.isAopProxy(started.getBean(PurgeBatchJobItemsScheduler.class)))
              .isTrue();
        });
  }
}
