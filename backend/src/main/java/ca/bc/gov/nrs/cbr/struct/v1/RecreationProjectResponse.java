package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The recreation project a file id names.
 *
 * <p>A record rather than a bare string: Spring serializes a {@code ResponseEntity<String>} through
 * the plain-text converter, so a null name would reach the browser as an empty 200 with no content
 * type worth having. One field in an object is still JSON, and leaves room for the other columns of
 * {@code RECREATION_PROJECT} if a screen ever needs them.
 *
 * @param forestFileId the file that was asked about, echoed so a caller can tell a stale answer
 *                     from a current one
 * @param projectName  the name, or null when no such project exists — the ordinary state while a
 *                     file id is being typed
 */
public record RecreationProjectResponse(String forestFileId, String projectName) {}
