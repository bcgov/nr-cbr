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
   * By name, division or city — a case-insensitive contains match on each.
   *
   * <p><b>{@code UPPER} on both sides, which is legacy's behaviour and not this application's
   * current behaviour.</b> {@code AbstractOracleDMLDAO.generateWhere} emits
   * {@code UPPER(col) LIKE UPPER('%'||?||'%')} for any criterion not declared case-sensitive, and
   * {@code SiteSearchForm} declares this one through the four-argument {@code addCriteria}, which
   * defaults to insensitive. {@code SiteSearchSpecifications.maintainedBy} folds no case at all, so
   * the free-text field beside this lookup still matches only the stored casing.
   *
   * <p><b>Three columns, where legacy searched one.</b> Legacy's lookup matched the client name and
   * offered separate boxes for first name, org unit and status. Division name and city are matched
   * here instead because they are what the suggestion already displays: a user reading
   * "Prince George" in the list has no way to know it is not searchable, and someone after a
   * specific division is likelier to type its name than its parent company's.
   *
   * @param term already trimmed, upper-cased and wrapped in wildcards by the caller
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
       WHERE UPPER(client.clientName) LIKE :term
          OR UPPER(location.clientLocnName) LIKE :term
          OR UPPER(location.city) LIKE :term
       ORDER BY client.clientName, location.clientLocnCode
      """)
  List<ClientLookupResult> findMaintainersByText(@Param("term") String term, Pageable limit);
}
