package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.CROSSING_STRUCTURE_FILE} — the bytes of an attachment. */
@Repository
public interface CrossingStructureFileRepository
    extends JpaRepository<CrossingStructureFileEntity, Long> {

  /**
   * Deletes files by id, in one statement. Bulk rather than derived: a derived delete loads each
   * row first, and a row here is the whole file.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM CrossingStructureFileEntity file WHERE file.fileId IN :fileIds")
  void deleteByFileIdIn(@Param("fileIds") Collection<Long> fileIds);

  /**
   * The file row of a document whose bytes are in the object store: an empty BLOB, as
   * {@code STRUCTURE_FILE} is {@code NOT NULL} and every detail row has its file row. Native, for
   * Oracle's {@code EMPTY_BLOB()}: bound as a zero-length {@code byte[]}, an empty value may reach
   * Oracle as NULL.
   */
  @Modifying(flushAutomatically = true)
  @Query(value = "INSERT INTO THE.CROSSING_STRUCTURE_FILE (FILE_ID, STRUCTURE_FILE) "
      + "VALUES (:fileId, EMPTY_BLOB())", nativeQuery = true)
  void insertEmpty(@Param("fileId") long fileId);

  /**
   * The file, its row locked until the transaction ends ({@code SELECT … FOR UPDATE}) — so its
   * bytes cannot change between {@code FILE_PURGE} checking them against the object store and
   * emptying them. DELETE-AFTER-FILE-MIGRATION.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT file FROM CrossingStructureFileEntity file WHERE file.fileId = :fileId")
  Optional<CrossingStructureFileEntity> findLockedByFileId(@Param("fileId") long fileId);

  /**
   * Empties the file's bytes, keeping its row — {@code FILE_PURGE}, once the object store's copy is
   * proved the same. Native, for Oracle's {@code EMPTY_BLOB()}, as {@link #insertEmpty}.
   * DELETE-AFTER-FILE-MIGRATION.
   *
   * @return the rows emptied: 1, or 0 when the file is gone
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = "UPDATE THE.CROSSING_STRUCTURE_FILE SET STRUCTURE_FILE = EMPTY_BLOB() "
      + "WHERE FILE_ID = :fileId", nativeQuery = true)
  int emptyFile(@Param("fileId") long fileId);
}
