package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.MaintainerNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureInUseException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityResponse;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes to structures: archiving, deleting, and setting their sites' maintainer. */
@Service
public class StructureService {

  private static final Logger log = LoggerFactory.getLogger(StructureService.class);

  /**
   * The zone the audit columns are written in: the JVM's, which the deployment sets with
   * {@code TZ=America/Vancouver} ({@code openshift.deploy.yml}) — Pacific time, as legacy's
   * stamps are. Named rather than left implicit so it is a decision, not an accident.
   */
  private static final ZoneId AUDIT_ZONE = ZoneId.systemDefault();

  /** Oracle refuses an {@code IN} list longer than this (ORA-01795). */
  static final int IN_LIST_LIMIT = 1000;

  /**
   * What a delete removes along with the structure, in legacy's order
   * ({@code CBR.DELETE_STRUCTURE}): each table that holds a foreign key to the structure, or to its
   * bridge, and is not one of the blockers. Piers and spans go before the bridge they hang from.
   * Native rather than through the entities: the bridge and culvert, and the four tables mapped
   * only for this, are read-only ({@code @Immutable}), and one statement per table is what legacy
   * runs.
   */
  private static final List<String> CHILD_DELETES = List.of(
      "DELETE FROM THE.STRUCTURE_LOAD_RATING WHERE CROSSING_STRUCTURE_ID = :id",
      "DELETE FROM THE.FOREST_SERVICE_BRIDGE_PIER WHERE FOREST_SERVICE_BRIDGE_ID IN"
          + " (SELECT FOREST_SERVICE_BRIDGE_ID FROM THE.FOREST_SERVICE_BRIDGE"
          + " WHERE CROSSING_STRUCTURE_ID = :id)",
      "DELETE FROM THE.FOREST_SERVICE_BRIDGE_SPAN WHERE FOREST_SERVICE_BRIDGE_ID IN"
          + " (SELECT FOREST_SERVICE_BRIDGE_ID FROM THE.FOREST_SERVICE_BRIDGE"
          + " WHERE CROSSING_STRUCTURE_ID = :id)",
      "DELETE FROM THE.FOREST_SERVICE_BRIDGE WHERE CROSSING_STRUCTURE_ID = :id",
      "DELETE FROM THE.FOREST_SERVICE_CULVERT WHERE CROSSING_STRUCTURE_ID = :id",
      "DELETE FROM THE.STRUCTURE_COMMENT WHERE CROSSING_STRUCTURE_ID = :id",
      "DELETE FROM THE.CROSSING_STRUCTURE_NAME_HIST WHERE CROSSING_STRUCTURE_ID = :id",
      "DELETE FROM THE.CROSSING_STRUCTURE WHERE CROSSING_STRUCTURE_ID = :id");

  private final CrossingStructureRepository structures;
  private final CrossingSiteRepository sites;
  private final ClientLocationRepository clientLocations;
  private final LoggedUserHelper loggedUser;
  private final StructureDeleteBlockers blockers;
  private final EntityManager entityManager;

  public StructureService(
      CrossingStructureRepository structures,
      CrossingSiteRepository sites,
      ClientLocationRepository clientLocations,
      LoggedUserHelper loggedUser,
      StructureDeleteBlockers blockers,
      EntityManager entityManager) {
    this.structures = structures;
    this.sites = sites;
    this.clientLocations = clientLocations;
    this.loggedUser = loggedUser;
    this.blockers = blockers;
    this.entityManager = entityManager;
  }

  /**
   * Archives structures — legacy's "Archive All Selected".
   *
   * <p>Sets {@code ACTIVE_IND} to {@code 'N'} and stamps {@code UPDATE_USERID} and
   * {@code UPDATE_TIMESTAMP} with the caller and now. Legacy's {@code ARCHIVE_STRUCTURE} took those
   * two as parameters but was handed the values it had just read, so it never recorded who archived
   * a structure; this does.
   *
   * <p>As legacy: nothing blocks an archive, an already-archived structure is archived again (and
   * re-stamped), and an id with no structure is skipped. One transaction, so either all are
   * archived or none are.
   *
   * @return how many structures were archived
   */
  @Transactional
  public StructureArchiveResponse archive(List<Long> structureIds) {
    List<Long> ids = structureIds.stream().filter(Objects::nonNull).distinct().toList();
    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(AUDIT_ZONE);

    int archived = sumInBatches(ids, batch -> structures.archive(batch, user, now));

    log.info("{} archived {} of {} requested structure(s)", user, archived, ids.size());
    return new StructureArchiveResponse(archived);
  }

