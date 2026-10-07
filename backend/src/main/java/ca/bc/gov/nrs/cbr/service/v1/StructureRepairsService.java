package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
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
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.Repair;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.View;
import ca.bc.gov.nrs.cbr.struct.v1.UserAudit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The structure page's Repairs tab — legacy's {@code repairTab.jsp}, a page at a time.
 *
 * <p>Read-only for now; legacy's Add, Edit (Level 1) and Delete come with the page's editing.
 * Legacy inner-joins the priority and status codes, so a repair whose code has no row would vanish
 * there; here it stays, its code shown without a description.
 */
@Service
public class StructureRepairsService {

  /** Suggested, required, carried forward — legacy's outstanding statuses. */
  private static final List<String> OUTSTANDING_STATUSES = List.of("SUG", "REQ", "CF");
  private static final int MAX_PAGE_SIZE = 100;

  private final CrossingStructureRepository structures;
  private final StructureRepairRepository repairs;
  private final RepairPriorityCodeRepository priorities;
  private final RepairStatusCodeRepository statuses;
  private final StructureRepairTypeCodeRepository types;
  private final StructureRepairTypeOrderRepository typeUnits;

  public StructureRepairsService(
      CrossingStructureRepository structures,
      StructureRepairRepository repairs,
      RepairPriorityCodeRepository priorities,
      RepairStatusCodeRepository statuses,
      StructureRepairTypeCodeRepository types,
      StructureRepairTypeOrderRepository typeUnits) {
    this.structures = structures;
    this.repairs = repairs;
    this.priorities = priorities;
    this.statuses = statuses;
    this.types = types;
    this.typeUnits = typeUnits;
  }

  /**
   * A page of the structure's repairs, in legacy's order.
   *
   * @param view       outstanding only, or all
   * @param pageNumber zero-based
   * @param pageSize   held to between 1 and 100
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public PagedResponse<Repair> repairs(long structureId, View view, int pageNumber, int pageSize) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    Page<StructureRepairEntity> page = repairs.findPageByStructure(
        structureId, view == View.OUTSTANDING, OUTSTANDING_STATUSES,
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
    return new PagedResponse<>(content, page.getTotalElements(), page.getTotalPages(),
        page.getNumber(), page.getSize());
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
