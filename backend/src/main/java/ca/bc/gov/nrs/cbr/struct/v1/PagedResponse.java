package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * Generic page envelope for server-side paginated endpoints.
 *
 * <p>Mirrors the shape of Spring Data's {@code Page} without coupling the JSON contract to it — the
 * repository layer here calls PL/SQL, not Spring Data, and the wire format should not change if that
 * ever swaps.
 *
 * @param content       the rows for this page
 * @param totalElements total matching rows across all pages
 * @param totalPages    total page count
 * @param pageNumber    zero-based page index of this page
 * @param pageSize      requested page size
 */
public record PagedResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int pageNumber,
    int pageSize
) {

  /** An empty page, preserving the caller's paging request. */
  public static <T> PagedResponse<T> empty(int pageNumber, int pageSize) {
    return new PagedResponse<>(List.of(), 0L, 0, pageNumber, pageSize);
  }
}
