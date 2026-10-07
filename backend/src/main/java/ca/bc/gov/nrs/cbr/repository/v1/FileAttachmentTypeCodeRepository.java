package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.FileAttachmentTypeCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.FILE_ATTACHMENT_TYPE_CODE} — decodes attachment types on Documents &amp; Photos. */
@Repository
public interface FileAttachmentTypeCodeRepository
    extends JpaRepository<FileAttachmentTypeCodeEntity, String> {}
