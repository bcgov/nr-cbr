package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Suggestions for the Designated Maintainer field on Site Search.
 *
 * <p>Replaces the legacy lookup popup ({@code client_search.jsp} plus
 * {@code CBR.FIND_EXSTNG_CLNTS_BY_CRITERIA}), which made the user open a window, choose which of
 * six fields to search, submit, and click a row — to fill in two text boxes. One term searches all
 * of it here.
 */
@Service
public class ClientLookupService {

  /** A client number is a number and nothing else; anything with a letter is a name. */
  private static final Pattern DIGITS = Pattern.compile("\\d+");

  /** {@code FOREST_CLIENT.CLIENT_NUMBER} is stored zero-padded to this width. */
  private static final int CLIENT_NUMBER_LENGTH = 8;

  /**
   * Shorter than this matches too much to be a suggestion.
   *
   * <p>Applied to the text arm only. An exact client number is precise at any length — typing
   * {@code 5} asks for {@code 00000005} and can return at most that client's locations — whereas
   * two letters would match a large share of the table and tell the user nothing.
   *
   * <p>nr-frep's floor is the same three characters, for a different reason worth not inheriting:
   * its proc {@code BULK COLLECT}s into a {@code VARRAY(500)} and <em>raises</em> when a search
   * overflows it. Nothing here overflows; a broad term is capped and ordered, not an error.
   */
  private static final int MINIMUM_TERM_LENGTH = 3;

  /**
   * How many suggestions come back.
   *
   * <p>Legacy caps at {@code ROWNUM <= 200}, which suited a results table the user scrolled. A combo
   * box shows a handful at a time, so this is the smaller number — and because the queries order by
   * client name, what a broad term loses is the tail of the alphabet rather than an arbitrary slice.
   * The remedy for a truncated list is a longer term, which is the behaviour a type-ahead teaches
   * anyway.
   */
  private static final int SUGGESTION_LIMIT = 50;

  private static final String WILDCARD = "%";

  private final ClientLocationRepository clientLocations;

  public ClientLookupService(ClientLocationRepository clientLocations) {
    this.clientLocations = clientLocations;
  }

  /**
   * Maintainers matching a free-text term.
   *
   * <p><b>The term's shape picks the query.</b> All digits is a client number, matched exactly
   * after zero-padding, so typing {@code 66838} finds {@code 00066838}. Anything else is matched
   * against the client name, the division name and the city. The user is not asked which they are
   * typing, because on this field they generally do not know — the legacy popup asked, and that is
   * most of what made it tedious.
   *
   * <p><b>An empty list is the answer to a term that cannot match, not an error.</b> A blank term,
   * two letters, or more than eight digits all return nothing: this is called on a keystroke, and a
   * 400 arriving mid-word would surface as a failure the user has not made yet.
   *
   * @param term what the user has typed so far
   * @return at most {@value #SUGGESTION_LIMIT} suggestions, ordered by client name then location
   */
  @Transactional(readOnly = true)
  public List<ClientLookupResult> suggest(String term) {
    if (!StringUtils.hasText(term)) {
      return List.of();
    }
    String trimmed = term.trim();
    Pageable limit = PageRequest.ofSize(SUGGESTION_LIMIT);

    if (DIGITS.matcher(trimmed).matches()) {
      // Longer than the stored width is not a client number at all, so there is nothing to pad and
      // nothing it could equal.
      if (trimmed.length() > CLIENT_NUMBER_LENGTH) {
        return List.of();
      }
      String padded = "0".repeat(CLIENT_NUMBER_LENGTH - trimmed.length()) + trimmed;
      return clientLocations.findMaintainersByClientNumber(padded, limit);
    }

    if (trimmed.length() < MINIMUM_TERM_LENGTH) {
      return List.of();
    }
    return clientLocations.findMaintainersByText(
        WILDCARD + trimmed.toUpperCase(Locale.ROOT) + WILDCARD, limit);
  }
}
