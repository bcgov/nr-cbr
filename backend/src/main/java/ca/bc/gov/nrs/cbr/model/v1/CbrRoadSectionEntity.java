package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.CBR_ROAD_SECTION_VW} — the road section a crossing site sits on.
 *
 * <p><b>A materialized view fed over a database link</b>, {@code REFRESH COMPLETE ON DEMAND}
 * (cbr-data-model.local.md §6). Two consequences worth knowing before trusting a result:
 *
 * <ul>
 *   <li>It is a snapshot. A road section renamed upstream keeps its old name here until the next
 *       refresh, so Forest Service Road is as current as that schedule and no more.</li>
 *   <li><b>In nr-mof-db it is stubbed</b> — the migration's own comment says the mview "relies on a
 *       dblink and has thus been faked", and selects a single all-{@code NULL} row from
 *       {@code DUAL}. In any environment provisioned from those scripts rather than being a real
 *       copy, Forest Service Road is blank and searching on it matches nothing. That is the
 *       environment, not the query.</li>
 * </ul>
 *
 * <p>Keyed by {@code (FOREST_FILE_ID, ROAD_SECTION_ID)}, the pair {@code CROSSING_SITE} carries.
 */
@Entity
@Immutable
@Table(name = "CBR_ROAD_SECTION_VW", schema = "THE")
@IdClass(CbrRoadSectionEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode(of = {"forestFileId", "roadSectionId"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CbrRoadSectionEntity {

  @Id
  @Column(name = "FOREST_FILE_ID", length = 10)
  private String forestFileId;

  @Id
  @Column(name = "ROAD_SECTION_ID", length = 30)
  private String roadSectionId;

  /** "Forest Service Road" on the search form and in the results table. */
  @Column(name = "ROAD_SECT_NAME", length = 30)
  private String roadSectName;

  /**
   * "Tenure Type" on the road search — {@code B01} road permit, {@code B40} forest service road,
   * {@code S01}/{@code S02} special use permit. The legacy dialog prints that key beside the box
   * because the view carries the code and nothing decodes it.
   */
  @Column(name = "FILE_TYPE_CODE", length = 10)
  private String fileTypeCode;

  /**
   * The road's forest region — and <b>what legacy puts in the site's Forest District</b>.
   *
   * <p>Not a mistranslation here: {@code OracleRoadSegmentDAO} reads it as
   * {@code ORG_UNIT_NO = "FOREST_REGION"} and {@code SiteAction} assigns it to
   * {@code SiteForm.orgUnitNo}. A region is a different kind of org unit from a district, so this
   * is carried under the view's own name and the site form is left to decide what to do with it.
   */
  @Column(name = "FOREST_REGION")
  private Long forestRegion;

  /**
   * The composite key.
   *
   * <p>A plain class rather than a record, because JPA requires a no-arg constructor. Named
   * {@code Key} rather than {@code RoadSectionId}: the field below must be called
   * {@code roadSectionId} — {@code @IdClass} matches its fields to the entity's {@code @Id} fields
   * by name — and a class differing from its own field only by capitalisation is a genuine
   * readability trap, as well as a Sonar finding. The class is the half that can move.
   */
  @Getter
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String forestFileId;
    private String roadSectionId;
  }
}
