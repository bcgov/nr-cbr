package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.client.LrmopsApiClient;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSummary;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.SectionKey;
import ca.bc.gov.nrs.cbr.exception.RoadLookupUnavailableException;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

/**
 * Road names for the search screens, read live from LRMOPS through the LRM Ops API rather than
 * from CBR's copy ({@code CBR_ROAD_SECTION_VW}).
 *
 * <p>Three uses:
 * <ul>
 *   <li>{@link #suggestions}: the Forest Service Road pick list on Site, Structure and Inspection
 *       Search.</li>
 *   <li>{@link #sectionsNamed}: the filter. The user picks a name, and the search matches sites on
 *       the sections LRMOPS gives that name, exactly.</li>
 *   <li>{@link #namesOf}: the Forest Service Road column, for one page of results at a time.</li>
 * </ul>
 *
 * <p><b>Proof of concept.</b> Each call logs its size and time at INFO ({@code road-name} in the
 * message) so the cost can be read off DEV and TEST logs.
 */
@Service
public class RoadNameService {

  private static final Logger log = LoggerFactory.getLogger(RoadNameService.class);

  /** Shorter terms are not looked up — as the Designated Maintainer lookup. */
  static final int MIN_TERM_LENGTH = 3;

  /** Names in the pick list. */
  static final int SUGGESTIONS = 20;

  /** Sections per lookup call. The API takes up to 1,000; smaller keeps each call quick. */
  static final int LOOKUP_BATCH = 500;

  private final LrmopsApiClient lrmops;

  public RoadNameService(LrmopsApiClient lrmops) {
    this.lrmops = lrmops;
  }

  /**
   * Road names containing {@code term}, those starting with it first, for the pick list. Empty
   * for a term under {@value #MIN_TERM_LENGTH} characters, without asking.
   *
   * @throws RoadLookupUnavailableException if the API cannot answer
   */
  public List<String> suggestions(String term) {
    if (term == null || term.trim().length() < MIN_TERM_LENGTH) {
      return List.of();
    }
    long start = System.nanoTime();
    List<String> names = call(() -> lrmops.getRoadNames(term, SUGGESTIONS));
    log.info("road-name suggestions: term length {}, {} name(s), {} ms",
        term.trim().length(), names.size(), millisSince(start));
    return names;
  }

  /**
   * The road sections LRMOPS names exactly {@code roadName}, for a search filtering on it. Empty
   * when the name is blank — no filter, as opposed to an empty list, which matches nothing.
   *
   * <p>Throws rather than answering empty when the API is down: an empty answer would read as "no
   * sites on that road", and no answer as "every site".
   *
   * @throws RoadLookupUnavailableException if the API cannot answer
   */
  public Optional<List<RoadSectionKey>> sectionsNamed(String roadName) {
    if (!StringUtils.hasText(roadName)) {
      return Optional.empty();
    }
    long start = System.nanoTime();
    List<RoadSectionKey> keys = call(() -> lrmops.getRoadSectionsNamed(roadName)).stream()
        .map(key -> RoadSectionKey.of(key.roadId(), key.sectionId()))
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    log.info("road-name filter: {} section(s) named, {} ms", keys.size(), millisSince(start));
    return Optional.of(keys);
  }

  /**
   * The names of the given sections, keyed as given (normalized, {@link RoadSectionKey#of}). A
   * section LRMOPS does not have, or has no name for, is absent.
   *
   * <p><b>Never throws.</b> The names are a column of a results page that is otherwise CBR's own,
   * so the API being down blanks the column (logged) rather than failing the search.
   */
  public Map<RoadSectionKey, String> namesOf(Collection<RoadSectionKey> sections) {
    List<RoadSectionKey> wanted = new ArrayList<>(new LinkedHashSet<>(
        sections.stream().filter(Objects::nonNull).toList()));
    if (wanted.isEmpty()) {
      return Map.of();
    }
    if (!lrmops.isConfigured()) {
      log.warn("road-name lookup skipped: the LRM Ops API is not configured");
      return Map.of();
    }

    long start = System.nanoTime();
    Map<RoadSectionKey, String> names = new HashMap<>();
    int calls = 0;
    try {
      for (int from = 0; from < wanted.size(); from += LOOKUP_BATCH) {
        List<SectionKey> batch = wanted.subList(from, Math.min(from + LOOKUP_BATCH, wanted.size()))
            .stream()
            .map(key -> new SectionKey(key.forestFileId(), key.roadSectionId()))
            .toList();
        calls++;
        for (RoadSummary road : lrmops.lookUpRoadSections(batch)) {
          RoadSectionKey key = RoadSectionKey.of(road.roadId(), road.sectionId());
          if (key != null && road.roadName() != null) {
            names.putIfAbsent(key, road.roadName());
          }
        }
      }
    } catch (RestClientException ex) {
      log.warn("road-name lookup failed after {} call(s), {} ms; names left blank: {}",
          calls, millisSince(start), ex.getMessage());
      return names;
    }
    log.info("road-name lookup: {} section(s), {} found, {} call(s), {} ms",
        wanted.size(), names.size(), calls, millisSince(start));
    return names;
  }

  /**
   * The name of one road section, for a page that shows a single site. Null when either half is
   * blank, LRMOPS has no such section or no name for it, or the API cannot answer (logged): the
   * name is one field of a page that is otherwise CBR's own.
   */
  public String nameOf(String forestFileId, String roadSectionId) {
    RoadSectionKey key = RoadSectionKey.of(forestFileId, roadSectionId);
    return key == null ? null : namesOf(List.of(key)).get(key);
  }

  private <T> T call(Supplier<T> request) {
    if (!lrmops.isConfigured()) {
      throw new RoadLookupUnavailableException(null);
    }
    try {
      return request.get();
    } catch (RestClientException ex) {
      log.warn("LRM Ops API call failed: {}", ex.getMessage());
      throw new RoadLookupUnavailableException(ex);
    }
  }

  private static long millisSince(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
