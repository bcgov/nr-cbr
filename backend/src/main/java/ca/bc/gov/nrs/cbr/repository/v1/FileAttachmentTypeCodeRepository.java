package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.FileAttachmentTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.FILE_ATTACHMENT_TYPE_CODE} — decodes attachment types on Documents &amp; Photos. */
@Repository
public interface FileAttachmentTypeCodeRepository
    extends JpaRepository<FileAttachmentTypeCodeEntity, String> {

  /** The current types, by description — the upload's and the edit's "Photo View" list. */
  @Query("""
      SELECT code
        FROM FileAttachmentTypeCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<FileAttachmentTypeCodeEntity> findAllCurrent();
}
