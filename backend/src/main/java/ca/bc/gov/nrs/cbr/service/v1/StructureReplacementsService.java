package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.ReplacementNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureReplacementXrefRepository;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureNameSuggestion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Details tab's Replacement History — legacy's P.Eng pop-up ({@code StructureAction.replacement},
 * {@code INSERT/DELETE_STRUCTURE_REPLACEMENT}): the structures this one replaced, each added or
 * deleted at once. As legacy, this structure is always the replacing one — a structure that
 * replaced this one is recorded from its own page — and a replacement is never changed, only
 * deleted and added again.
 *
 * <p>As legacy, the Structure # is required and must be a structure ("Invalid." there), and one
 * already recorded is refused ("Replacement already exists."). Not this structure itself, nor a
 * structure already recorded as replacing this one, which legacy let through.
 */
@Service
public class StructureReplacementsService {

  private static final Logger log = LoggerFactory.getLogger(StructureReplacementsService.class);

  /** The fewest characters of a Structure # the type-ahead searches on. */
  private static final int SUGGEST_MIN = 2;
  private static final String REPLACED = "replacedName";

  private final CrossingStructureRepository structures;
  private final StructureReplacementXrefRepository links;

  public StructureReplacementsService(
      CrossingStructureRepository structures, StructureReplacementXrefRepository links) {
    this.structures = structures;
    this.links = links;
  }

  /**
   * Structures whose Structure # starts with what was typed, for the dialog's type-ahead: at most
   * 20, by name, archived ones included and marked. Nothing for fewer than {@value #SUGGEST_MIN}
   * characters, which would match most of the register.
   */
  @Transactional(readOnly = true)
  public List<StructureNameSuggestion> suggestNames(String term) {
    String prefix = trimmed(term);
    if (prefix == null || prefix.length() < SUGGEST_MIN) {
      return List.of();
    }
    return structures
        .findTop20ByCrossingStructureNameStartingWithIgnoreCaseOrderByCrossingStructureNameAsc(prefix)
        .stream()
        .map(structure -> new StructureNameSuggestion(
            String.valueOf(structure.getCrossingStructureId()),
            structure.getCrossingStructureName(), "N".equals(structure.getActiveInd())))
        .toList();
  }

  /**
   * Records that this structure replaced another.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   keyed {@code replacedName}
   */
  @Transactional
  public void add(long structureId, ReplacementRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    String name = trimmed(request.replacedName());
    String error = null;
    Long replaced = null;
    if (name == null) {
      error = "Replaced Structure # is required.";
    } else {
      Optional<CrossingStructureEntity> found = structures.findByCrossingStructureName(name);
      if (found.isEmpty()) {
        error = "Structure # " + name + " does not exist.";
      } else {
        replaced = found.get().getCrossingStructureId();
        if (replaced == structureId) {
          error = "A structure cannot replace itself.";
        } else if (links.existsById(new StructureReplacementXrefEntity.Key(replaced, structureId))) {
          error = "Replacement already exists.";
        } else if (links.existsById(new StructureReplacementXrefEntity.Key(structureId, replaced))) {
          error = "Structure # " + name + " is already recorded as replacing "
              + structure.getCrossingStructureName() + ".";
        }
      }
    }
    if (error != null) {
      throw new FieldValidationException("The replacement cannot be saved",
          Map.of(REPLACED, error));
    }
    links.save(new StructureReplacementXrefEntity(replaced, structureId));
    log.info("Structure {} replaced structure {}", structureId, replaced);
  }

  /**
   * Deletes that this structure replaced another — legacy's Delete, from the replacing structure.
   *
   * @throws ReplacementNotFoundException if this structure did not replace that one
   */
  @Transactional
  public void delete(long structureId, long replacedId) {
    StructureReplacementXrefEntity link =
        links.findById(new StructureReplacementXrefEntity.Key(replacedId, structureId))
            .orElseThrow(() -> new ReplacementNotFoundException(structureId, replacedId));
    links.delete(link);
    log.info("Deleted the replacement of structure {} by {}", replacedId, structureId);
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
