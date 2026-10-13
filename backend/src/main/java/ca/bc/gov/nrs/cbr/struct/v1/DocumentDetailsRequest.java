package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDate;

/**
 * One file's details in an upload — legacy's row on its upload page. The file itself is the
 * multipart part at the same position.
 *
 * @param attachmentTypeCode {@code FILE_ATTACHMENT_TYPE_CODE} — legacy's "Photo View"; required
 * @param createdDate        when the photo was taken or the document made; required
 * @param description        required when the type is Other ({@code OTH}); at most 2000 bytes
 */
public record DocumentDetailsRequest(
    String attachmentTypeCode,
    LocalDate createdDate,
    String description) {}
