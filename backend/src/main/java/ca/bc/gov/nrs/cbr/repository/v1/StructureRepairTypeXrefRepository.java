package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeXrefEntity;
import ca.bc.gov.nrs.cbr.struct.v1.RepairTypeOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_REPAIR_TYPE_XREF} — the repair types that apply to a kind of structure. */
@Repository
public interface StructureRepairTypeXrefRepository
    extends JpaRepository<StructureRepairTypeXrefEntity, StructureRepairTypeXrefEntity.Key> {

  /**
   * The repair types for a structure type and class, each with its group, as legacy's
   * {@code FIND_REPAIR_TYPE_BY_CODES} lists them for all groups: ordered by group, then by the
   * type's order. A type with no {@code STRUCTURE_REPAIR_TYPE_ORDER} row is left out, as legacy's
   * inner join leaves it; an expired type is not, as legacy does not.
   *
   * <p>A type in two groups is listed once in each. The repair class, part of the key, is ignored
   * as legacy ignores it, so a type and group repeat once per class: the caller drops the repeats.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.struct.v1.RepairTypeOption(
               type.structureRepairTypeCode, type.description, typeOrder.structureRepairUnit,
               xref.id.structureRepairGroupCode)
        FROM StructureRepairTypeXrefEntity xref
        JOIN StructureRepairTypeCodeEntity type
          ON type.structureRepairTypeCode = xref.id.structureRepairTypeCode
        JOIN StructureRepairTypeOrderEntity typeOrder
          ON typeOrder.structureRepairTypeCode = xref.id.structureRepairTypeCode
       WHERE xref.id.structureTypeClassCode = :structureTypeClassCode
       ORDER BY xref.id.structureRepairGroupCode, typeOrder.structureRepairTypeOrder,
                type.structureRepairTypeCode
      """)
  List<RepairTypeOption> findTypesForStructureTypeClass(
      @Param("structureTypeClassCode") String structureTypeClassCode);
}
