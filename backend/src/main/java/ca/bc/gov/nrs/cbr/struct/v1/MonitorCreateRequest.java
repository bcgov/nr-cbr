package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A new monitoring item — legacy's Add Monitor dialog. No status and no number: a new item is
 * always Suggested, as legacy locks it, and takes the structure's next number.
 *
 * @param frequencyCode    {@code MONITOR_FREQUENCY_CODE}, or blank for none
 * @param frequencyComment required when the frequency is Other ({@code OTH}); kept only then
 * @param description      required, at most 2000 characters
 */
public record MonitorCreateRequest(
    String frequencyCode,
    String frequencyComment,
    String description) {}
