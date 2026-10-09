package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An edit to a monitoring item — legacy's "Monitoring Item" dialog, opened from its number. The
 * number itself is not here: it is shown, not changed.
 *
 * @param statusCode       {@code MONITORING_STATUS_CODE}: suggested, required or completed
 * @param frequencyCode    {@code MONITOR_FREQUENCY_CODE}, or blank for none
 * @param frequencyComment required when the frequency is Other ({@code OTH}); kept only then
 * @param description      required, at most 2000 characters
 */
public record MonitorUpdateRequest(
    String statusCode,
    String frequencyCode,
    String frequencyComment,
    String description) {}
