package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.SiteInUseException;
import ca.bc.gov.nrs.cbr.exception.SiteNotFoundException;
import ca.bc.gov.nrs.cbr.repository.v1.CloseProximityInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations on a crossing site itself, as opposed to searching for one.
 *
 * <p>Deleting is all there is so far; creating and editing arrive with the site screens.
 */
@Service
public class SiteService {

  private static final Logger log = LoggerFactory.getLogger(SiteService.class);

  private static final String ACTIVE = "Y";
  private static final String ARCHIVED = "N";

  private final CrossingSiteRepository crossingSiteRepository;
  private final CrossingStructureRepository crossingStructureRepository;
  private final CloseProximityInspectionRepository closeProximityInspectionRepository;

  public SiteService(
      CrossingSiteRepository crossingSiteRepository,
      CrossingStructureRepository crossingStructureRepository,
      CloseProximityInspectionRepository closeProximityInspectionRepository) {
    this.crossingSiteRepository = crossingSiteRepository;
    this.crossingStructureRepository = crossingStructureRepository;
    this.closeProximityInspectionRepository = closeProximityInspectionRepository;
  }

  /**
   * Deletes a site, if nothing depends on it.
   *
   * <p><b>A hard delete, and it is not recoverable.</b> That is what legacy does —
   * {@code CBR.DELETE_SITE} is a bare {@code DELETE FROM CROSSING_SITE WHERE CROSSING_SITE_ID = :id}
   * — and CBR has no soft-delete column on the table and writes no history row for a site, so there
   * is nothing to reverse it with. Worth knowing before this is offered anywhere more reachable than
   * behind a confirmation dialog and the destructive capability.
   *
   * <h3>The guards, and why they are here rather than left to the database</h3>
   * Two tables key to {@code CROSSING_SITE} and neither cascades, so the database will refuse a
   * delete that would orphan a child — with {@code ORA-02292}, which says nothing a user can act on.
   * Checking first turns the same refusal into a sentence naming what is in the way.
   *
   * <ul>
   *   <li><b>Active structures</b> — legacy's {@code errors.site.delete}.</li>
   *   <li><b>Archived structures</b> — legacy's {@code errors.site.delete.inactive}. Reported
   *       separately because the user cannot see them from the search results and would otherwise
   *       be told a site with no visible structures cannot be deleted, with no way to find out
   *       why.</li>
   *   <li><b>Close-proximity inspections</b> — <b>not a legacy check</b>.
   *       {@code SiteSearchAction.delete} tests the structures and stops, so in legacy a site
   *       carrying one of these and no structures passes every guard and fails in the database. This
   *       closes that gap rather than reproducing it.</li>
   * </ul>
   *
   * <p>The checks are read-then-delete in one transaction, not a lock. Two people deleting the same
   * site is harmless — the second gets a 404 — and someone adding a structure between the count and
   * the delete is caught by the foreign key, which is the backstop this is layered on top of rather
   * than a replacement for.
   *
   * @param siteId the {@code CROSSING_SITE_ID}
   * @throws SiteNotFoundException if no such site exists
   * @throws SiteInUseException    if a structure or inspection still references it
   */
  @Transactional
  public void delete(String siteId) {
    if (!crossingSiteRepository.existsById(siteId)) {
      throw new SiteNotFoundException(siteId);
    }

    long active = crossingStructureRepository.countByCrossingSiteIdAndActiveInd(siteId, ACTIVE);
    if (active > 0) {
      throw new SiteInUseException(
          "Site " + siteId + " has " + active + " associated structure(s) and cannot be deleted.");
    }

    long archived = crossingStructureRepository.countByCrossingSiteIdAndActiveInd(siteId, ARCHIVED);
    if (archived > 0) {
      throw new SiteInUseException("Site " + siteId + " has " + archived
          + " associated archived structure(s) and cannot be deleted.");
    }

    long inspections = closeProximityInspectionRepository.countByCrossingSiteId(siteId);
    if (inspections > 0) {
      throw new SiteInUseException("Site " + siteId + " has " + inspections
          + " associated close proximity inspection(s) and cannot be deleted.");
    }

    crossingSiteRepository.deleteById(siteId);
    log.info("Deleted site {}", siteId);
  }
}
