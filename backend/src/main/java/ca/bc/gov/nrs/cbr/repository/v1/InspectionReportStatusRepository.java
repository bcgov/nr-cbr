package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.INSPECTION_REPORT_STATUS} — an inspection's status history.
 *
 * <p>The application role holds {@code SELECT, INSERT, DELETE} on this table and <b>no
 * {@code UPDATE}</b> (cbr-auth-and-roles.local.md §9), which matches what it is: rows are appended
 * as transitions happen and removed only with the inspection they belong to. Nothing rewrites
 * history.
 */
@Repository
public interface InspectionReportStatusRepository
    extends JpaRepository<InspectionReportStatusEntity, Long> {

  void deleteByInspectionId(Long inspectionId);
}
