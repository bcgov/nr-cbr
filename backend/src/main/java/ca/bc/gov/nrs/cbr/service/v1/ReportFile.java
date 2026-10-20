package ca.bc.gov.nrs.cbr.service.v1;

import java.util.Arrays;
import java.util.Objects;
import org.springframework.http.MediaType;

/**
 * A produced report: its bytes, the name it downloads under, and its type.
 *
 * <p>Equality and {@code toString} by the file's contents rather than the array's identity, as a
 * record would otherwise have them — as on {@link StructureDocumentsService.DocumentFile};
 * {@code toString} gives the size, not the bytes.
 *
 * @param content   the PDF or CSV
 * @param filename  e.g. {@code StructureInventorySummaryReport_2026-10-20.pdf}
 * @param mediaType {@code application/pdf} or {@code text/csv}
 */
public record ReportFile(byte[] content, String filename, MediaType mediaType) {

  @Override
  public boolean equals(Object other) {
    return other instanceof ReportFile(
            byte[] otherContent, String otherName, MediaType otherType)
        && Arrays.equals(content, otherContent)
        && Objects.equals(filename, otherName)
        && Objects.equals(mediaType, otherType);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(filename, mediaType) + Arrays.hashCode(content);
  }

  @Override
  public String toString() {
    return "ReportFile[filename=" + filename + ", mediaType=" + mediaType + ", content="
        + (content == null ? "null" : content.length + " bytes") + "]";
  }
}
