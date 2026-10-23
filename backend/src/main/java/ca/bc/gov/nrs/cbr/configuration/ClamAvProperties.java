package ca.bc.gov.nrs.cbr.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cbr.clamav} — the ClamAV daemon (clamd) every uploaded document is scanned by.
 *
 * <p>The shared clamd in {@code bd657f-<env>}, which nr-frep uses too. Always on when deployed; the
 * local profile turns it off, as there is no clamd locally. There is no fail-open switch: when
 * clamd cannot answer, the upload is refused.
 *
 * @param enabled        whether uploads are scanned; off, no socket is opened at all
 * @param host           clamd's host — a cross-namespace service name,
 *                       {@code <service>.bd657f-<env>.svc.cluster.local}
 * @param port           clamd's port
 * @param connectTimeout how long to wait to connect
 * @param readTimeout    how long to wait for each read, the verdict included
 */
@ConfigurationProperties(prefix = "cbr.clamav")
public record ClamAvProperties(
    @DefaultValue("true") boolean enabled,
    String host,
    @DefaultValue("3310") int port,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("30s") Duration readTimeout) {
}
