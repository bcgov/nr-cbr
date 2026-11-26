package ca.bc.gov.nrs.cbr.service.v1;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * A CSV written row by row to a stream, as it is made — not assembled in memory first.
 *
 * <p>A province-wide report runs to hundreds of thousands of rows (Inspections Conducted – Detail:
 * 278,618, 165 MB). Built as a string and then as bytes, the file alone took several times its
 * size in a pod with about 480 MB of heap. Written here, what the report holds at once is what it
 * reads at once.
 *
 * <p>Sent on every {@value #FLUSH_EVERY} rows, so the browser receives the file as it is written
 * and the response buffer never holds more than a few hundred rows.
 *
 * <p>The same format as {@link StructureInventoryReportSupport#csv}: header first, RFC 4180, UTF-8.
 */
public final class CsvStream implements AutoCloseable {

  /** Rows between sends to the client. */
  static final int FLUSH_EVERY = 500;

  private final CSVPrinter printer;
  private int rows;

  private CsvStream(CSVPrinter printer) {
    this.printer = printer;
  }

  /** A CSV on this stream, its header written. */
  public static CsvStream open(OutputStream out, List<String> header) {
    try {
      CSVPrinter printer = new CSVPrinter(
          new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)),
          CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).build());
      return new CsvStream(printer);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** One row, in the header's order. */
  public void row(List<String> values) {
    try {
      printer.printRecord(values);
      if (++rows % FLUSH_EVERY == 0) {
        printer.flush();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** Every row of a list, in order. */
  public void rows(List<? extends List<String>> values) {
    values.forEach(this::row);
  }

  /** Rows written so far, the header not counted. */
  public int count() {
    return rows;
  }

  /** Sends what is left and closes the stream. */
  @Override
  public void close() {
    try {
      printer.close(true);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
