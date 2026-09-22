package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One row of the road search — a road section, and whoever holds its file.
 *
 * <p>The six columns legacy's dialog shows, in its order. {@code clientName} and
 * {@code clientNumber} are null for a file nobody holds: the joins that reach them are outer ones,
 * because a road is still a road without a tenure holder and withholding it would hide the very
 * section the user is looking for.
 */
public record RoadSearchResult(
    String forestServiceRoad,
    String forestFileId,
    String roadSectionId,
    String tenureType,
    String clientName,
    String clientNumber) {}
