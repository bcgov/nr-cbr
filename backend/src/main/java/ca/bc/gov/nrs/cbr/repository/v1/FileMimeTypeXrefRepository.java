package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.FileMimeTypeXrefEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.FILE_MIME_TYPE_XREF} — the extensions a document may have. Checked on upload, as
 * legacy's "File Type is invalid"; the upload dialog also checks a file the moment it is picked,
 * without listing the types, as legacy lists them nowhere.
 */
@Repository
public interface FileMimeTypeXrefRepository
    extends JpaRepository<FileMimeTypeXrefEntity, FileMimeTypeXrefEntity.Key> {

  /** The extensions a document may have, upper case, in order — for the dialog's early check. */
  @Query("""
      SELECT DISTINCT xref.id.efileExtensionCode
        FROM FileMimeTypeXrefEntity xref
       ORDER BY xref.id.efileExtensionCode
      """)
  List<String> findExtensions();

  /**
   * The MIME type codes an extension is stored under, in order; the first is used. Empty when the
   * extension is not accepted.
   */
  @Query("""
      SELECT xref.id.fileMimeTypeCode
        FROM FileMimeTypeXrefEntity xref
       WHERE xref.id.efileExtensionCode = :extension
       ORDER BY xref.id.fileMimeTypeCode
      """)
  List<String> findMimeTypeCodes(@Param("extension") String extension);
}
