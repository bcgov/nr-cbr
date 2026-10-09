package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.RepairNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeOrderEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.RepairPriorityCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.RepairStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairTypeOrderRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairTypeXrefRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairTypeOption;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.Listing;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.Repair;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.View;
import ca.bc.gov.nrs.cbr.struct.v1.UserAudit;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The structure page's Repairs tab — legacy's {@code repairTab.jsp}, a page at a time.
 *
 * <p>Lists, edits and deletes repairs; legacy's Add comes next. Legacy inner-joins the priority
 * and status codes, so a repair whose code has no row would vanish there; here it stays, its code
 * shown without a description.
 */
@Service
public class StructureRepairsService {

  private static final Logger log = LoggerFactory.getLogger(StructureRepairsService.class);

  /** Suggested, required, carried forward — legacy's outstanding statuses. */
  private static final List<String> OUTSTANDING_STATUSES = List.of("SUG", "REQ", "CF");
  private static final String SUGGESTED = "SUG";
  private static final String REQUIRED = "REQ";
  private static final String COMPLETED = "COM";
  /** Required and Not Required: the statuses legacy offers only a P.Eng. */
  private static final Set<String> ENGINEER_STATUSES = Set.of(REQUIRED, "NRQ");
  /** The type whose repair must be described — legacy's {@code Repair.validate}. */
  private static final String DESCRIBED_TYPE = "800A";
  /** The description column, in bytes. */
  private static final int TEXT_MAX = 2000;
  /** {@code ESTIMATE} and {@code ACTUAL_COST} are {@code NUMBER(6)}: at most six digits. */
  private static final long AMOUNT_MAX = 999_999L;
  private static final int MAX_PAGE_SIZE = 100;

  private final CrossingStructureRepository structures;
  private final StructureRepairRepository repairs;
  private final RepairPriorityCodeRepository priorities;
  private final RepairStatusCodeRepository statuses;
  private final StructureRepairTypeCodeRepository types;
  private final StructureRepairTypeOrderRepository typeUnits;
  private final StructureRepairTypeXrefRepository typeXref;
  private final LoggedUserHelper loggedUser;

  public StructureRepairsService(
      CrossingStructureRepository structures,
      StructureRepairRepository repairs,
      RepairPriorityCodeRepository priorities,
      RepairStatusCodeRepository statuses,
      StructureRepairTypeCodeRepository types,
      StructureRepairTypeOrderRepository typeUnits,
      StructureRepairTypeXrefRepository typeXref,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.repairs = repairs;
    this.priorities = priorities;
    this.statuses = statuses;
    this.types = types;
    this.typeUnits = typeUnits;
    this.typeXref = typeXref;
    this.loggedUser = loggedUser;
  }

