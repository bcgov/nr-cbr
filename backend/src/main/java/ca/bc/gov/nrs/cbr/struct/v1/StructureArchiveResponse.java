package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * What an archive did.
 *
 * @param archivedCount how many structures were archived — every requested one that exists,
 *                      already-archived ones included, as legacy counts them
 */
public record StructureArchiveResponse(int archivedCount) {}
