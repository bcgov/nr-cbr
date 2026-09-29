package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.RECREATION_PROJECT} — the recreation file a recreation site belongs to.
 *
 * <p>Mapped for one column. A recreation site's "Project File ID#" names one of these rather than a
 * road file, and the site form shows its name where a crossing shows the Forest Service Road
 * (`site.jsp:814-824`, the other half of the {@code <c:if>} that chooses between the two).
 *
 * <p>Read-only: CBR does not own this table and has no reason to write to it. Readable through a
 * role, checked 2026-09-23 — unlike {@code FOREST_FILE_CLIENT}, this one needed no new grant.
 *
 * <p>The table has thirty-odd columns; the rest belong to the recreation application and are none
 * of CBR's business. Mapping only what is read keeps it that way — and keeps a column added
 * upstream from breaking a query here.
 */
@Entity
@Immutable
@Table(name = "RECREATION_PROJECT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestFileId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecreationProjectEntity {

  @Id
  @Column(name = "FOREST_FILE_ID", length = 10)
  private String forestFileId;

  /** "Project Name" on the site form. {@code NOT NULL}, so a row that exists always has one. */
  @Column(name = "PROJECT_NAME", length = 100)
  private String projectName;
}
