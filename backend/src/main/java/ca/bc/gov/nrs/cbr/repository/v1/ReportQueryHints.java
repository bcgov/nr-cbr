package ca.bc.gov.nrs.cbr.repository.v1;

/**
 * Query hints for the queries a report runs: its main query, and those it runs a batch at a time.
 *
 * <p>A province-wide report reads hundreds of thousands of rows — the Inspections Conducted –
 * Detail report's items alone are about 278,000. At the application's default fetch size of 500
 * that was some 560 round trips to Oracle for the items, 20 s of the report's 71 s from a
 * workstation. Fetched 5,000 at a time it is a tenth as many, at the cost of a larger fetch buffer
 * while each query runs.
 */
public final class ReportQueryHints {

  private ReportQueryHints() {}

  /** Rows fetched per round trip by a report's batch queries. */
  public static final String FETCH_SIZE = "5000";

  /** {@link #FETCH_SIZE} as a number, for a query's {@code setHint}. Keep the two equal. */
  public static final int FETCH_ROWS = 5000;
}
