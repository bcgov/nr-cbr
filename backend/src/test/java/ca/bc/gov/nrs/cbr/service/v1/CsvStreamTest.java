package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link CsvStream}: the format the reports' CSVs have always had, written as it is made. */
class CsvStreamTest {

  @Test
  @DisplayName("writes the header, then each row, quoted where a value needs it, in UTF-8")
  void writesTheCsv() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (CsvStream csv = CsvStream.open(out, List.of("Name", "Comment"))) {
      csv.row(List.of("B1", "plain"));
      csv.rows(List.of(List.of("B2", "a, \"quoted\" é")));
      assertThat(csv.count()).isEqualTo(2);
    }

    assertThat(out.toString(StandardCharsets.UTF_8))
        .isEqualTo("Name,Comment\r\nB1,plain\r\nB2,\"a, \"\"quoted\"\" é\"\r\n");
  }

  @Test
  @DisplayName("sends rows on as it goes, not only when it is closed")
  void sendsAsItGoes() {
    int[] flushes = new int[1];
    OutputStream out = new ByteArrayOutputStream() {
      @Override
      public void flush() {
        flushes[0]++;
      }
    };
    try (CsvStream csv = CsvStream.open(out, List.of("N"))) {
      IntStream.range(0, CsvStream.FLUSH_EVERY * 2).forEach(i -> csv.row(List.of("" + i)));
      assertThat(flushes[0]).isGreaterThanOrEqualTo(2);
    }
  }

  @Test
  @DisplayName("writes to memory the same as to a response, for a caller that needs the file")
  void inMemory() {
    ReportFile file = ReportTarget.inMemory(target -> {
      try (CsvStream csv = StructureInventoryReportSupport.csv(target, "x.csv", List.of("N"))) {
        csv.row(List.of("1"));
      }
    });

    assertThat(file.filename()).isEqualTo("x.csv");
    assertThat(file.mediaType()).hasToString("text/csv");
    assertThat(new String(file.content(), StandardCharsets.UTF_8)).isEqualTo("N\r\n1\r\n");
  }
}