  /**
   * A page of the structure's repairs, in legacy's order. Those raised by an inspection from before
   * the superstructure went in are left out unless {@code includeBeforeInstall} — legacy's
   * "Show Inspections before the Superstructure Install Date".
   *
   * @param view       outstanding only, or all
   * @param pageNumber zero-based
   * @param pageSize   held to between 1 and 100
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public Listing repairs(long structureId, View view, int pageNumber, int pageSize,
      boolean includeBeforeInstall) {
    InstallCutoff cutoff = InstallCutoff.of(structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId)));
    boolean outstandingOnly = view == View.OUTSTANDING;
    Page<StructureRepairEntity> page = repairs.findPageByStructure(
        structureId, outstandingOnly, OUTSTANDING_STATUSES,
        includeBeforeInstall || cutoff.none(), cutoff.installed(),
        PageRequest.of(Math.max(pageNumber, 0), Math.clamp(pageSize, 1, MAX_PAGE_SIZE)));
    List<StructureRepairEntity> rows = page.getContent();

    Map<String, String> priorityNames = descriptions(priorities, rows,
        StructureRepairEntity::getRepairPriorityCode,
        RepairPriorityCodeEntity::getRepairPriorityCode, RepairPriorityCodeEntity::getDescription);
    Map<String, String> statusNames = descriptions(statuses, rows,
        StructureRepairEntity::getRepairStatusCode,
        RepairStatusCodeEntity::getRepairStatusCode, RepairStatusCodeEntity::getDescription);
    Map<String, String> typeNames = descriptions(types, rows,
        StructureRepairEntity::getStructureRepairTypeCode,
        StructureRepairTypeCodeEntity::getStructureRepairTypeCode,
        StructureRepairTypeCodeEntity::getDescription);
    Map<String, String> units = descriptions(typeUnits, rows,
        StructureRepairEntity::getStructureRepairTypeCode,
        StructureRepairTypeOrderEntity::getStructureRepairTypeCode,
        StructureRepairTypeOrderEntity::getStructureRepairUnit);

    List<Repair> content = rows.stream()
        .map(repair -> new Repair(
            String.valueOf(repair.getRepairId()),
            repair.getRepairNumber(),
            code(repair.getRepairStatusCode(), statusNames),
            code(repair.getStructureRepairTypeCode(), typeNames),
            UserAudit.of(repair.getSuggestedByUserid(), repair.getSuggestedByTimestamp()),
            UserAudit.of(repair.getRequiredByUserid(), repair.getRequiredByTimestamp()),
            UserAudit.of(repair.getCompletedByUserid(), repair.getCompletedByTimestamp()),
            repair.getInspectionId() == null ? null : String.valueOf(repair.getInspectionId()),
            repair.getInspection() == null ? null : repair.getInspection().getInspectionDate(),
            code(repair.getRepairPriorityCode(), priorityNames),
            repair.getCompletedDate(),
            repair.getEstimate(),
            repair.getActualCost(),
            repair.getRepairQuantity(),
            units.get(repair.getStructureRepairTypeCode()),
            repair.getDescription()))
        .toList();
    return new Listing(
        new PagedResponse<>(content, page.getTotalElements(), page.getTotalPages(),
            page.getNumber(), page.getSize()),
        cutoff.none()
            ? 0
            : repairs.countBeforeInstall(
                structureId, outstandingOnly, OUTSTANDING_STATUSES, cutoff.installed()));
  }

  /**
   * The repair types that apply to the structure, each with its group — the Repair Item dialog's
   * Repair Type list before its group checkboxes narrow it. In legacy's order: by group, then the
   * type's order. A type in two groups appears once in each.
   *
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public List<RepairTypeOption> repairTypes(long structureId) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    if (structure.getStructureTypeClassCode() == null) {
      return List.of();
    }
    return typeXref.findTypesForStructureTypeClass(structure.getStructureTypeClassCode()).stream()
        .distinct()
        .toList();
  }

  /**
   * Saves an edit to one repair — legacy's "Repair Item" dialog ({@code StructureAction.repair},
   * {@code CBR.UPDATE_REPAIR}).
   *
   * <p>As legacy: the status, priority and type are required; a completed repair needs its
   * completed date; the description is required for the type {@code 800A}; Required and Not
   * Required are a P.Eng's to set. Legacy's dialog clears the completed date and actual cost
   * unless the status is Completed, and so does this, on the server. The number is not changed.
   *
   * <p>Beyond legacy: the amounts and quantity are whole numbers from 0 to 999,999, and a blank one
   * stays blank, where legacy saved 0.
   *
   * <p>As legacy's procedure, the audit for the status the repair now has — Suggested, Required or
   * Completed by — is stamped with the user and the time on every save; the other two are kept.
   * Unlike legacy, the carried-forward flag is kept: legacy reset it, which returned a repair
   * already carried forward to the outstanding list beside its copy.
   *
   * @throws RepairNotFoundException  if the structure has no such repair
   * @throws FieldValidationException with a message for each field at fault
   */
  @Transactional
  public void update(long structureId, long repairId, RepairUpdateRequest request) {
    StructureRepairEntity repair = repairs.findById(repairId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId))
        .orElseThrow(() -> new RepairNotFoundException(structureId, repairId));

