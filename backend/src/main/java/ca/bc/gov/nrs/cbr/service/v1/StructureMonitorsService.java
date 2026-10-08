package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.MonitorNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.MonitorFrequencyCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.MonitorFrequencyCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.MonitoringStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureMonitorItemRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse.Listing;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse.Monitor;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse.View;
import ca.bc.gov.nrs.cbr.struct.v1.UserAudit;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The structure page's Monitoring tab — legacy's {@code monitorTab.jsp}, a page at a time.
 *
 * <p>Read-only for now; legacy's Add, Edit (Level 1) and Delete come with the page's editing.
 * Legacy inner-joins the status code, so an item whose status has no row would vanish there; here
 * it stays, its code shown without a description.
 */
@Service
public class StructureMonitorsService {

  private static final Logger log = LoggerFactory.getLogger(StructureMonitorsService.class);

  /** Suggested, required — legacy's outstanding statuses. */
  private static final List<String> OUTSTANDING_STATUSES = List.of("SUG", "REQ");
  private static final String SUGGESTED = "SUG";
  private static final String REQUIRED = "REQ";
  private static final String COMPLETED = "COM";
  /** A frequency that needs words: "Other". */
  private static final String OTHER_FREQUENCY = "OTH";
  /** The description and frequency comment columns, in bytes. */
  private static final int TEXT_MAX = 2000;
  private static final int MAX_PAGE_SIZE = 100;

  private final CrossingStructureRepository structures;
  private final StructureMonitorItemRepository monitors;
  private final MonitoringStatusCodeRepository statuses;
  private final MonitorFrequencyCodeRepository frequencies;
  private final LoggedUserHelper loggedUser;

  public StructureMonitorsService(
      CrossingStructureRepository structures,
      StructureMonitorItemRepository monitors,
      MonitoringStatusCodeRepository statuses,
      MonitorFrequencyCodeRepository frequencies,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.monitors = monitors;
    this.statuses = statuses;
    this.frequencies = frequencies;
    this.loggedUser = loggedUser;
  }

  /**
   * A page of the structure's monitoring items, in legacy's order. Those raised by an inspection
   * from before the superstructure went in are left out unless {@code includeBeforeInstall} —
   * legacy's "Show Inspections before the Superstructure Install Date".
   *
   * @param view       outstanding only, or all
   * @param pageNumber zero-based
   * @param pageSize   held to between 1 and 100
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public Listing monitors(long structureId, View view, int pageNumber, int pageSize,
      boolean includeBeforeInstall) {
    InstallCutoff cutoff = InstallCutoff.of(structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId)));
    boolean outstandingOnly = view == View.OUTSTANDING;
    Page<StructureMonitorItemEntity> page = monitors.findPageByStructure(
        structureId, outstandingOnly, OUTSTANDING_STATUSES,
        includeBeforeInstall || cutoff.none(), cutoff.installed(),
        PageRequest.of(Math.max(pageNumber, 0), Math.clamp(pageSize, 1, MAX_PAGE_SIZE)));
    List<StructureMonitorItemEntity> rows = page.getContent();

    Map<String, String> statusNames = statuses
        .findAllById(codes(rows, StructureMonitorItemEntity::getMonitoringStatusCode)).stream()
        .collect(Collectors.toMap(MonitoringStatusCodeEntity::getMonitoringStatusCode,
            MonitoringStatusCodeEntity::getDescription));
    Map<String, String> frequencyNames = frequencies
        .findAllById(codes(rows, StructureMonitorItemEntity::getMonitorFrequencyCode)).stream()
        .collect(Collectors.toMap(MonitorFrequencyCodeEntity::getMonitorFrequencyCode,
            MonitorFrequencyCodeEntity::getDescription));

    List<Monitor> content = rows.stream()
        .map(monitor -> new Monitor(
            String.valueOf(monitor.getMonitorId()),
            monitor.getMonitorNumber(),
            code(monitor.getMonitoringStatusCode(), statusNames),
            UserAudit.of(monitor.getSuggestedByUserid(), monitor.getSuggestedByTimestamp()),
            UserAudit.of(monitor.getRequiredByUserid(), monitor.getRequiredByTimestamp()),
            UserAudit.of(monitor.getCompletedByUserid(), monitor.getCompletedByTimestamp()),
            monitor.getInspectionId() == null ? null : String.valueOf(monitor.getInspectionId()),
            monitor.getInspection() == null ? null : monitor.getInspection().getInspectionDate(),
            monitor.getDescription(),
            code(monitor.getMonitorFrequencyCode(), frequencyNames),
            monitor.getMonitorFrequencyCmt()))
        .toList();
    return new Listing(
        new PagedResponse<>(content, page.getTotalElements(), page.getTotalPages(),
            page.getNumber(), page.getSize()),
        cutoff.none()
            ? 0
            : monitors.countBeforeInstall(
                structureId, outstandingOnly, OUTSTANDING_STATUSES, cutoff.installed()));
  }

  /**
   * Saves an edit to one monitoring item — legacy's "Monitoring Item" dialog
   * ({@code StructureAction.monitor}, {@code CBR.UPDATE_MONITOR}).
   *
   * <p>As legacy: the description is required and at most 2000 characters; a frequency of Other
   * needs its comment. The status and frequency must be codes that exist. The number is not
   * changed.
   *
   * <p>As legacy's procedure, the audit for the status the item now has — Suggested, Required or
   * Completed by — is stamped with the user and the time on every save, even when the status did
   * not change; the other two are kept. The frequency comment is kept only with Other, where legacy
   * kept whatever a hidden box still held.
   *
   * @throws MonitorNotFoundException if the structure has no such item
   * @throws FieldValidationException with a message for each field at fault
   */
  @Transactional
  public void update(long structureId, long monitorId, MonitorUpdateRequest request) {
    StructureMonitorItemEntity monitor = monitors.findById(monitorId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId))
        .orElseThrow(() -> new MonitorNotFoundException(structureId, monitorId));

