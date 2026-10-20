package ca.bc.gov.nrs.cbr.service.v1;

import org.springframework.http.MediaType;

/**
 * A produced report: its bytes, the name it downloads under, and its type.
 *
 * @param content   the PDF or CSV
 * @param filename  e.g. {@code StructureInventorySummaryReport_2026-10-20.pdf}
 * @param mediaType {@code application/pdf} or {@code text/csv}
 */
public record ReportFile(byte[] content, String filename, MediaType mediaType) {}
