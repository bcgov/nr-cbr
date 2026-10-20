package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReportFileTest {

  private static ReportFile file(String content, String name) {
    return new ReportFile(content.getBytes(), name, MediaType.APPLICATION_PDF);
  }

  @Test
  @DisplayName("is equal by the file's contents, not the array's identity")
  void equalByContents() {
    assertThat(file("%PDF", "a.pdf")).isEqualTo(file("%PDF", "a.pdf"))
        .hasSameHashCodeAs(file("%PDF", "a.pdf"))
        .isNotEqualTo(file("%PDF-1.7", "a.pdf"))
        .isNotEqualTo(file("%PDF", "b.pdf"));
  }

  @Test
  @DisplayName("prints the file's size, not its bytes")
  void printsSize() {
    assertThat(file("%PDF", "a.pdf")).hasToString(
        "ReportFile[filename=a.pdf, mediaType=application/pdf, content=4 bytes]");
  }
}
