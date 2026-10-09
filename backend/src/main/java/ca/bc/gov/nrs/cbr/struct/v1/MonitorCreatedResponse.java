package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The monitoring item just added: its id, and the number it was given.
 *
 * @param id     {@code MONITOR_ID}
 * @param number {@code MONITOR_NUMBER} — the structure's next
 */
public record MonitorCreatedResponse(String id, Long number) {}
