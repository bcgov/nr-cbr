/**
 * Shapes shared by every paginated endpoint.
 *
 * <p>Here rather than beside the screen that first needed one, for the same reason
 * `@/types/configuration` exists: both search screens speak this envelope, and a copy per screen is
 * two definitions that can drift from each other and from the backend's `PagedResponse`.
 */

/**
 * One page of results plus the true total. Mirrors the backend's `PagedResponse`.
 *
 * <p>`pageNumber` is zero-based, as Spring Data's is. Carbon's `Pagination` is one-based, so each
 * page component converts at its own edge rather than either side pretending otherwise.
 */
export type PagedResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
};
