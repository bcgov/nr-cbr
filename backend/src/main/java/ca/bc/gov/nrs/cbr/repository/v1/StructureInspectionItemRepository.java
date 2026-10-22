package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_INSPECTION_ITEM} — the answered questions of an inspection form. */
@Repository
public interface StructureInspectionItemRepository
    extends JpaRepository<StructureInspectionItemEntity, Long> {

  void deleteByInspectionId(Long inspectionId);

  /** An inspection's answers. */
  List<StructureInspectionItemEntity> findByInspectionId(Long inspectionId);

  /** The items of these inspections — a report's, a batch at a time. */
  List<StructureInspectionItemEntity> findByInspectionIdIn(Collection<Long> inspectionIds);

  /** An inspection's answers to one question group — a Condition card. */
  List<StructureInspectionItemEntity> findByInspectionIdAndSubsectionCode(Long inspectionId,
      String subsectionCode);

  /**
   * Whether any inspection answered a question about this span — legacy's
   * {@code FIND_INSPCTN_ITEMS_BY_SPAN_ID}, which blocks its delete.
   */
  boolean existsByForestServiceBridgeSpanId(Long forestServiceBridgeSpanId);

  /**
   * Whether any inspection answered a question about this pier — legacy's
   * {@code FIND_INSPCTN_ITEMS_BY_PIER_ID}, which blocks its delete.
   */
  boolean existsByForestServiceBridgePierId(Long forestServiceBridgePierId);
}
