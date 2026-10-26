package ca.bc.gov.nrs.cbr.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link ClamAvProperties} for {@code ClamAvClient} and {@code VirusScanner}. */
@Configuration
@EnableConfigurationProperties(ClamAvProperties.class)
public class ClamAvConfiguration {
}
