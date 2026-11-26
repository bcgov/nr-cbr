package ca.bc.gov.nrs.cbr.service.v1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;
import org.springframework.http.MediaType;

/**
 * Where a report is written as it is made: the HTTP response, for a download, or memory.
 *
 * <p>Opened once, by the report, when it is ready to write — after anything that can fail on the
 * criteria — so a request that fails before then still gets an ordinary error response rather
 * than a download's headers and nothing after them.
 */
@FunctionalInterface
public interface ReportTarget {

  /**
   * The stream to write the report to, for a file of this name and type.
   *
   * @param filename  e.g. {@code InspectionsConductedDetailReport_2026-11-26.csv}
   * @param mediaType e.g. {@code text/csv}
   */
  OutputStream open(String filename, MediaType mediaType) throws IOException;

  /**
   * A report written to memory, as a {@link ReportFile} — for a caller that needs the whole file,
   * and the tests.
   */
  static ReportFile inMemory(Consumer<ReportTarget> report) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    String[] filename = new String[1];
    MediaType[] mediaType = new MediaType[1];
    report.accept((name, type) -> {
      filename[0] = name;
      mediaType[0] = type;
      return bytes;
    });
    return new ReportFile(bytes.toByteArray(), filename[0], mediaType[0]);
  }
}
