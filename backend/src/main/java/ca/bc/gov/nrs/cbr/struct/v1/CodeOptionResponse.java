package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One entry of a code table: the stored code and its label.
 *
 * <p>All 37 CBR code tables share one shape — {@code <NAME>_CODE}, {@code DESCRIPTION}, and an
 * {@code EFFECTIVE_DATE}/{@code EXPIRY_DATE} range — so one record serves every dropdown
 * (cbr-data-model.local.md §1).
 *
 * <p><b>The date range is not carried here on purpose.</b> Validity in CBR is date-ranged rather
 * than a boolean flag: a code is current when {@code SYSDATE BETWEEN EFFECTIVE_DATE AND
 * EXPIRY_DATE}. That filter belongs in the query, so what reaches a dropdown is already the set of
 * codes a user may pick today. Historical records still resolve against expired codes, but they do
 * so by reading the code table directly, not through this list.
 *
 * @param code        the stored value, e.g. {@code "ACT"}
 * @param description the label shown to the user
 */
public record CodeOptionResponse(String code, String description) {}
