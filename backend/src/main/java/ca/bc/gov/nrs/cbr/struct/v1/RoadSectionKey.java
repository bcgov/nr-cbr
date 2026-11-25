package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * A road section by the pair a site records: {@code FOREST_FILE_ID} (LRMOPS's road id) and
 * {@code ROAD_SECTION_ID} (its section id).
 *
 * <p>Built through {@link #of}, which trims and upper-cases both halves, so keys from CBR's rows
 * and from LRMOPS's answers compare equal however either stored them — LRMOPS matches sections
 * case-insensitively too.
 */
public record RoadSectionKey(String forestFileId, String roadSectionId) {

  /** The key for a pair, normalized; null when either half is blank. */
  public static RoadSectionKey of(String forestFileId, String roadSectionId) {
    if (!StringUtils.hasText(forestFileId) || !StringUtils.hasText(roadSectionId)) {
      return null;
    }
    return new RoadSectionKey(
        forestFileId.trim().toUpperCase(Locale.ROOT),
        roadSectionId.trim().toUpperCase(Locale.ROOT));
  }
}
