package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_REPAIR_TYPE_CODE} — read by the structure page's Repairs tab. */
@Repository
public interface StructureRepairTypeCodeRepository
    extends JpaRepository<StructureRepairTypeCodeEntity, String> {}
