package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * {@code FILE_PURGE}, one file at a time: empties its {@code CROSSING_STRUCTURE_FILE} BLOB once the
 * object store's copy is proved the same, byte for byte. The row stays — every detail row keeps its
 * file row — with an empty BLOB, as a file uploaded through CBR has.
 *
 * <p><b>The proof, in one transaction.</b> The file's row is locked ({@code SELECT … FOR UPDATE}),
 * its bytes read and their MD5 compared with the object's ETag (a single-part upload's ETag is its
 * MD5), and only on a match is the BLOB emptied and the lock released by the commit. Nothing can
 * change the bytes between the check and the emptying. No object, or a different one, is an
 * {@code ERROR} and the BLOB is left as it was.
 *
 * <p>Only files {@code FILE_COPY} has {@code DONE} are listed, and only once its gate would pass —
 * see {@code FilePurgeTaskSchedulerService}. Emptying a BLOB cannot be undone: after it, the object
 * store holds the only copy.
 *
 * <p>DELETE-AFTER-FILE-MIGRATION.
 */
@Service
public class FilePurgeService {

  public static final String JOB_NAME = "FILE_PURGE";

  private static final Logger log = LoggerFactory.getLogger(FilePurgeService.class);

  private final CrossingStructureFileRepository files;
  private final BatchJobItemRepository items;
  private final ObjectStorageService storage;
  private final TransactionTemplate transaction;

  public FilePurgeService(CrossingStructureFileRepository files, BatchJobItemRepository items,
      ObjectStorageService storage, PlatformTransactionManager transactionManager) {
    this.files = files;
    this.items = items;
    this.storage = storage;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /**
   * Purges the file on the {@code filePurgeTaskExecutor} and records the outcome on its item. The
   * future completes once the outcome is saved, exceptionally if saving it failed (the item then
   * stays {@code READY} and is handed out again).
   */
  @Async("filePurgeTaskExecutor")
  public CompletableFuture<BatchJobItemStatus> purgeFile(long fileId) {
    BatchJobItemStatus status;
    try {
      status = purge(fileId);
    } catch (RuntimeException failed) {
      log.error("{}: file {} failed; its BLOB is kept{}", JOB_NAME, fileId,
          failed instanceof SdkException sdk ? FileCopyService.gatewayError(sdk) : "", failed);
      status = BatchJobItemStatus.ERROR;
    }
    items.updateStatus(JOB_NAME, fileId, status, LocalDateTime.now(ZoneId.systemDefault()));
    return CompletableFuture.completedFuture(status);
  }

  /**
   * The purge itself, in its own transaction: {@code DONE} when the BLOB was emptied or had
   * nothing to empty, {@code ERROR} when the object store's copy is missing or different. A
   * failure — the store unreachable, say — rolls back and throws.
   */
  BatchJobItemStatus purge(long fileId) {
    return transaction.execute(started -> purgeLocked(fileId));
  }

  private BatchJobItemStatus purgeLocked(long fileId) {
    Optional<CrossingStructureFileEntity> file = files.findLockedByFileId(fileId);
    byte[] content = file.map(CrossingStructureFileEntity::getStructureFile).orElse(null);
    if (content == null || content.length == 0) {
      // Deleted since it was copied, or already empty: nothing to empty.
      log.debug("{}: file {} has no bytes; nothing to empty", JOB_NAME, fileId);
      return BatchJobItemStatus.DONE;
    }
    String key = ObjectStorageService.fileKey(fileId);
    String expected = HexFormat.of().formatHex(FileCopyService.md5(content));
    Optional<String> etag = storage.objectEtag(key).map(FileCopyService::unquoted);
    if (etag.isEmpty()) {
      log.error("{}: file {} ({} bytes) has no object at {}; its BLOB is kept", JOB_NAME, fileId,
          content.length, key);
      return BatchJobItemStatus.ERROR;
    }
    if (!expected.equalsIgnoreCase(etag.get())) {
      log.error("{}: file {} ({} bytes) has MD5 {}, but its object's ETag is {}; its BLOB is kept",
          JOB_NAME, fileId, content.length, expected, etag.get());
      return BatchJobItemStatus.ERROR;
    }
    files.emptyFile(fileId);
    log.debug("{}: file {} ({} bytes) emptied; its object holds it", JOB_NAME, fileId,
        content.length);
    return BatchJobItemStatus.DONE;
  }
}
