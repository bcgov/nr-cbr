package ca.bc.gov.nrs.cbr.configuration;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The executors {@code @Async} methods run on, after EDUC-STUDENT-ASSESSMENT-API's.
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
}
