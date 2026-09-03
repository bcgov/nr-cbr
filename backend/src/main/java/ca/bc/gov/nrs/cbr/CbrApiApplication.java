package ca.bc.gov.nrs.cbr;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * CBR (Corporate Bridge Register) API.
 *
 * <p>Structure follows nr-frep: an OAuth 2.0 resource server over FAM/Cognito access tokens, reading
 * and writing the shared Oracle {@code THE} schema through the legacy {@code CBR_*} PL/SQL packages.
 * Schema objects live in {@code bcgov-c/nr-mof-db}, not here.
 */
@SpringBootApplication
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class CbrApiApplication extends SpringBootServletInitializer {

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    return builder.sources(CbrApiApplication.class);
  }

  public static void main(String[] args) {
    SpringApplication.run(CbrApiApplication.class, args);
  }
}
