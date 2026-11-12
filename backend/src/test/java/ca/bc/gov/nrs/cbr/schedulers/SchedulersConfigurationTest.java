package ca.bc.gov.nrs.cbr.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.service.v1.schedulers.FileCopyTaskSchedulerService;
import java.io.IOException;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * The schedulers against the real {@code application.yml}: every placeholder in their annotations
 * resolves, the default crons ("-") schedule nothing, and set crons schedule all three behind the
 * ShedLock proxy.
 */
class SchedulersConfigurationTest {

  @Configuration
  @EnableScheduling
  @EnableSchedulerLock(defaultLockAtMostFor = "1s")
  static class Scheduling {
  }

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withInitializer(started -> {
        try {
          new YamlPropertySourceLoader()
              .load("application.yml", new ClassPathResource("application.yml"))
              .forEach(started.getEnvironment().getPropertySources()::addLast);
        } catch (IOException missing) {
          throw new IllegalStateException(missing);
        }
      })
      .withBean(LockProvider.class, () -> mock(LockProvider.class))
      .withBean(BatchJobItemRepository.class, () -> mock(BatchJobItemRepository.class))
      .withBean(FileCopyTaskSchedulerService.class,
          () -> mock(FileCopyTaskSchedulerService.class))
      .withUserConfiguration(Scheduling.class, FileCopyScheduler.class,
          PurgeBatchJobItemsScheduler.class);

  private static int scheduledTasks(ApplicationContext started) {
    return started.getBeansOfType(ScheduledTaskHolder.class).values().stream()
        .mapToInt(holder -> holder.getScheduledTasks().size()).sum();
  }

  @Test
  @DisplayName("schedules nothing by default: every cron is \"-\"")
  void offByDefault() {
    context.run(started -> {
      assertThat(started).hasNotFailed();
      assertThat(scheduledTasks(started)).isZero();
    });
  }

  @Test
  @DisplayName("schedules the fill, the copy and the purge, each behind its lock, once their crons "
      + "are set")
  void onWhenCronsSet() {
    context.withPropertyValues(
            "SCHEDULED_JOBS_FILL_FILE_COPY_ITEMS_CRON=0 */15 * * * *",
            "SCHEDULED_JOBS_COPY_FILES_CRON=0/2 * * * * *",
            "SCHEDULED_JOBS_PURGE_BATCH_JOB_ITEMS_CRON=0 0 2 * * *",
            "PURGE_BATCH_JOB_ITEMS_JOBS=FILE_COPY,FILE_PURGE")
        .run(started -> {
          assertThat(started).hasNotFailed();
          assertThat(scheduledTasks(started)).isEqualTo(3);
          assertThat(AopUtils.isAopProxy(started.getBean(FileCopyScheduler.class))).isTrue();
          assertThat(AopUtils.isAopProxy(started.getBean(PurgeBatchJobItemsScheduler.class)))
              .isTrue();
        });
  }
}
