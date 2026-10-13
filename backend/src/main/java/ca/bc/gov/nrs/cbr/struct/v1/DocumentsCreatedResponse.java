package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * The documents just added, in the order their files were sent.
 *
 * @param ids each one's {@code FILE_ID}
 */
public record DocumentsCreatedResponse(List<String> ids) {}
