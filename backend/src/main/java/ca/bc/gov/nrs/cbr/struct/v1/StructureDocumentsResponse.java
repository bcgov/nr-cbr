package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import java.time.LocalDate;
import java.util.List;

/**
 * A structure's documents and photos, for the structure page's Documents &amp; Photos tab —
 * {@code GET /api/v1/structures/{structureId}/documents}; legacy's {@code documentTab.jsp}.
 *
 * <p>The frontend's {@code documentsResponse.ts} is the other half of this contract.
 *
 * @param documents in legacy's order: the structure's own first, then each inspection's, newest
 *                  inspection first; within each, newest file first
 */
public record StructureDocumentsResponse(List<Document> documents) {

  /**
   * One file's details; its bytes come from the file endpoint.
   *
   * @param inspectionId   the inspection it was attached to, or null for the structure's own
   * @param inspectionDate that inspection's date
   * @param attachmentType what the file shows or is — the link text
   * @param created        when the photo was taken or the document made
   * @param extension      {@code EFILE_EXTENSION_CODE}, e.g. {@code JPG}
   */
  public record Document(
      String id,
      String inspectionId,
      LocalDate inspectionDate,
      CodeValue attachmentType,
      LocalDate created,
      String extension,
      String description,
      String filename) {}
}
