package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ClientLocationEntity;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The Designated Maintainer lookup, behind the search form's client combo box.
 *
 * <h2>Only maintainers already in use</h2>
 * Both queries below start at {@code CrossingSiteEntity} and join outwards, so nothing is suggested
 * unless some site actually names it. This is legacy's own
 * {@code CBR.FIND_EXSTNG_CLNTS_BY_CRITERIA} — {@code GET_CLIENT_BASE_SELECT(TRUE)} adds
 * {@code INNER JOIN CROSSING_SITE} and a {@code DISTINCT} — offered there as a checkbox
 * ("Only search existing clients within the system?") and taken here as the only mode.
 *
 * <p>It is the only mode because of what the lookup is for. This fills a <em>search filter</em>:
 * picking a client that appears on no site produces a search that is guaranteed to match nothing,
 * which is a worse answer than not offering it. It also bounds the result set by the site table
 * rather than by the ministry-wide client table, and it makes {@code LOCN_EXPIRED_IND} irrelevant —
 * a site naming an expired location still has to be findable.
 *
 * <p>A screen that <em>assigns</em> a maintainer needs the opposite (every client, whether or not
 * CBR has met it), which is legacy's unchecked box and its {@code FIND_CLIENTS_BY_CRITERIA}. That
 * is a second query when a site edit screen arrives, not a flag on this one.
 *
 * <h2>Two queries rather than one with null-guarded parameters</h2>
 * A single query reading {@code (:number IS NULL OR …) AND (:term IS NULL OR …)} would let one
 * plan serve both shapes, and Oracle would pick it knowing neither. They are separate so each gets
 * a plan built for the predicate it actually has — an equality on a key, or three case-folded
 * {@code LIKE}s.
 *
 * <h2>The join has no index behind it</h2>
 * {@code CROSSING_SITE} carries only {@code CSI_PK}, so the driving scan reads the whole table and
 * hash-joins from there. That is one pass with a predictable cost rather than a nested loop, and it
 * is what legacy does, but a composite index on
 * {@code CROSSING_SITE (CLIENT_NUMBER, CLIENT_LOCN_CODE)} would turn it into an index scan — and
 * would serve the existing Client Number criterion on Site Search at the same time.
 */
