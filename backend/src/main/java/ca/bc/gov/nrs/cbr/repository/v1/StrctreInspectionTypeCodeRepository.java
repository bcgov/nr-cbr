package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.STRCTRE_INSPECTION_TYPE_CODE}.
 */
@Repository
public interface StrctreInspectionTypeCodeRepository
    extends JpaRepository<StrctreInspectionTypeCodeEntity, String> {

  /**
   * Every inspection type, by description.
   *
   * <p>This is {@code CBR.FIND_INSPECTION_TYPE_CODES}, and it is the one lookup here that does not
   * match its procedure literally: <b>that procedure has no {@code ORDER BY} at all.</b> Its rows
   * arrive in whatever order Oracle returns them, which is not an order anyone chose and not one
   * the database promises to keep — the same query can come back differently after the table is
   * reorganised. So there is no legacy ordering to preserve, and sorting is a choice rather than a
   * change: by description, matching every other code list on the form that has no {@code _XREF}
   * sibling to order it.
   *
   * <p>No filter on {@code EFFECTIVE_DATE}/{@code EXPIRY_DATE}, which here <em>is</em> literal:
   * {@code InspectionSearchAction} asks for this list with the expiry filter switched off, because
   * inspections recorded years ago still carry types that have since been retired and filtering
   * would make them unfindable by type. A data-entry form is the opposite case and needs the
   * filtered query — which does not exist yet, and should be added as its own method rather than by
   * narrowing this one.
   */
  List<StrctreInspectionTypeCodeEntity> findAllByOrderByDescriptionAsc();
}
