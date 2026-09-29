package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import org.springframework.data.domain.Page;
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
   * specifically; paging is what keeps that affordable.
   *
   * <h2>Paged, with a count of its own</h2>
   * Legacy caps the dialog at {@code ROWNUM <= 200}; CBR pages it instead, as Site Search and
   * Inspection Search do, so nothing past the two-hundredth road is out of reach and the total is
   * the true one. The count cannot be derived: the select is a {@code DISTINCT} over six columns,
   * and Oracle has no multi-column {@code COUNT(DISTINCT …)}. It counts the same distinct rows from
   * a derived table instead — the only way to be sure it agrees with the rows it pages over.
   *
   * @param page the page asked for; its size is bounded by the service
   */
  @Query(value = """
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
      """,
      countQuery = """
      SELECT COUNT(*)
        FROM (SELECT DISTINCT road.roadSectName AS roadSectName,
                     road.forestFileId AS forestFileId,
                     road.roadSectionId AS roadSectionId,
                     road.fileTypeCode AS fileTypeCode,
                     client.clientName AS clientName,
                     client.clientNumber AS clientNumber
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
             ) matched
      """)
  Page<RoadSearchResult> search(
      @Param("forestServiceRoad") String forestServiceRoad,
      @Param("forestFileId") String forestFileId,
      @Param("roadSectionId") String roadSectionId,
      @Param("tenureType") String tenureType,
      @Param("clientName") String clientName,
      @Param("clientNumber") String clientNumber,
      Pageable page);

  /**
   * The same search with the tenure holder left out.
   *
   * <p>Used only when {@code FOREST_FILE_CLIENT} cannot be read — see
   * {@link ca.bc.gov.nrs.cbr.service.v1.RoadSectionService}. Identical but for the two joins and
   * the two columns they supply, so a road still finds itself by name, file, section or tenure
   * type; only the client is unknown.
   */
  @Query(value = """
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
      """,
      countQuery = """
      SELECT COUNT(road)
        FROM CbrRoadSectionEntity road
       WHERE (:forestServiceRoad IS NULL OR UPPER(road.roadSectName) LIKE :forestServiceRoad)
         AND (:forestFileId IS NULL OR UPPER(road.forestFileId) LIKE :forestFileId)
         AND (:roadSectionId IS NULL OR UPPER(road.roadSectionId) LIKE :roadSectionId)
         AND (:tenureType IS NULL OR UPPER(road.fileTypeCode) LIKE :tenureType)
      """)
  Page<RoadSearchResult> searchWithoutClient(
      @Param("forestServiceRoad") String forestServiceRoad,
      @Param("forestFileId") String forestFileId,
      @Param("roadSectionId") String roadSectionId,
      @Param("tenureType") String tenureType,
      Pageable page);
}
