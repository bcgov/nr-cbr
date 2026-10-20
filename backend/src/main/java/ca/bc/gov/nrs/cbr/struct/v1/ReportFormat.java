package ca.bc.gov.nrs.cbr.struct.v1;

import org.springframework.http.MediaType;

/** What a report is produced as: a PDF to print, or a CSV of the same rows to work with. */
public enum ReportFormat {
  PDF(MediaType.APPLICATION_PDF, "pdf"),
  CSV(new MediaType("text", "csv"), "csv");

  private final MediaType mediaType;
  private final String extension;

  ReportFormat(MediaType mediaType, String extension) {
    this.mediaType = mediaType;
    this.extension = extension;
  }

  public MediaType mediaType() {
    return mediaType;
  }

  public String extension() {
    return extension;
  }
}
