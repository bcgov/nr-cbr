/**
 * The most useful human-readable sentence a failed API call carries.
 *
 * <p>CBR enables `spring.mvc.problemdetails`, so a backend error arrives as an RFC 7807 problem
 * detail and the sentence worth showing is in **`detail`**. That is where the value is: a 409 from
 * `SiteInUseException` names what is still referencing the site — an archived structure, a
 * close-proximity inspection — and neither is visible from the screen the user is on, so without it
 * they are told "could not delete" and have nowhere to go.
 *
 * <p>`message` is checked next because that is the shape nr-frep reads (`ResponseStatusException`
 * with `server.error.include-message` enabled, no problem details), and this util came from there.
 * Anything that reaches CBR through a different Spring configuration still resolves.
 *
 * <p>The thrown `Error`'s own `message` is the third choice, not the first: for an `ApiError` it is
 * the bare status phrase — "Conflict", "Bad Request" — which tells the user nothing they cannot see
 * from the fact that something failed.
 *
 * <p><b>Always give a real `fallback`.</b> The default is a last resort for a caller with nothing
 * specific to say; a message naming the thing that failed is worth more than "Unknown error".
 */
export function apiErrorMessage(err: unknown, fallback = 'Unknown error'): string {
  const body = (err as { body?: unknown })?.body;

  if (body !== null && typeof body === 'object') {
    for (const field of ['detail', 'message'] as const) {
      const value = (body as Record<string, unknown>)[field];
      if (typeof value === 'string' && value.trim() !== '') {
        return value.trim();
      }
    }
  }

  if (err instanceof Error && err.message.trim() !== '') {
    return err.message;
  }

  return fallback;
}