  /**
   * Sets the designated maintainer of the ticked structures' sites — legacy's "Update Repair
   * Responsibility for All Selected".
   *
   * <p><b>It is the site that changes.</b> The maintainer is {@code CROSSING_SITE.CLIENT_NUMBER}
   * and {@code CLIENT_LOCN_CODE}, so every structure on a site shares it, ticked or not; each site
   * is updated once however many of its structures were ticked. Stamped with who and when.
   *
   * <p>Each ticked structure is stamped too, as legacy's {@code structure.save} did — and, as that
   * save did, set active: a ticked archived structure is restored. That is legacy's behaviour, kept
   * at the business's choice; legacy's {@code Bridge.save} and {@code Culvert.save} both call
   * {@code setActive(true)}.
   *
   * <p>A structure that does not exist, or stands on no site, is skipped — legacy crashed on both.
   * One transaction.
   *
   * @throws MaintainerNotFoundException if no such client location exists — legacy's "Primary User
   *                                     Client Number/Location is invalid."
   */
  @Transactional
  public StructureRepairResponsibilityResponse updateRepairResponsibility(
      StructureRepairResponsibilityRequest request) {
    String clientNumber = request.clientNumber().trim();
    String location = request.clientLocationCode().trim();
    if (clientLocations.findMaintainer(clientNumber, location).isEmpty()) {
      throw new MaintainerNotFoundException(clientNumber, location);
    }

    List<Long> ids = request.structureIds().stream().filter(Objects::nonNull).distinct().toList();
    List<CrossingStructureEntity> onSites = batches(ids).stream()
        .flatMap(batch -> structures.findAllById(batch).stream())
        .filter(structure -> structure.getCrossingSiteId() != null)
        .toList();
    List<Long> structureIds =
        onSites.stream().map(CrossingStructureEntity::getCrossingStructureId).toList();
    List<String> siteIds =
        onSites.stream().map(CrossingStructureEntity::getCrossingSiteId).distinct().toList();

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(AUDIT_ZONE);
    int siteCount = sumInBatches(siteIds,
        batch -> sites.setMaintainer(batch, clientNumber, location, user, now));
    int structureCount = sumInBatches(structureIds,
        batch -> structures.touchForRepairResponsibility(batch, user, now));

    log.info("{} set maintainer {}-{} on {} site(s) through {} structure(s)",
        user, clientNumber, location, siteCount, structureCount);
    return new StructureRepairResponsibilityResponse(structureCount, siteCount);
  }

  /**
   * Deletes a structure — one of legacy's "Delete All Selected", which the screen calls once per
   * ticked structure.
   *
   * <p><b>Irreversible.</b> A hard delete of the structure and what hangs off it, as legacy's
   * {@code CBR.DELETE_STRUCTURE}: load ratings, the bridge with its piers and spans, the culvert,
   * comments and name history. One transaction, so a failure part-way leaves the structure whole.
   *
   * <p>Refused, rather than attempted, when the structure has any of what
   * {@link StructureDeleteBlockers} checks. The search shows those in advance, but the check is
   * made again here: an inspection may have been recorded since the page was read.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws StructureInUseException    if something still belongs to it, naming what
   */
  @Transactional
  public void delete(long structureId) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));

    List<String> blocking = blockers.of(List.of(structureId)).get(structureId);
    if (!blocking.isEmpty()) {
      throw new StructureInUseException("Structure " + name(structure) + " has "
          + joined(blocking) + " and cannot be deleted.");
    }

    // The structure was read into the persistence context above; the native deletes go around it.
    entityManager.detach(structure);
    for (String statement : CHILD_DELETES) {
      entityManager.createNativeQuery(statement).setParameter("id", structureId).executeUpdate();
    }
    log.info("{} deleted structure {} ({})", loggedUser.getLoggedUserId(), structureId,
        structure.getCrossingStructureName());
  }

  /** {@code values} a thousand at a time — Oracle refuses a longer {@code IN} list. */
  private static <T> List<List<T>> batches(List<T> values) {
    List<List<T>> batches = new ArrayList<>();
    for (int from = 0; from < values.size(); from += IN_LIST_LIMIT) {
      batches.add(values.subList(from, Math.min(from + IN_LIST_LIMIT, values.size())));
    }
    return batches;
  }

  /** Runs {@code statement} over each batch of {@code values} and adds up what each returns. */
  private static <T> int sumInBatches(List<T> values, ToIntFunction<List<T>> statement) {
    return batches(values).stream().mapToInt(statement).sum();
  }

  private static String name(CrossingStructureEntity structure) {
    String name = structure.getCrossingStructureName();
    return name == null || name.isBlank()
        ? String.valueOf(structure.getCrossingStructureId())
        : name;
  }

  /** "inspections", "inspections and repairs", "inspections, repairs and monitors". */
  static String joined(List<String> labels) {
    if (labels.size() == 1) {
      return labels.get(0);
    }
    return String.join(", ", labels.subList(0, labels.size() - 1))
        + " and " + labels.get(labels.size() - 1);
  }
}
