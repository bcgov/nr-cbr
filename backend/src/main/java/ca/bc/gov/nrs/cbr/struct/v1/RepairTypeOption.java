package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One entry of the Repair Item dialog's Repair Type list, for one group it falls in.
 *
 * @param code        {@code STRUCTURE_REPAIR_TYPE_CODE}
 * @param description the label shown
 * @param unit        what its quantity is counted in, e.g. {@code m²}; null when none
 * @param groupCode   {@code STRUCTURE_REPAIR_GROUP_CODE}, which the dialog's checkboxes filter on
 */
public record RepairTypeOption(String code, String description, String unit, String groupCode) {}
