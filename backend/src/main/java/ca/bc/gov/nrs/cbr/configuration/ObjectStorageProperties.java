package ca.bc.gov.nrs.cbr.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code cbr.object-storage} — the S3-compatible NRS object store that holds the documents and
 * photos' files.
 *
 * <p>Required in every deployed environment ({@code OBJECT_STORAGE_*} in
 * {@code openshift.deploy.yml}); usually unset locally, where {@link #configured()} is false and
 * the storage service refuses any call rather than the app refusing to start.
 *
 * @param host      the gateway's URL, scheme included (e.g. {@code https://nrs.objectstore.gov.bc.ca})
 * @param bucket    the bucket's name
 * @param accessKey the bucket's access key id
 * @param secretKey the bucket's secret access key
 */
@ConfigurationProperties(prefix = "cbr.object-storage")
public record ObjectStorageProperties(
    String host, String bucket, String accessKey, String secretKey) {

  /** Whether all four are set. */
  public boolean configured() {
    return isSet(host) && isSet(bucket) && isSet(accessKey) && isSet(secretKey);
  }

  /** The secret key is never printed, nor the access key with it. */
  @Override
  public String toString() {
    return "ObjectStorageProperties[host=" + host + ", bucket=" + bucket + "]";
  }

  private static boolean isSet(String value) {
    return value != null && !value.isBlank();
  }
}
