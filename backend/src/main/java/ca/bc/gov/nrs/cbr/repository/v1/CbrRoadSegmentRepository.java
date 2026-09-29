package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSegmentEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Road segments, over {@code THE.CBR_ROAD_SEGMENT_VW}.
 *
 * <p>Read-only: the target is a materialized view refreshed on demand over a database link.
 */
@Repository
public interface CbrRoadSegmentRepository
    extends JpaRepository<CbrRoadSegmentEntity, CbrRoadSegmentEntity.Key> {

  /**
   * The segment a site on this section belongs to — the first one, by segment id.
   *
   * <p><b>"First" is a faithful port, not a choice made here.</b> Legacy fills a hidden
   * {@code <select>} with every segment of the section, in the order
   * {@code CBR_GENERAL.FIND_RD_SGMNT_BY_FILE_SEC_ID} returns them —
   * {@code ORDER BY RS.ROAD_SEGMENT_ID, RS.START_STATION, RS.END_STATION} — and, with no blank
   * option and no way for the user to see the control, the browser posts back whichever the
   * ordering put at the top. {@code SiteAction.view} reads the same first element for the Forest
   * District. So the stored value has always been "the lowest segment id on the section".
   *
   * <p>Only the id ever varies within one section here, so the two station columns of the procedure's
   * ordering cannot change the answer; they are kept in the method name's spirit rather than its
   * letter because a derived query cannot express a tie-break that never breaks a tie.
   *
   * <p><b>Worth raising with the business.</b> A section with several segments gets an arbitrary
   * one, and nothing on the screen says so. Reproduced because it is what thirty years of rows were
   * written with, and changing it would make new sites disagree with old ones.
   *
   * @return the segment, or empty when the section has none — which legacy reports as an invalid
   *         Project File ID#/Br.
   */
  Optional<CbrRoadSegmentEntity> findFirstByForestFileIdAndRoadSectionIdOrderByRoadSegmentIdAsc(
      String forestFileId, String roadSectionId);
}
