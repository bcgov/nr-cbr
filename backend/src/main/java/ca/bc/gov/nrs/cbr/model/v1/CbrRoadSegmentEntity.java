package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.CBR_ROAD_SEGMENT_VW} — a stretch of one road section.
 *
 * <p>FTA models a road file as sections, and a section as one or more segments with a start and end
 * station and a responsibility. {@code CROSSING_SITE.ROAD_SEGMENT_ID} records which one a site sits
 * on.
 *
 * <p><b>Nobody chooses it.</b> The Road Segment control on `site.jsp` is a {@code <select>} inside
 * two {@code <td style="visibility:hidden;">} cells, filled by the server with every segment of the
 * chosen section; with no blank option the browser selects the first, and that is what posts. So
 * the stored value is "the first segment of the section the user picked", decided by the order of
 * {@code CBR_GENERAL.FIND_RD_SGMNT_BY_FILE_SEC_ID} — {@code ORDER BY ROAD_SEGMENT_ID, START_STATION,
 * END_STATION}. See {@code CbrRoadSegmentRepository}.
 *
 * <p><b>A materialized view over a database link</b>, like the section view beside it, and
 * <b>stubbed in nr-mof-db</b> for the same reason — its migration selects a single all-{@code NULL}
 * row from {@code DUAL}. In an environment provisioned from those scripts no segment resolves for
 * any section, which legacy reports as an invalid Project File ID#/Br. That is the environment
 * rather than the data.
 *
 * <p>Keyed by the triple {@code FIND_ROAD_SEGMENT_BY_ID} matches on. The segment id alone is not
 * relied on to be unique across files here, because that procedure does not rely on it either.
 */
@Entity
@Immutable
@Table(name = "CBR_ROAD_SEGMENT_VW", schema = "THE")
@IdClass(CbrRoadSegmentEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode(of = {"forestFileId", "roadSectionId", "roadSegmentId"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CbrRoadSegmentEntity {

  @Id
  @Column(name = "FOREST_FILE_ID", length = 10)
  private String forestFileId;

  @Id
  @Column(name = "ROAD_SECTION_ID", length = 30)
  private String roadSectionId;

  @Id
  @Column(name = "ROAD_SEGMENT_ID")
  private Long roadSegmentId;

  /**
   * Who is responsible for this stretch of road.
   *
   * <p>The one thing on the site form that exists <em>only</em> here — the section view carries no
   * responsibility at all, which is why "Road Responsibility" cannot be filled from it.
   */
  @Column(name = "ROAD_RESPONSIBILITY_TYPE_CODE", length = 10)
  private String roadResponsibilityTypeCode;

  /** Where the segment starts along the section, in kilometres. */
  @Column(name = "START_STATION", precision = 11, scale = 4)
  private BigDecimal startStation;

  @Column(name = "END_STATION", precision = 11, scale = 4)
  private BigDecimal endStation;

  /** The composite key: file, section and segment, as {@code FIND_ROAD_SEGMENT_BY_ID} matches. */
  @Getter
  @ToString
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String forestFileId;
    private String roadSectionId;
    private Long roadSegmentId;
  }
}
