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
 * <p><b>Not for a server error.</b> A 5xx returns the `fallback` whatever it carries — see below.
 *
 * <p><b>Always give a real `fallback`.</b> The default is a last resort for a caller with nothing
 * specific to say; a message naming the thing that failed is worth more than "Unknown error".
 */
export function apiErrorMessage(err: unknown, fallback = 'Unknown error'): string {
  // A server error says nothing the user can act on, and what it does say is internal: with
  // `server.error.include-message` on, an unhandled exception's own text arrives as `message` —
  // "Request processing failed: org.hibernate.exception.SQLGrammarException: … [DELETE FROM …]
  // [ORA-01031: insufficient privileges]". The caller's own wording is the message for those.
  // Only a 4xx carries a sentence written for the user, such as a 409's "Structure B1 has
  // inspections and cannot be deleted."
  const status = (err as { status?: unknown })?.status;
  if (typeof status === 'number' && status >= 500) {
    return fallback;
  }

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
