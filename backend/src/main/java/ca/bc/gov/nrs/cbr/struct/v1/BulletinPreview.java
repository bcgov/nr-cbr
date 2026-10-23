package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A draft bulletin as Save would keep it, for the editor's live preview.
 *
 * @param message the draft, as HTML kept to the safe set of tags
 */
public record BulletinPreview(String message) {}