    String status = trimmed(request.statusCode());
    String frequency = trimmed(request.frequencyCode());
    String comment = OTHER_FREQUENCY.equals(frequency) ? trimmed(request.frequencyComment()) : null;
    String description = trimmed(request.description());
    validate(status, frequency, comment, description);

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    StructureMonitorItemEntity.StructureMonitorItemEntityBuilder updated = monitor.toBuilder()
        .monitoringStatusCode(status)
        .monitorFrequencyCode(frequency)
        .monitorFrequencyCmt(comment)
        .description(description)
        .updateUserid(user)
        .updateTimestamp(now);
    switch (status) {
      case SUGGESTED -> updated.suggestedByUserid(user).suggestedByTimestamp(now);
      case REQUIRED -> updated.requiredByUserid(user).requiredByTimestamp(now);
      case COMPLETED -> updated.completedByUserid(user).completedByTimestamp(now);
      default -> { /* A status with no audit of its own, as legacy's CASE ... ELSE NULL. */ }
    }
    monitors.save(updated.build());
    log.info("Updated monitoring item {} ({}) of structure {} to status {}", monitorId,
        monitor.getMonitorNumber(), structureId, status);
  }

  private void validate(String status, String frequency, String comment, String description) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (status == null) {
      errors.put("statusCode", "Monitoring Status is required.");
    } else if (!statuses.existsById(status)) {
      errors.put("statusCode", "Monitoring Status is not one of the listed statuses.");
    }
    if (frequency != null && !frequencies.existsById(frequency)) {
      errors.put("frequencyCode", "Monitoring Frequency is not one of the listed frequencies.");
    }
    if (OTHER_FREQUENCY.equals(frequency) && comment == null) {
      errors.put("frequencyComment", "Monitor Freq. Comment is required when the frequency is "
          + "Other.");
    } else if (tooLong(comment)) {
      errors.put("frequencyComment",
          "Monitor Freq. Comment can be at most " + TEXT_MAX + " characters.");
    }
    if (description == null) {
      errors.put("description", "Monitor Description is required.");
    } else if (tooLong(description)) {
      errors.put("description",
          "Monitor Description can be at most " + TEXT_MAX + " characters.");
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Monitoring item cannot be saved", errors);
    }
  }

  /** Bytes, because the columns are declared in bytes and an accented character costs two. */
  private static boolean tooLong(String value) {
    return value != null && value.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX;
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * Deletes one monitoring item — legacy's delete icon on the Monitoring tab
   * ({@code CBR.DELETE_MONITOR}, a plain delete). Nothing else references the row.
   *
   * @throws MonitorNotFoundException if the structure has no such item
   */
  @Transactional
  public void delete(long structureId, long monitorId) {
    StructureMonitorItemEntity monitor = monitors.findById(monitorId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId))
        .orElseThrow(() -> new MonitorNotFoundException(structureId, monitorId));
    monitors.delete(monitor);
    log.info("Deleted monitoring item {} ({}) of structure {}", monitorId,
        monitor.getMonitorNumber(), structureId);
  }

  private static List<String> codes(List<StructureMonitorItemEntity> rows,
      Function<StructureMonitorItemEntity, String> codeOf) {
    return rows.stream().map(codeOf).filter(Objects::nonNull).distinct().toList();
  }

  private static CodeValue code(String code, Map<String, String> descriptions) {
    return code == null ? CodeValue.NONE : new CodeValue(code, descriptions.get(code));
  }
}
