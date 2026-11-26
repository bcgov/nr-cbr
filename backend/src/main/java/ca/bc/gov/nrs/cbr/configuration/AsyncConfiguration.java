package ca.bc.gov.nrs.cbr.configuration;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The executors {@code @Async} methods run on.
 *
 * <p>Defining an {@code Executor} bean makes Spring Boot drop its {@code applicationTaskExecutor};
 * CBR has no async MVC or unnamed {@code @Async} that would use it.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

  /**
   * {@code FileCopyService.copyFile}: {@code threads.file.copy} files at once, each holding one
   * file (10 MB at most) in memory and briefly a DB connection. The queue holds what the scheduler
   * hands out per firing, which it caps at {@code number.files.process.copy} in flight.
   */
  @Bean(name = "fileCopyTaskExecutor")
  public Executor fileCopyTaskExecutor(@Value("${threads.file.copy}") int threads,
      @Value("${number.files.process.copy}") int numberOfFilesToProcess) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("async-file-copy-executor-");
    executor.setCorePoolSize(threads);
    executor.setMaxPoolSize(threads);
    executor.setQueueCapacity(numberOfFilesToProcess);
    return executor;
  }

  /**
   * {@code FilePurgeService.purgeFile}: {@code threads.file.purge} files at once, each holding one
   * file (10 MB at most) in memory and a DB connection — its row locked — while its MD5 is checked
   * against the object store. The queue holds what the scheduler hands out per firing, capped at
   * {@code number.files.process.purge} in flight. DELETE-AFTER-FILE-MIGRATION.
   */
  @Bean(name = "filePurgeTaskExecutor")
  public Executor filePurgeTaskExecutor(@Value("${threads.file.purge}") int threads,
      @Value("${number.files.process.purge}") int numberOfFilesToProcess) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("async-file-purge-executor-");
    executor.setCorePoolSize(threads);
    executor.setMaxPoolSize(threads);
    executor.setQueueCapacity(numberOfFilesToProcess);
    return executor;
  }

  /**
   * {@code RoadNameService.namesOf}: the batches of a road-name lookup, sent to the LRM Ops API a
   * few at a time. One pool for the whole application, so however many reports run at once, at
   * most {@code ca.bc.gov.nrs.lrmops-api.lookup-threads} lookups reach the API together — it has a
   * pool of five database connections. The queue is unbounded: a batch waits rather than fails.
   */
  @Bean(name = "roadNameLookupExecutor")
  public Executor roadNameLookupExecutor(
      @Value("${ca.bc.gov.nrs.lrmops-api.lookup-threads:4}") int threads) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("road-name-lookup-");
    executor.setCorePoolSize(threads);
    executor.setMaxPoolSize(threads);
    return executor;
  }
}
