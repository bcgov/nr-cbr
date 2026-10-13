package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An edit to a document's details — legacy's viewer edit: the type and the description only; the
 * file, its name and its date are not changed.
 *
 * @param attachmentTypeCode {@code FILE_ATTACHMENT_TYPE_CODE} — "Photo View"; required
 * @param description        required when the type is Other ({@code OTH}); at most 2000 bytes
 */
public record DocumentUpdateRequest(String attachmentTypeCode, String description) {}
