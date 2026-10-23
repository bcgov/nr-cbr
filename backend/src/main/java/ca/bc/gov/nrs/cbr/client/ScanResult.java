package ca.bc.gov.nrs.cbr.client;

/**
 * What clamd said about one file.
 *
 * @param status    clean, infected, or no verdict
 * @param signature the virus found — only when {@link Status#INFECTED}
 * @param detail    clamd's reply, or why there was none, for the log
 */
public record ScanResult(Status status, String signature, String detail) {

  /** A scan's outcome. */
  public enum Status {
    /** clamd answered {@code OK}. */
    CLEAN,
    /** clamd answered {@code FOUND}. */
    INFECTED,
    /** clamd errored, timed out or could not be reached: no verdict. */
    ERROR
  }

  public static ScanResult clean() {
    return new ScanResult(Status.CLEAN, null, "OK");
  }

  public static ScanResult infected(String signature, String detail) {
    return new ScanResult(Status.INFECTED, signature, detail);
  }

  public static ScanResult error(String detail) {
    return new ScanResult(Status.ERROR, null, detail);
  }
}
