package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a structure's <b>current</b> load rating is.
 *
 * <p>A port of {@code CBR.FIND_CRNT_LD_RATING_ID}, and it is a business rule rather than a query.
 * The same function is called from thirteen places in the legacy package — saving a rating, deleting
 * one, archiving a structure, and deleting an inspection — so it is the definition of "current load
 * rating" for the whole application, not something an inspection delete happens to need.
 *
 * <h2>The rule</h2>
 * A structure's rating comes from whichever of these is <em>later</em>:
 *
 * <ul>
 *   <li><b>A manual rating</b> — one entered by hand ({@code INSPECTION_ID IS NULL}), which counts
 *       from its {@code ENTRY_TIMESTAMP}.</li>
 *   <li><b>An inspection's rating</b> — which counts from the <em>inspection's date</em>, and only
 *       once that inspection is terminal. An inspection sitting at {@code SUB} contributes nothing,
 *       however recent.</li>
 * </ul>
 *
 * <p>If there is neither, the structure has no current rating.
 *
 * <h2>Comparing a timestamp against a date</h2>
 * The two candidates are not the same kind of value — a manual rating carries a timestamp, an
 * inspection carries a date — and legacy compares them directly, defaulting either missing side to
 * {@code 0001/01/01}. The comparison here is on the manual rating's <em>date</em> for the same
 * reason: an inspection dated today and a manual rating entered today are the same day, and legacy's
 * comparison would let the manual one win on the time of day. Ported as written, because the
 * alternative changes which rating a structure shows.
 *
 * <p><b>Ties go to the inspection.</b> Legacy's test is {@code manual > inspection}, so equal dates
 * fall to the {@code ELSE} branch.
 *
 * <h2>Unverified against real data</h2>
 * This is a faithful port of the procedure's logic, tested against fixtures. It has <b>not</b> been
 * checked against production load-rating data, and it is worth doing that before it is trusted to
 * rewrite a structure's rating — the shape it replaces has been running for fifteen years and will
 * have met cases these tests do not imagine.
 */
@Service
public class LoadRatingService {

  private static final Logger log = LoggerFactory.getLogger(LoadRatingService.class);

  /**
   * The statuses that make an inspection's load rating count.
   *
   * <p>{@code RVD} is the terminal status; {@code ACC} is its expired predecessor, which nothing can
   * set any more but which still sits on rows nobody has re-saved. Legacy tests for both, and the
   * outbound LRM views do too (cbr-workflows.local.md §1) — so an inspection carrying {@code ACC}
   * must keep supplying its rating.
   */
  private static final List<String> TERMINAL_STATUSES = List.of("RVD", "ACC");

  private final StructureLoadRatingRepository loadRatings;
  private final StructureInspectionRepository inspections;

  public LoadRatingService(
      StructureLoadRatingRepository loadRatings, StructureInspectionRepository inspections) {
    this.loadRatings = loadRatings;
    this.inspections = inspections;
  }

  /**
   * The rating that is currently in force for a structure, if any.
   *
   * <p>Legacy returns {@code -1} for "none"; an empty {@link Optional} says the same thing without a
   * magic number, and without a caller having to remember which one.
   *
   * @param crossingStructureId the structure
   */
  @Transactional(readOnly = true)
  public Optional<StructureLoadRatingEntity> currentLoadRating(Long crossingStructureId) {
    Optional<StructureLoadRatingEntity> manual = loadRatings
        .findFirstByCrossingStructureIdAndInspectionIdIsNullOrderByEntryTimestampDesc(
            crossingStructureId);
    Optional<LocalDate> reviewedOn =
        inspections.findLatestReviewedInspectionDate(crossingStructureId, TERMINAL_STATUSES);

    if (manual.isEmpty() && reviewedOn.isEmpty()) {
      log.debug("Structure {} has no load rating from either source", crossingStructureId);
      return Optional.empty();
    }

    if (manualWins(manual, reviewedOn)) {
      log.debug("Structure {} takes its load rating from a manual entry", crossingStructureId);
      return manual;
    }

    // The inspection side can still come back empty: the inspection that carries the latest
    // reviewed date may have recorded no rating at all. Legacy raises NO_DATA_FOUND there and its
    // handler turns that into -1, so "no rating" is the same answer either way.
    Optional<StructureLoadRatingEntity> fromInspection = reviewedOn
        .map(date -> loadRatings.findReviewedRatingsOn(crossingStructureId, date, TERMINAL_STATUSES))
        .orElseGet(List::of)
        .stream()
        .findFirst();

    log.debug("Structure {} takes its load rating from an inspection dated {}",
        crossingStructureId, reviewedOn.orElse(null));
    return fromInspection;
  }

  /**
   * Whether the manual rating is the later of the two.
   *
   * <p>Strictly later, matching legacy's {@code >}: on the same day the inspection wins. A missing
   * side always loses, which is legacy's {@code NVL(…, 0001/01/01)} without the sentinel date.
   */
  private static boolean manualWins(
      Optional<StructureLoadRatingEntity> manual, Optional<LocalDate> reviewedOn) {
    if (manual.isEmpty()) {
      return false;
    }
    if (reviewedOn.isEmpty()) {
      return true;
    }
    return manual.get().getEntryTimestamp().toLocalDate().isAfter(reviewedOn.get());
  }
}
