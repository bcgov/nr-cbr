package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.client.CssApiClient;
import ca.bc.gov.nrs.cbr.client.CssApiClient.CssUser;
import ca.bc.gov.nrs.cbr.security.CbrRoles;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionReviewerResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Inspection Search's Reviewed By list: everyone FAM has granted {@link CbrRoles#INSPECTION_REVIEWER},
 * read from CSS — legacy's active reviewers, now that the role is what an active row was
 * ({@code cbr-inspection-reviewer.local.md} §10).
 *
 * <p>A reviewer who has not reviewed yet is listed all the same, as legacy listed one; searching on
 * them finds nothing. Nothing is written for them: their reviewer row comes with their first review.
 *
 * <p>Held for {@link #FRESH} rather than for the life of the pod, as the code tables are, because a
 * FAM grant should show up the same morning. When CSS cannot be asked, the last list it gave is
 * kept; with none, the list is empty and the search works without it.
 */
@Service
public class InspectionReviewersService {

  private static final Logger log = LoggerFactory.getLogger(InspectionReviewersService.class);

  static final Duration FRESH = Duration.ofMinutes(5);

  private final CssApiClient css;
  private final Clock clock;

  /** The list and when it goes stale, swapped as one so a reader never sees half an update. */
  private final AtomicReference<Held> held = new AtomicReference<>(new Held(List.of(), Instant.MIN));

  @Autowired
  public InspectionReviewersService(CssApiClient css) {
    this(css, Clock.systemUTC());
  }

  InspectionReviewersService(CssApiClient css, Clock clock) {
    this.css = css;
    this.clock = clock;
  }

  /** The role's holders by last name, then first, each once. */
  public List<InspectionReviewerResponse> getInspectionReviewers() {
    if (!css.isConfigured()) {
      return List.of();
    }
    Instant now = clock.instant();
    Held current = held.get();
    if (now.isBefore(current.expires())) {
      return current.reviewers();
    }
    try {
      List<InspectionReviewerResponse> read =
          options(css.usersWithRole(CbrRoles.INSPECTION_REVIEWER));
      held.set(new Held(read, now.plus(FRESH)));
      log.info("Read {} inspection reviewers from CSS", read.size());
      return read;
    } catch (RestClientResponseException ex) {
      // CSS's error says what it refused — a parameter, the role. One line, cut short.
      log.warn("Could not read the inspection reviewers from CSS ({} {}); keeping the {} held",
          ex.getStatusCode().value(), oneLine(ex.getResponseBodyAsString()),
          current.reviewers().size());
    } catch (RestClientException ex) {
      log.warn("Could not read the inspection reviewers from CSS ({}); keeping the {} held",
          ex.getClass().getSimpleName(), current.reviewers().size());
    }
    return current.reviewers();
  }

  private record Held(List<InspectionReviewerResponse> reviewers, Instant expires) {}

  private static List<InspectionReviewerResponse> options(List<CssUser> users) {
    Map<String, CssUser> byUserId = new LinkedHashMap<>();
    for (CssUser user : users) {
      String userId = user.userId();
      if (!userId.isEmpty()) {
        byUserId.putIfAbsent(userId.toUpperCase(Locale.ROOT), user);
      }
    }
    return byUserId.values().stream()
        .sorted(Comparator.comparing((CssUser user) -> sortable(user.lastName()))
            .thenComparing(user -> sortable(user.firstName())))
        .map(user -> new InspectionReviewerResponse(user.userId(), displayName(user)))
        .toList();
  }

  /** "Last, First", as legacy's {@code InspectionReviewerDTO.getDisplayName}; else the user ID. */
  static String displayName(CssUser user) {
    String last = StringUtils.trimToEmpty(user.lastName());
    String first = StringUtils.trimToEmpty(user.firstName());
    if (last.isEmpty() || first.isEmpty()) {
      String either = last + first;
      return either.isEmpty() ? user.userId() : either;
    }
    return last + ", " + first;
  }

  /** The text with line breaks and other control characters dropped, at most 300 characters. */
  static String oneLine(String text) {
    String flat = StringUtils.defaultString(text).replaceAll("\\p{Cntrl}", " ").strip();
    return StringUtils.abbreviate(flat, 300);
  }

  private static String sortable(String name) {
    return StringUtils.trimToEmpty(name).toLowerCase(Locale.ROOT);
  }
}
