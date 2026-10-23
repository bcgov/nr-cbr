package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.configuration.ObjectStorageProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * The documents and photos' files in the NRS object store — a bucket of bytes, nothing more.
 *
 * <p>Oracle stays the index: {@code CROSSING_STRUCTURE_FILE_DETAIL} says what a file is and whose,
 * and the object's key is computed from its {@code FILE_ID} by {@link #fileKey}. Nothing lists the
 * bucket to find a file. Copied from nr-frep's, with one client built at startup and closed on
 * shutdown rather than one per call.
 *
 * <p>Only a missing object is "not there". Any other failure — the gateway down, a bad key or
 * signature — propagates: nr-frep once read every failure as missing and served a not-yet-migrated
 * file's empty BLOB in its place.
 */
@Service
public class ObjectStorageService implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

  /** Every document's object, structure's and inspection's alike — see {@link #fileKey}. */
  private static final String FILE_PREFIX = "docs/";

  private final String bucket;
  /** Null when {@code cbr.object-storage} is not set, as locally. */
  private final S3Client client;

  public ObjectStorageService(ObjectStorageProperties properties) {
    this.bucket = properties.bucket();
    if (properties.configured()) {
      this.client = client(properties);
      log.info("Object storage: bucket {} at {}", properties.bucket(), properties.host());
    } else {
      this.client = null;
      log.warn("Object storage is not configured (cbr.object-storage); document files are "
          + "unavailable");
    }
  }

  /**
   * The object key for a document's file: {@code docs/<FILE_ID>}. <b>The single definition</b> —
   * the upload, the download, the deletes and the BLOB migration all resolve their key here.
   *
   * <p>They must agree exactly, and a disagreement would not look like a bug: while downloads fall
   * back to the BLOB for a missing object, a migration writing to a key the download never reads
   * passes every check and orphans every object, until the fallback is removed.
   */
  public static String fileKey(long fileId) {
    return FILE_PREFIX + fileId;
  }

  /** Stores {@code content} at {@code key}, replacing any object there. */
  public void putObject(String key, String contentType, byte[] content) {
    // The Content-Type is signed: it must be a real media type, not legacy's MIME code or its
    // description, or the gateway answers 403.
    client().putObject(
        builder -> builder.bucket(bucket).key(key).contentType(contentType),
        RequestBody.fromBytes(content));
  }

  /** The object's bytes; {@link NoSuchKeyException} when there is none. */
  public byte[] getObjectBytes(String key) {
    return client().getObjectAsBytes(builder -> builder.bucket(bucket).key(key)).asByteArray();
  }

  /**
   * The object's bytes, or {@code null} when there is none — the download's one round trip, with
   * the BLOB to fall back to while files are being migrated.
   */
  public byte[] getObjectBytesIfPresent(String key) {
    try {
      return getObjectBytes(key);
    } catch (NoSuchKeyException missing) {
      return null;
    }
  }

  /** Whether the object is there, without fetching it — the migration's "already done?". */
  public boolean objectExists(String key) {
    try {
      client().headObject(builder -> builder.bucket(bucket).key(key));
      return true;
    } catch (NoSuchKeyException missing) {
      return false;
    }
  }

  /** Deletes the object; nothing happens when there is none. */
  public void deleteObject(String key) {
    client().deleteObject(builder -> builder.bucket(bucket).key(key));
  }

  @Override
  public void destroy() {
    if (client != null) {
      client.close();
    }
  }

  private S3Client client() {
    if (client == null) {
      throw new IllegalStateException(
          "Object storage is not configured: set OBJECT_STORAGE_HOST, OBJECT_STORAGE_BUCKET, "
              + "OBJECT_STORAGE_ACCESS_KEY and OBJECT_STORAGE_SECRET_KEY");
    }
    return client;
  }

  static S3Client client(ObjectStorageProperties properties) {
    return S3Client.builder()
        // The gateway ignores the region, but SigV4 signs one.
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create(properties.host()))
        // host/bucket/key, not bucket.host/key: the gateway has no per-bucket hostnames.
        .forcePathStyle(true)
        // AWS SDK 2.30+ adds CRC32 checksums and aws-chunked trailers to every request by default.
        // The NRS gateway rejects them with a 400 "Content-SHA256 did not match", so send and check
        // checksums only where an operation requires one.
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
        .build();
  }
}
