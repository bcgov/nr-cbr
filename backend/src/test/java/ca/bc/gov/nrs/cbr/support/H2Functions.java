package ca.bc.gov.nrs.cbr.support;

/**
 * Oracle functions the native queries call, for the embedded test database — registered as H2
 * aliases in {@code schema.sql}.
 */
public final class H2Functions {

  private H2Functions() {
  }

  /** Oracle's {@code EMPTY_BLOB()}: a BLOB of no bytes, which is not NULL. */
  public static byte[] emptyBlob() {
    return new byte[0];
  }
}
