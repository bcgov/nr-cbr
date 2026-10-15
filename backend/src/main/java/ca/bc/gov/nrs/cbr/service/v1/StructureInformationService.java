package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureNameHistEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.SitePreviewResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInformationRequest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Information tab's Edit — the two fields of legacy's header a user may change there:
 * Structure # (Level 2) and Site # (Level 1 and up), saved together. Type/Class, the header's third,
 * is edited with the bridge on the Details tab, beside the fields its Durable Bridge rules check.
 *
 * <p>Legacy's rules ({@code StructureForm.validate}), with the name checked against every
 * structure, archived ones too — the database's {@code CS_UK} — where legacy checked active ones
 * only and then failed on the constraint. Unlike legacy's whole-row save, only the name, the site
 * and the update audit are written: nothing else is blanked, and an archived structure stays
 * archived.
 */
@Service
public class StructureInformationService {

  private static final Logger log = LoggerFactory.getLogger(StructureInformationService.class);

  /** {@code CROSSING_STRUCTURE_NAME VARCHAR2(14 BYTE)}. */
  private static final int NAME_MAX = 14;
  private static final String PROPOSED = "PP";
  private static final String STORAGE = "STRG";
  private static final String NAME = "structureName";
  private static final String SITE = "siteId";

  private final CrossingStructureRepository structures;
  private final CrossingSiteRepository sites;
  private final StructureDetailService details;
  private final LoggedUserHelper loggedUser;
  private final EntityManager entityManager;

  public StructureInformationService(
      CrossingStructureRepository structures,
      CrossingSiteRepository sites,
      StructureDetailService details,
      LoggedUserHelper loggedUser,
      EntityManager entityManager) {
    this.structures = structures;
    this.sites = sites;
    this.details = details;
    this.loggedUser = loggedUser;
    this.entityManager = entityManager;
  }

  /**
   * A site to move a structure to, as the Edit previews it: the site as the Information tab would
   * show it, and why the structure could not move there, if it could not — legacy refreshed its
   * header the same way as the site number was typed.
   */
  @Transactional(readOnly = true)
  public SitePreviewResponse preview(String siteId) {
    String id = trimmed(siteId);
    Optional<CrossingSiteEntity> site = id == null ? Optional.empty() : sites.findById(id);
    return site
        .map(found -> new SitePreviewResponse(details.site(found), problemWith(found)))
        .orElseGet(() -> new SitePreviewResponse(null, notFound(id)));
  }

  /**
   * Saves the structure's name and site.
   *
   * <p>As legacy: the name is required, at most 14 characters, and changed only at Level 2; a site
   * must exist, be complete, and not be Proposed, and once the structure has a site it cannot be
   * cleared. A rename records the old name in {@code CROSSING_STRUCTURE_NAME_HIST}, as legacy's
   * {@code UPDATE_STRUCTURE} did. A field sent unchanged is not checked again.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   keyed {@code structureName} or {@code siteId}
   */
  @Transactional
  public void update(long structureId, StructureInformationRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    String name = trimmed(request.structureName());
    String siteId = trimmed(request.siteId());
    boolean renamed = !Objects.equals(name, structure.getCrossingStructureName());
    boolean moved = !Objects.equals(siteId, structure.getCrossingSiteId());

    Map<String, String> errors = new LinkedHashMap<>();
    if (renamed) {
      checkName(errors, structureId, name);
    }
    if (moved) {
      checkSite(errors, structure, siteId);
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Structure information cannot be saved", errors);
    }
    if (!renamed && !moved) {
      return;
    }

    String user = loggedUser.getLoggedUserId();
    if (renamed && structure.getCrossingStructureName() != null) {
      // CHANGE_TIMESTAMP is a DATE: whole seconds, as Oracle would keep it.
      entityManager.persist(CrossingStructureNameHistEntity.builder()
          .oldCrossingStructureName(structure.getCrossingStructureName())
          .changeTimestamp(LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS))
          .crossingStructureId(structureId)
          .changeUserid(user)
          .build());
    }
    structures.updateInformation(structureId, name, siteId, user);
    log.info("Updated the information of structure {} (renamed: {}, moved: {})", structureId,
        renamed, moved);
  }

  private void checkName(Map<String, String> errors, long structureId, String name) {
    if (!loggedUser.canDestroy()) {
      errors.put(NAME, "Structure # can be changed only at Level 2 and up.");
    } else if (name == null) {
      errors.put(NAME, "Structure # is required.");
    } else if (name.getBytes(StandardCharsets.UTF_8).length > NAME_MAX) {
      errors.put(NAME, "Structure # can be at most " + NAME_MAX + " characters.");
    } else if (structures.existsByCrossingStructureNameAndCrossingStructureIdNot(
        name, structureId)) {
      errors.put(NAME, "Structure " + name + " already exists.");
    }
  }

  private void checkSite(Map<String, String> errors, CrossingStructureEntity structure,
      String siteId) {
    if (siteId == null) {
      // As legacy: required once the structure has a site; one that never had one may stay so.
      if (structure.getCrossingSiteId() != null) {
        errors.put(SITE, "Site # is required.");
      }
      return;
    }
    String problem = siteProblem(siteId);
    if (problem != null) {
      errors.put(SITE, problem);
    }
  }

  /**
   * Why a structure cannot stand on this site — it does not exist, is incomplete, or is Proposed —
   * or null when it can. Shared with Add Structure.
   */
  String siteProblem(String siteId) {
    // Not Optional.map: a site with nothing wrong maps to null, which map() would take for "none".
    Optional<CrossingSiteEntity> site = sites.findById(siteId);
    return site.isPresent() ? problemWith(site.get()) : notFound(siteId);
  }

  /**
   * Why a structure cannot move to this site, or null — legacy's {@code Site.isComplete()} and its
   * Proposed check, each with its own sentence where legacy said "Site # is invalid." for both.
   */
  private String problemWith(CrossingSiteEntity site) {
    String id = site.getCrossingSiteId();
    if (!complete(site)) {
      return "Site " + id + " is incomplete. Complete it on its Site Detail page first.";
    }
    if (PROPOSED.equals(site.getCrossingSiteStatusCode())) {
      return "Site " + id + " is Proposed. A structure cannot be moved to a Proposed site.";
    }
    return null;
  }

  /**
   * Legacy's {@code Site.isComplete()}: status, inspection status, district and type set, and a
   * crossing name and kilometres unless it is a storage site.
   */
  private static boolean complete(CrossingSiteEntity site) {
    if (isBlank(site.getCrossingSiteStatusCode())
        || isBlank(site.getStructureInspectionStatusCode())
        || site.getOrgUnitNo() == null
        || isBlank(site.getCrossingSiteTypeCode())) {
      return false;
    }
    return STORAGE.equals(site.getCrossingSiteTypeCode())
        || (!isBlank(site.getCrossingName()) && site.getPointOfCommencementDistance() != null);
  }

  private static String notFound(String siteId) {
    return siteId == null ? "Site # is required." : "Site " + siteId + " does not exist.";
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
