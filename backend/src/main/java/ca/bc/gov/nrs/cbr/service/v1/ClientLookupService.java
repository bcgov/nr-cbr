package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import ca.bc.gov.nrs.cbr.struct.v1.ClientScope;
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
   * <p><b>The scope decides which set is searched, not how.</b> The term is read the same way
   * either side of it — all digits is a client number, anything else is a name — so a user moving
   * between the two screens does not have to learn two rules.
   *
   * <p><b>Three characters minimum, whether digits or letters.</b> The field says so, and until
   * now it was true only of names — a lone digit searched, and answered with whatever the cap
   * allowed. A number is matched as a fragment like a name is, so it needs the same floor.
   *
   * @param term  what the user has typed so far
   * @param scope which clients this screen can usefully offer
   * @return at most {@value #SUGGESTION_LIMIT} suggestions, ordered by client name then location
   */
  @Transactional(readOnly = true)
  public List<ClientLookupResult> suggest(String term, ClientScope scope) {
    if (!StringUtils.hasText(term)) {
      return List.of();
    }
    String trimmed = term.trim();
    Pageable limit = PageRequest.ofSize(SUGGESTION_LIMIT);

    if (trimmed.length() < MINIMUM_TERM_LENGTH) {
      return List.of();
    }

    if (DIGITS.matcher(trimmed).matches()) {
      // Longer than the stored width is not a client number at all — nothing eight characters wide
      // can contain it.
      if (trimmed.length() > CLIENT_NUMBER_LENGTH) {
        return List.of();
      }
      // Wrapped, not zero-padded. The number is stored padded to eight and the user types the
      // digits they know; padding a fragment invents the rest of it. For client 00001286 that made
      // "1286" work — padding happened to rebuild the whole number — and "0128" fail, because it
      // became 00000128. Both are fragments of the same number.
      String digits = WILDCARD + trimmed + WILDCARD;
      return scope == ClientScope.ROAD_FILE_HOLDERS
          ? clientLocations.findRoadFileHolders(null, digits, limit)
          : clientLocations.findMaintainerClientsByNumber(digits, limit);
    }

    String wildcarded = WILDCARD + trimmed.toUpperCase(Locale.ROOT) + WILDCARD;
    return scope == ClientScope.ROAD_FILE_HOLDERS
        ? clientLocations.findRoadFileHolders(wildcarded, null, limit)
        : clientLocations.findMaintainerClientsByText(wildcarded, limit);
  }

  /**
   * The locations of one client that actually maintain a site — the Location filter beside the
   * client on Site Search.
   *
   * <p>Narrowed to what is in use rather than every location the client has: a filter offering a
   * location no site names would return nothing, which reads as a broken search rather than as an
   * empty one.
   *
   * <p>Empty for a blank or over-long number rather than an error. The caller asks as soon as a
   * client is picked, and a half-typed number is an ordinary state.
   */
  @Transactional(readOnly = true)
  public List<ClientLookupResult> locationsOf(String clientNumber) {
    if (!StringUtils.hasText(clientNumber)) {
      return List.of();
    }
    String trimmed = clientNumber.trim();
    if (trimmed.length() > CLIENT_NUMBER_LENGTH || !DIGITS.matcher(trimmed).matches()) {
      return List.of();
    }
    String padded = "0".repeat(CLIENT_NUMBER_LENGTH - trimmed.length()) + trimmed;
    return clientLocations.findMaintainersByClientNumber(
        padded, PageRequest.ofSize(SUGGESTION_LIMIT));
  }
}
