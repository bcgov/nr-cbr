package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Road sections, over {@code THE.CBR_ROAD_SECTION_VW}.
 *
 * <p>Read-only: the target is a materialized view refreshed on demand over a database link, and
 * the inherited write methods would fail at the database rather than here.
 *
 * <p>The single-section lookup the site form uses is by the view's own key, which
 * {@code JpaRepository.findById} already answers — see {@link CbrRoadSectionEntity.Key}.
 */
@Repository
public interface CbrRoadSectionRepository
    extends JpaRepository<CbrRoadSectionEntity, CbrRoadSectionEntity.Key> {

  /**
   * The road search behind the Project File ID# lookup dialog.
   *
   * <h2>Two outer joins, and why they are outer</h2>
   * A road file may be held by nobody, and a client number may name a client the public view does
   * not return. Either as an inner join would hide the road itself, which is the one thing the
   * dialog exists to find. Legacy left-joins both for the same reason.
   *
   * <p><b>{@code V_CLIENT_PUBLIC}, where legacy joins {@code FOREST_CLIENT}.</b> The table is not
   * granted to this application at all; the view carries the one column needed and withholds the
   * personal detail the table also holds. The same substitution the client lookup makes.
   *
   * <h2>One query with null-guarded parameters, unlike the client lookup</h2>
   * Six optional criteria is sixty-four shapes, and a method for each is not a plan. Legacy builds
   * the predicate as a string for the same reason. The cost is one plan that knows none of them
   * specifically; the cap below is what keeps that affordable, and the dialog is a lookup rather
   * than a report — nobody waits on it with a page of results open.
   *
   * @param limit legacy's is {@code ROWNUM <= 200}, and its dialog says "200 or more records"
   *              when it fills
   */
  @Query("""
      SELECT DISTINCT new ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult(
               road.roadSectName,
               road.forestFileId,
               road.roadSectionId,
               road.fileTypeCode,
               client.clientName,
               client.clientNumber)
        FROM CbrRoadSectionEntity road
        LEFT JOIN ForestFileClientEntity fileClient
          ON fileClient.forestFileId = road.forestFileId
        LEFT JOIN ClientPublicEntity client
          ON client.clientNumber = fileClient.clientNumber
       WHERE (:forestServiceRoad IS NULL OR UPPER(road.roadSectName) LIKE :forestServiceRoad)
         AND (:forestFileId IS NULL OR UPPER(road.forestFileId) LIKE :forestFileId)
         AND (:roadSectionId IS NULL OR UPPER(road.roadSectionId) LIKE :roadSectionId)
         AND (:tenureType IS NULL OR UPPER(road.fileTypeCode) LIKE :tenureType)
         AND (:clientName IS NULL OR UPPER(client.clientName) LIKE :clientName)
         AND (:clientNumber IS NULL OR UPPER(client.clientNumber) LIKE :clientNumber)
       ORDER BY road.roadSectName, road.forestFileId, road.roadSectionId
      """)

  List<RoadSearchResult> search(
      @Param("forestServiceRoad") String forestServiceRoad,
      @Param("forestFileId") String forestFileId,
      @Param("roadSectionId") String roadSectionId,
      @Param("tenureType") String tenureType,
      @Param("clientName") String clientName,
      @Param("clientNumber") String clientNumber,
      Pageable limit);

  /**
   * The same search with the tenure holder left out.
   *
   * <p>Used only when {@code FOREST_FILE_CLIENT} cannot be read — see
   * {@link ca.bc.gov.nrs.cbr.service.v1.RoadSectionService}. Identical but for the two joins and
   * the two columns they supply, so a road still finds itself by name, file, section or tenure
   * type; only the client is unknown.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult(
               road.roadSectName,
               road.forestFileId,
               road.roadSectionId,
               road.fileTypeCode,
               NULL,
               NULL)
        FROM CbrRoadSectionEntity road
       WHERE (:forestServiceRoad IS NULL OR UPPER(road.roadSectName) LIKE :forestServiceRoad)
         AND (:forestFileId IS NULL OR UPPER(road.forestFileId) LIKE :forestFileId)
         AND (:roadSectionId IS NULL OR UPPER(road.roadSectionId) LIKE :roadSectionId)
         AND (:tenureType IS NULL OR UPPER(road.fileTypeCode) LIKE :tenureType)
       ORDER BY road.roadSectName, road.forestFileId, road.roadSectionId
      """)
  List<RoadSearchResult> searchWithoutClient(
      @Param("forestServiceRoad") String forestServiceRoad,
      @Param("forestFileId") String forestFileId,
      @Param("roadSectionId") String roadSectionId,
      @Param("tenureType") String tenureType,
      Pageable limit);
}
