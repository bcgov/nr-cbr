package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.HazardsUpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An inspection's Hydraulic Hazards, edited on its page — legacy's tab ({@code
 * cbr-inspection-page.local.md} §2, §8), with legacy's rules:
 *
 * <ul>
 *   <li>the three water measurements: metres, to one decimal place, from 0 to 99999.9; and
 *       required past In Progress, or once a value has been saved — legacy's "Required field.",
 *       which its save ignored, enforced here;
 *   <li>the comment: at most 2000;
 *   <li>refused once the inspection is Reviewed or Accepted, as legacy disables its page then (not
 *       once Rejected) — only in the browser there, here on the server too.
 * </ul>
 *
 * <p>Legacy's range warnings (depth 20, width 350, clearance 100) stay warnings, on the page.
 */
@Service
public class InspectionHazardsService {

  private static final Logger log = LoggerFactory.getLogger(InspectionHazardsService.class);

  private static final String IN_PROGRESS = "PRO";
  /** The statuses after which legacy's page is disabled. */
  private static final Map<String, String> LOCKING_STATUSES =
      Map.of("RVD", "Reviewed", "ACC", "Accepted");
  private static final int TEXT_MAX = 2000;
  private static final String YES = "Y";
  private static final String NO = "N";

  private final StructureInspectionRepository inspections;
  private final LoggedUserHelper loggedUser;

  public InspectionHazardsService(StructureInspectionRepository inspections,
      LoggedUserHelper loggedUser) {
    this.inspections = inspections;
    this.loggedUser = loggedUser;
  }

  /**
   * Saves the inspection's hydraulic hazards.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   once it is Reviewed or Accepted
   * @throws FieldValidationException    with a message for each field at fault
   */
  @Transactional
  public void update(long inspectionId, HazardsUpdateRequest request) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId)
        .orElseThrow(() -> new InspectionNotFoundException(inspectionId));
    String status = statusOf(inspection);
    if (status != null && LOCKING_STATUSES.containsKey(status)) {
      throw new InspectionLockedException(inspectionId, LOCKING_STATUSES.get(status),
          "its hydraulic hazards can no longer be changed from it");
    }
    validate(request, status, inspection);

    inspections.save(inspection.toBuilder()
        .channelPwlDepth(request.presentWaterDepth())
        .channelPwlWidth(request.presentWaterWidth())
        .channelHwlClearance(request.highWaterClearance())
        .highWaterHazardInd(yesNo(request.highWater()))
        .debrisHazardInd(yesNo(request.debris()))
        .scourHazardInd(yesNo(request.scour()))
        .iceHazardInd(yesNo(request.ice()))
        .aggradationHazardInd(yesNo(request.aggradation()))
        .hazardComment(InspectionCreateValidator.trimmed(request.comment()))
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated the hydraulic hazards of inspection {}", inspectionId);
  }

  private static void validate(HazardsUpdateRequest request, String status,
      StructureInspectionEntity stored) {
    Map<String, String> errors = new LinkedHashMap<>();
    boolean pastInProgress = status != null && !IN_PROGRESS.equals(status);
    measurement(errors, "presentWaterDepth", "Estimated Present Water Level Depth",
        request.presentWaterDepth(), pastInProgress || stored.getChannelPwlDepth() != null);
    measurement(errors, "presentWaterWidth", "Estimated Present Water Level Width",
        request.presentWaterWidth(), pastInProgress || stored.getChannelPwlWidth() != null);
    measurement(errors, "highWaterClearance", "Estimated High Water Level Clearance",
        request.highWaterClearance(),
        pastInProgress || stored.getChannelHwlClearance() != null);
    InspectionCreateValidator.tooLong(errors, "hazardComment", "Comments", request.comment(),
        TEXT_MAX);
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Hydraulic hazards cannot be saved", errors);
    }
  }

  /** Required when legacy marks it so; otherwise metres, when given. */
  private static void measurement(Map<String, String> errors, String field, String label,
      BigDecimal value, boolean required) {
    if (value == null && required) {
      errors.put(field, label + " is required.");
    } else {
      InspectionCreateValidator.metres(errors, field, label, value);
    }
  }

  private static String statusOf(StructureInspectionEntity inspection) {
    InspectionReportStatusEntity status = inspection.getCurrentStatus();
    return status == null ? null : status.getInspectionReportStatusCode();
  }

  private static String yesNo(Boolean value) {
    return Boolean.TRUE.equals(value) ? YES : NO;
  }
}
