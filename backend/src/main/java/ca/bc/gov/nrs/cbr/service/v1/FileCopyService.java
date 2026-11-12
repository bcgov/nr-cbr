package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.BatchJobItemStatus;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.repository.v1.BatchJobItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileDetailRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import ca.bc.gov.nrs.cbr.util.DocumentMediaTypes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * {@code FILE_COPY}, one file at a time: its {@code CROSSING_STRUCTURE_FILE} BLOB to the object
 * store, at {@link ObjectStorageService#fileKey} — the key the download reads.
 *
 * <p>Each copy is checked byte for byte: the MD5 goes up as {@code Content-MD5}, which the gateway
 * checks, and comes back as the ETag, which this checks. A file already in the store with the
 * right ETag is not uploaded again — so a file handed out twice (by two pods, or two firings) costs
 * a BLOB read and a HEAD, not a second upload.
 *
 * <p>Only files with bytes are listed. Files uploaded through CBR already have their object and an
 * empty BLOB, so nothing CBR stored is ever overwritten.
 *
 * <p>DELETE-AFTER-FILE-MIGRATION, once the gate has passed after legacy stopped writing.
 */
@Service
public class FileCopyService {

  public static final String JOB_NAME = "FILE_COPY";

  private static final Logger log = LoggerFactory.getLogger(FileCopyService.class);

  private final CrossingStructureFileRepository files;
  private final CrossingStructureFileDetailRepository details;
  private final BatchJobItemRepository items;
  private final ObjectStorageService storage;

  public FileCopyService(CrossingStructureFileRepository files,
      CrossingStructureFileDetailRepository details, BatchJobItemRepository items,
      ObjectStorageService storage) {
    this.files = files;
    this.details = details;
    this.items = items;
    this.storage = storage;
  }

  /**
   * Copies the file on the {@code fileCopyTaskExecutor} and records the outcome on its item. The
   * future completes once the outcome is saved, exceptionally if saving it failed (the item then
   * stays {@code READY} and is handed out again).
   */
  @Async("fileCopyTaskExecutor")
  public CompletableFuture<BatchJobItemStatus> copyFile(long fileId) {
    BatchJobItemStatus status;
    try {
      status = copy(fileId);
    } catch (RuntimeException failed) {
      log.error("{}: file {} failed", JOB_NAME, fileId, failed);
      status = BatchJobItemStatus.ERROR;
    }
    items.updateStatus(JOB_NAME, fileId, status, LocalDateTime.now());
    return CompletableFuture.completedFuture(status);
  }

  /** The copy itself: {@code DONE} or {@code ERROR}, the cause of an error logged here. */
  BatchJobItemStatus copy(long fileId) {
    Optional<CrossingStructureFileDetailEntity> detail = details.findById(fileId);
    byte[] content = files.findById(fileId)
        .map(CrossingStructureFileEntity::getStructureFile)
        .orElse(null);
    if (detail.isEmpty() || content == null || content.length == 0) {
      // Deleted since it was listed, or emptied: nothing left to copy.
      log.debug("{}: file {} has no bytes any more; nothing to copy", JOB_NAME, fileId);
      return BatchJobItemStatus.DONE;
    }
    String key = ObjectStorageService.fileKey(fileId);
    byte[] md5 = md5(content);
    String expected = HexFormat.of().formatHex(md5);
    if (storage.objectEtag(key).map(FileCopyService::unquoted)
        .filter(expected::equalsIgnoreCase).isPresent()) {
      log.debug("{}: file {} is already in the store", JOB_NAME, fileId);
      return BatchJobItemStatus.DONE;
    }

    String contentType = DocumentMediaTypes
        .of(detail.get().getFilename(), detail.get().getEfileExtensionCode()).toString();
    String etag;
    try {
      etag = storage.putObjectWithMd5(key, contentType, content, md5);
    } catch (SdkException failed) {
      log.error("{}: file {} ({} bytes, {}) could not be stored{}", JOB_NAME, fileId,
          content.length, contentType, gatewayError(failed), failed);
      return BatchJobItemStatus.ERROR;
    }
    if (!expected.equalsIgnoreCase(unquoted(etag))) {
      log.error("{}: file {} ({} bytes) stored with ETag {}, but its MD5 is {}", JOB_NAME, fileId,
          content.length, etag, expected);
      return BatchJobItemStatus.ERROR;
    }
    if (!details.existsById(fileId)) {
      // Deleted while it was being copied: its delete removed the object before this put it back.
      storage.deleteObject(key);
      log.debug("{}: file {} was deleted while being copied; its object removed", JOB_NAME,
          fileId);
    }
    return BatchJobItemStatus.DONE;
  }

  private static String unquoted(String etag) {
    return etag == null ? null : etag.replace("\"", "");
  }

  /**
   * The gateway's own account of a failure, for the log. A 403 alone could be a bad Content-Type
   * ({@code AccessDenied}), a full bucket ({@code QuotaExceeded}), throttling ({@code SlowDown}) or
   * the clock ({@code RequestTimeTooSkewed}).
   */
  private static String gatewayError(SdkException failed) {
    if (failed instanceof AwsServiceException service && service.awsErrorDetails() != null) {
      return ": " + service.awsErrorDetails().errorCode() + " (HTTP " + service.statusCode()
          + ", request " + service.requestId() + ")";
    }
    return "";
  }

  private static byte[] md5(byte[] content) {
    try {
      return MessageDigest.getInstance("MD5").digest(content);
    } catch (NoSuchAlgorithmException absent) {
      throw new IllegalStateException("Every Java runtime has MD5", absent);
    }
  }
}
