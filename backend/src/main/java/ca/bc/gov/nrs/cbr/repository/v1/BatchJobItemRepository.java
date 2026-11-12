package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code THE.CBR_BATCH_JOB_ITEM} — the scheduled batch jobs' work items.
 *
 * <p>Every write is its own transaction: an item's status is saved as soon as it is processed, and
 * the purge commits chunk by chunk.
 */
@Repository
public interface BatchJobItemRepository
    extends JpaRepository<BatchJobItemEntity, BatchJobItemEntity.Key> {

  /** A status and how many of a job's items have it. */
  interface StatusCount {
    BatchJobItemStatus getStatus();

    long getTotal();
  }

  /** The job's {@code READY} items above {@code after}, lowest first. */
  @Query("""
      SELECT i.itemId FROM BatchJobItemEntity i
       WHERE i.jobName = :jobName AND i.status = :status AND i.itemId > :after
       ORDER BY i.itemId
       LIMIT :limit""")
  List<Long> findItemIds(@Param("jobName") String jobName,
      @Param("status") BatchJobItemStatus status, @Param("after") long after,
      @Param("limit") int limit);

  /** The highest id the job has in {@code status}, if any. */
  @Query("SELECT MAX(i.itemId) FROM BatchJobItemEntity i "
      + "WHERE i.jobName = :jobName AND i.status = :status")
  Optional<Long> findHighestItemId(@Param("jobName") String jobName,
      @Param("status") BatchJobItemStatus status);

  /** Records an item's outcome. */
  @Transactional
  @Modifying
  @Query("""
      UPDATE BatchJobItemEntity i SET i.status = :status, i.updateTimestamp = :now
       WHERE i.jobName = :jobName AND i.itemId = :itemId""")
  int updateStatus(@Param("jobName") String jobName, @Param("itemId") long itemId,
      @Param("status") BatchJobItemStatus status, @Param("now") LocalDateTime now);

  /** The job's items by status; a status it has none of is absent. */
  @Query("""
      SELECT i.status AS status, COUNT(i) AS total FROM BatchJobItemEntity i
       WHERE i.jobName = :jobName GROUP BY i.status""")
  List<StatusCount> countByStatus(@Param("jobName") String jobName);

  /** How many of the job's items reached {@code status} after {@code since} — the pace. */
  long countByJobNameAndStatusAndUpdateTimestampAfter(String jobName, BatchJobItemStatus status,
      LocalDateTime since);

  /** When the job's rows last changed; empty when it has none. */
  @Query("SELECT MAX(i.updateTimestamp) FROM BatchJobItemEntity i WHERE i.jobName = :jobName")
  Optional<LocalDateTime> findLastUpdateTimestamp(@Param("jobName") String jobName);

  /** The job's lowest item id; empty when it has none. */
  @Query("SELECT MIN(i.itemId) FROM BatchJobItemEntity i WHERE i.jobName = :jobName")
  Optional<Long> findLowestItemId(@Param("jobName") String jobName);

  /** Deletes the job's items below {@code below} — one chunk of the purge. */
  @Transactional
  @Modifying
  @Query("DELETE FROM BatchJobItemEntity i WHERE i.jobName = :jobName AND i.itemId < :below")
  int deleteByJobNameAndItemIdBelow(@Param("jobName") String jobName,
      @Param("below") long below);

  /**
   * {@code FILE_COPY}'s fill: a {@code READY} item for every file whose BLOB holds bytes and that
   * has no item yet. The full anti-join rather than "above the highest id": the id sequence is
   * shared, so a lower id can commit after a higher one. DELETE-AFTER-FILE-MIGRATION.
   */
  @Transactional
  @Modifying
  @Query(value = """
      INSERT INTO THE.CBR_BATCH_JOB_ITEM (JOB_NAME, ITEM_ID, STATUS, UPDATE_TIMESTAMP)
      SELECT 'FILE_COPY', f.FILE_ID, 'READY', LOCALTIMESTAMP
        FROM THE.CROSSING_STRUCTURE_FILE f
       WHERE DBMS_LOB.GETLENGTH(f.STRUCTURE_FILE) > 0
         AND NOT EXISTS (SELECT 1 FROM THE.CBR_BATCH_JOB_ITEM i
                          WHERE i.JOB_NAME = 'FILE_COPY' AND i.ITEM_ID = f.FILE_ID)""",
      nativeQuery = true)
  int insertFileCopyItems();

  /**
   * {@code FILE_COPY}'s gate: files whose BLOB holds bytes and that have no {@code DONE} item —
   * listed or not, so a file never listed fails the gate too. DELETE-AFTER-FILE-MIGRATION.
   */
  @Query(value = """
      SELECT COUNT(*) FROM THE.CROSSING_STRUCTURE_FILE f
       WHERE DBMS_LOB.GETLENGTH(f.STRUCTURE_FILE) > 0
         AND NOT EXISTS (SELECT 1 FROM THE.CBR_BATCH_JOB_ITEM i
                          WHERE i.JOB_NAME = 'FILE_COPY' AND i.ITEM_ID = f.FILE_ID
                            AND i.STATUS = 'DONE')""",
      nativeQuery = true)
  long countFilesNotCopied();
}
