package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_REPLACEMENT_XREF} — the structure page's Replacement History. */
@Repository
public interface StructureReplacementXrefRepository
    extends JpaRepository<StructureReplacementXrefEntity, StructureReplacementXrefEntity.Key> {

  /** Links where this structure replaced another — legacy's {@code FIND_REPLACING_STRUCTURES}. */
  List<StructureReplacementXrefEntity> findByReplacesStructureNumber(Long structureId);

  /** Links where another replaced this one — legacy's {@code FIND_REPLACED_BY_STRUCTURES}. */
  List<StructureReplacementXrefEntity> findByReplacedStructureNumber(Long structureId);
}
