package ca.bc.gov.nrs.cbr;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CBR (Corporate Bridge Register) API.
 *
 * <p>An OAuth 2.0 resource server over FAM/Cognito access tokens, reading
 * and writing the shared Oracle {@code THE} schema through the legacy {@code CBR_*} PL/SQL packages.
 * Schema objects live in {@code bcgov-c/nr-mof-db}, not here.
 *
 * <p>Scheduled tasks run under ShedLock locks, after EDUC-STUDENT-ASSESSMENT-API; see the
 * {@code schedulers} package. Every task's cron defaults to {@code -}, "never".
 */
@SpringBootApplication
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "1s")
public class CbrApiApplication extends SpringBootServletInitializer {

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    return builder.sources(CbrApiApplication.class);
  }

  public static void main(String[] args) {
    SpringApplication.run(CbrApiApplication.class, args);
  }

  /**
   * Lock provider for distributed locks, so that only one pod runs a scheduled task at a time —
   * locks in {@code THE.CBR_SHEDLOCK}, timed by Oracle's clock rather than the pods'.
   */
  @Bean
  public LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
    return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
        .withJdbcTemplate(jdbcTemplate)
        .withTableName("THE.CBR_SHEDLOCK")
        .usingDbTime()
        .build());
  }
}