@Repository
public interface ClientLocationRepository
    extends JpaRepository<ClientLocationEntity, ClientLocationEntity.Key> {

  /**
   * By client number — an exact match, because the number is a key and a partial one is not a
   * smaller question but a different one.
   *
   * @param clientNumber zero-padded to the stored width of 8 by the caller
   */
  @Query("""
      SELECT DISTINCT new ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult(
               location.clientNumber,
               location.clientLocnCode,
               client.clientName,
               location.clientLocnName,
               location.city)
        FROM CrossingSiteEntity site
        JOIN ClientLocationEntity location
          ON location.clientNumber = site.clientNumber
         AND location.clientLocnCode = site.clientLocnCode
        JOIN ClientPublicEntity client
          ON client.clientNumber = location.clientNumber
       WHERE location.clientNumber = :clientNumber
       ORDER BY client.clientName, location.clientLocnCode
      """)
  List<ClientLookupResult> findMaintainersByClientNumber(
      @Param("clientNumber") String clientNumber, Pageable limit);

  /**
   * One maintainer, by the pair a site records.
   *
   * <p>Not restricted to maintainers in use, unlike the two lookups above: this is asked *because*
   * a site names it, so the restriction is already satisfied — and a site pointing at a location
   * that has since been removed should still say who it points at rather than nothing.
   *
   * <p>A list rather than one row: nothing constrains {@code V_CLIENT_PUBLIC} to a single row per
   * client number, and a duplicate there would turn a detail screen into a 500 for a reason that
   * has nothing to do with the site.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult(
               location.clientNumber,
               location.clientLocnCode,
               client.clientName,
               location.clientLocnName,
               location.city)
        FROM ClientLocationEntity location
        JOIN ClientPublicEntity client
          ON client.clientNumber = location.clientNumber
       WHERE location.clientNumber = :clientNumber
         AND location.clientLocnCode = :clientLocnCode
      """)
  List<ClientLookupResult> findMaintainer(
      @Param("clientNumber") String clientNumber, @Param("clientLocnCode") String clientLocnCode);

  /**
   * Clients holding a road file, by name or number.
   *
   * <p>Driven from {@code FOREST_FILE_CLIENT}, which is what connects a client to a road file and
   * the same table the road search joins to show the holder beside each road. A client who holds
   * no file is never suggested, for the reason {@link ca.bc.gov.nrs.cbr.struct.v1.ClientScope}
   * gives: it would offer a filter guaranteed to match nothing.
   *
   * <p><b>One row per client, not per location.</b> The file-client row carries a location code,
   * but the road search matches on name and number and shows no address — so a second row for the
   * same company would be two identical suggestions.
   *
   * <p>The two absent columns are left off the projection rather than selected as {@code NULL} —
   * see the two-argument constructor on {@link ClientLookupResult}. This query has never run
   * against Oracle: the road search cannot reach it without the {@code FOREST_FILE_CLIENT} grant,
   * so the fault it shared with the maintainer lookup was invisible here.
   *
   * @param term already trimmed, upper-cased and wrapped in wildcards by the caller, or the exact
   *             zero-padded client number
   */
  @Query("""
      SELECT DISTINCT new ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult(
               client.clientNumber, client.clientName)
        FROM ForestFileClientEntity fileClient
        JOIN ClientPublicEntity client
          ON client.clientNumber = fileClient.clientNumber
       WHERE (:term IS NOT NULL AND UPPER(client.clientName) LIKE :term)
          OR (:clientNumber IS NOT NULL AND client.clientNumber LIKE :clientNumber)
       ORDER BY client.clientName
      """)
  List<ClientLookupResult> findRoadFileHolders(
      @Param("term") String term,
      @Param("clientNumber") String clientNumber,
      Pageable limit);


  /**
   * Maintainer <b>clients</b>, by name — one row per client rather than one per location.
   *
   * <p>The two queries above answer "which client *locations* maintain a site", which is what the
   * site form needed when it set both halves of the key at once. Site Search asks a different
   * question: it filters on the client, and on the location separately. A client that maintains
   * sites at five locations is one choice there, not five identical-looking ones.
   *
   * <p>The location columns come back null, and {@code clientLabel} on the browser side already
   * guards for that — a result with only a number and a name renders as "NAME · NUMBER". They are
   * left off the projection rather than selected as {@code NULL}: see the two-argument constructor
   * on {@link ClientLookupResult} for why that distinction matters to Oracle.
   *
   * <p><b>Matched on three columns, projected as one client.</b> Division name and city are
   * searched as well as the client name, because they are what the suggestion used to display and
   * what a user looking for a particular office types — someone after "Prince George" has no way
   * to know it is not searchable. Collapsing to one row per client is what {@code DISTINCT} does
   * here: a company with three matching offices is still one choice.
   *
   * <p><b>{@code UPPER} on both sides, which is legacy's behaviour.</b>
   * {@code AbstractOracleDMLDAO.generateWhere} emits {@code UPPER(col) LIKE UPPER('%'||?||'%')} for
   * any criterion not declared case-sensitive, and {@code SiteSearchForm} declares this one through
   * the four-argument {@code addCriteria}, which defaults to insensitive.
   * {@code SiteSearchSpecifications.maintainedBy} folds no case at all, so the free-text criterion
   * beside this lookup still matches only the stored casing.
   *
   * <p>Still driven from {@code CrossingSiteEntity}: the list offers clients that actually maintain
   * something, not every client in the province.
   */
  @Query("""
      SELECT DISTINCT new ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult(
               client.clientNumber, client.clientName)
        FROM CrossingSiteEntity site
        JOIN ClientLocationEntity location
          ON location.clientNumber = site.clientNumber
         AND location.clientLocnCode = site.clientLocnCode
        JOIN ClientPublicEntity client
          ON client.clientNumber = location.clientNumber
       WHERE UPPER(client.clientName) LIKE :term
          OR UPPER(location.clientLocnName) LIKE :term
          OR UPPER(location.city) LIKE :term
       ORDER BY client.clientName
      """)
  List<ClientLookupResult> findMaintainerClientsByText(
      @Param("term") String term, Pageable limit);

  /**
   * The same list narrowed by a run of digits appearing anywhere in the client number.
   *
   * <p><b>A contains, not an equals.</b> The number is stored zero-padded to eight
   * ({@code VARCHAR2(8)}), and a user types the digits they know rather than the padding. Matching
   * exactly meant padding first, which made a partial number behave arbitrarily: for client
   * {@code 00001286}, typing {@code 1286} found it — the padding happened to reproduce the whole
   * number — while {@code 0128} became {@code 00000128} and found a different client that does not
   * exist. Both are fragments of the same number and neither should be privileged.
   *
   * <p>The leading wildcard means no index is used, which is affordable here and nowhere else: the
   * set is clients that maintain a site, the result is capped, and a type-ahead is not a report.
   */
  @Query("""
      SELECT DISTINCT new ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult(
               client.clientNumber, client.clientName)
        FROM CrossingSiteEntity site
        JOIN ClientPublicEntity client
          ON client.clientNumber = site.clientNumber
       WHERE client.clientNumber LIKE :digits
       ORDER BY client.clientName
      """)
  List<ClientLookupResult> findMaintainerClientsByNumber(
      @Param("digits") String digits, Pageable limit);
}