    String status = trimmed(request.statusCode());
    boolean completed = COMPLETED.equals(status);
    String priority = trimmed(request.priorityCode());
    String type = trimmed(request.typeCode());
    String description = trimmed(request.description());
    LocalDate completedDate = completed ? request.completedDate() : null;
    Long actualCost = completed ? request.actualCost() : null;
    validate(request, status, priority, type, description, completedDate, actualCost);

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    StructureRepairEntity.StructureRepairEntityBuilder updated = repair.toBuilder()
        .repairStatusCode(status)
        .repairPriorityCode(priority)
        .completedDate(completedDate)
        .estimate(request.estimate())
        .actualCost(actualCost)
        .structureRepairTypeCode(type)
        .repairQuantity(request.quantity())
        .description(description)
        .updateUserid(user)
        .updateTimestamp(now);
    switch (status) {
      case SUGGESTED -> updated.suggestedByUserid(user).suggestedByTimestamp(now);
      case REQUIRED -> updated.requiredByUserid(user).requiredByTimestamp(now);
      case COMPLETED -> updated.completedByUserid(user).completedByTimestamp(now);
      default -> { /* Not Required and Carried Forward have no audit, as legacy's ELSE NULL. */ }
    }
    repairs.save(updated.build());
    log.info("Updated repair {} ({}) of structure {} to status {}", repairId,
        repair.getRepairNumber(), structureId, status);
  }

  private void validate(RepairUpdateRequest request, String status, String priority, String type,
      String description, LocalDate completedDate, Long actualCost) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (status == null) {
      errors.put("statusCode", "Repair Status is required.");
    } else if (!statuses.existsById(status)) {
      errors.put("statusCode", "Repair Status is not one of the listed statuses.");
    } else if (ENGINEER_STATUSES.contains(status) && !loggedUser.isPeng()) {
      errors.put("statusCode",
          "Only a professional engineer can set Repair Status to Required or Not Required.");
    }
    if (priority == null) {
      errors.put("priorityCode", "Repair Priority is required.");
    } else if (!priorities.existsById(priority)) {
      errors.put("priorityCode", "Repair Priority is not one of the listed priorities.");
    }
    if (COMPLETED.equals(status) && completedDate == null) {
      errors.put("completedDate",
          "Repair Completed Date is required when the status is Completed.");
    }
    checkAmount(errors, "estimate", "Repair Estimate Cost", request.estimate());
    checkAmount(errors, "actualCost", "Repair Actual Cost", actualCost);
    if (type == null) {
      errors.put("typeCode", "Repair Type is required.");
    } else if (!types.existsById(type)) {
      errors.put("typeCode", "Repair Type is not one of the listed types.");
    }
    checkAmount(errors, "quantity", "Qty", request.quantity());
    if (description == null && DESCRIBED_TYPE.equals(type)) {
      errors.put("description", "Repair Description is required for this Repair Type.");
    } else if (description != null
        && description.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX) {
      // Bytes, because the column is declared in bytes and an accented character costs two.
      errors.put("description",
          "Repair Description can be at most " + TEXT_MAX + " characters.");
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Repair cannot be saved", errors);
    }
  }

  private static void checkAmount(Map<String, String> errors, String field, String label,
      Long value) {
    if (value != null && (value < 0 || value > AMOUNT_MAX)) {
      errors.put(field, label + " must be a whole number from 0 to 999,999.");
    }
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * Deletes one repair — legacy's delete icon on the Repairs tab ({@code CBR.DELETE_REPAIR}, a
   * plain delete). As legacy, any repair of the structure may go: carried forward or not, raised by
   * an inspection or not. Nothing else references the row.
   *
   * @throws RepairNotFoundException if the structure has no such repair
   */
  @Transactional
  public void delete(long structureId, long repairId) {
    StructureRepairEntity repair = repairs.findById(repairId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId))
        .orElseThrow(() -> new RepairNotFoundException(structureId, repairId));
    repairs.delete(repair);
    log.info("Deleted repair {} ({}) of structure {}", repairId, repair.getRepairNumber(),
        structureId);
  }

  /** Each code on the page with its description (or other column), read in one query. */
  private static <E> Map<String, String> descriptions(JpaRepository<E, String> table,
      Collection<StructureRepairEntity> rows, Function<StructureRepairEntity, String> codeOf,
      Function<E, String> keyOf, Function<E, String> valueOf) {
    return table
        .findAllById(rows.stream().map(codeOf).filter(Objects::nonNull).distinct().toList())
        .stream()
        .filter(row -> valueOf.apply(row) != null)
        .collect(Collectors.toMap(keyOf, valueOf));
  }

  private static CodeValue code(String code, Map<String, String> descriptions) {
    return code == null ? CodeValue.NONE : new CodeValue(code, descriptions.get(code));
  }
}
