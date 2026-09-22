package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * Which clients a lookup may suggest.
 *
 * <p>There is no unscoped option, and that is the point. A suggestion the user cannot usefully act
 * on is worse than no suggestion: it offers a filter guaranteed to match nothing, and the user has
 * no way to tell that from a filter that simply found nothing. Each scope names the set its screen
 * can actually do something with.
 *
 * <p>The two sets overlap without either containing the other — a company may hold a road file and
 * maintain no site, or maintain a site on a road somebody else holds — and they are reached
 * through different tables. Neither query can answer the other's question.
 */
public enum ClientScope {

  /**
   * Clients already recorded as a Designated Maintainer on a site.
   *
   * <p>Legacy's "Only search existing clients within the system?", which its client dialog offers
   * as a checkbox and ticks by default.
   */
  MAINTAINERS,

  /**
   * Clients holding a road file, for the road search.
   *
   * <p>One row per client rather than per location: the road search matches on the client's name
   * and number and shows no address, so the location code that a maintainer is identified by has
   * nothing to distinguish here.
   */
  ROAD_FILE_HOLDERS
}
