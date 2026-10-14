package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.repository.v1.BuiltByCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureSourceCodeRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailsRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Details tab's edits, a card at a time: the Structure card — legacy's fields above the bridge
 * and culvert ones: Built For, the three years, Source, End of Design Life, As Built Information on
 * File and the two costs — and Replacement Details. The Bridge card, Load Rating Details and
 * Replacement History have services of their own.
 *
 * <p>Legacy's blocking rules ({@code StructureForm.validate}), each beside its field, plus values
 * that fit their columns and years of four digits. Legacy's range warnings — a year before 1900, a cost over $500,000 — never
 * blocked its save and do not block this one; the page shows them as the user types. Only these
 * columns and the update audit are written, so nothing else is blanked and an archived structure
 * stays archived.
 */
@Service
public class StructureDetailsService {

  private static final Logger log = LoggerFactory.getLogger(StructureDetailsService.class);

  /** The years are {@code NUMBER(4)}, and a year is written with all four digits. */
  private static final int YEAR_MIN = 1000;
  private static final int YEAR_MAX = 9999;
  /** The costs are {@code NUMBER(6)}, whole dollars. */
  private static final long COST_MAX = 999_999L;
  /** Sources that need Year Added to Inventory — legacy's {@code StructureForm.validate}. */
  private static final Set<String> SOURCES_NEEDING_YEAR_ADDED =
      Set.of("IR", "INSR", "ITM", "IFS", "IIO");
  /** {@code ESTIMATED_REPLACEMENT_COST NUMBER(8)}, whole dollars. */
  private static final long REPLACEMENT_COST_MAX = 99_999_999L;
  /** {@code ESTIMATED_REPLACEMENT_COST_CMT VARCHAR2(2000 BYTE)}. */
  private static final int TEXT_MAX = 2000;
  private static final String YEAR_FABRICATED = "yearFabricated";
  private static final String YEAR_BUILT = "yearBuilt";
  private static final String INVENTORY_ADDED = "inventoryAddedYear";
  private static final String SOURCE = "sourceCode";
  private static final String CLOSURE = "closureYear";
  private static final String REPLACEMENT = "replacementYear";
  private static final String LOAD_RESTRICTION = "loadRestrictionYear";
  private static final String YES = "Y";
  private static final String NO = "N";

  private final CrossingStructureRepository structures;
  private final BuiltByCodeRepository builtByCodes;
  private final StructureSourceCodeRepository sourceCodes;
  private final LoggedUserHelper loggedUser;

  public StructureDetailsService(
      CrossingStructureRepository structures,
      BuiltByCodeRepository builtByCodes,
      StructureSourceCodeRepository sourceCodes,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.builtByCodes = builtByCodes;
    this.sourceCodes = sourceCodes;
    this.loggedUser = loggedUser;
  }

  /**
   * Saves the Structure card.
   *
   * <p>As legacy: the years and costs are whole numbers; Year Superstructure Fabricated is not after
   * this year or after Year Superstructure Installed; Source is required once the structure has one,
   * and the sources IR, INSR, ITM, IFS and IIO need Year Added to Inventory; Built For and Source are
   * codes that exist — a code since expired may be kept by the structure that has it. As Built
   * Information on File is required.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   with a message for each field at fault
   */
  @Transactional
  public void updateStructure(long structureId, StructureDetailsRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    String builtBy = trimmed(request.builtByCode());
    String source = trimmed(request.sourceCode());

    Map<String, String> errors = new LinkedHashMap<>();
    if (builtBy != null && !builtBy.equals(structure.getBuiltByCode())
        && !builtByCodes.existsById(builtBy)) {
      errors.put("builtByCode", "Built For is not one of the listed values.");
    }
    checkYear(errors, YEAR_FABRICATED, "Year Superstructure Fabricated", request.yearFabricated());
    checkYear(errors, YEAR_BUILT, "Year Superstructure Installed", request.yearBuilt());
    checkYear(errors, INVENTORY_ADDED, "Year Added to Inventory", request.inventoryAddedYear());
    checkYear(errors, "endOfDesignLifeYear", "End of Design Life", request.endOfDesignLifeYear());
    checkFabricated(errors, request);
    checkSource(errors, structure, source, request);
    if (request.asBuiltInfoOnFile() == null) {
      errors.put("asBuiltInfoOnFile", "As Built Information on File is required.");
    }
    checkCost(errors, "installationCost", "Installation Cost", request.installationCost());
    checkCost(errors, "materialCost", "Material Cost", request.materialCost());
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Structure details cannot be saved", errors);
    }

    structures.updateStructureDetails(structureId, builtBy, request.yearFabricated(),
        request.yearBuilt(), request.inventoryAddedYear(), source, request.endOfDesignLifeYear(),
        Objects.equals(request.asBuiltInfoOnFile(), Boolean.TRUE) ? YES : NO,
        request.installationCost(), request.materialCost(), loggedUser.getLoggedUserId());
    log.info("Updated the structure details of structure {}", structureId);
  }

  /** Year Superstructure Fabricated: not after this year, nor after the year installed. */
  private static void checkFabricated(Map<String, String> errors,
      StructureDetailsRequest request) {
    Integer fabricated = request.yearFabricated();
    if (errors.containsKey(YEAR_FABRICATED) || fabricated == null) {
      return;
    }
    int thisYear = LocalDate.now(ZoneId.systemDefault()).getYear();
    if (fabricated > thisYear) {
      errors.put(YEAR_FABRICATED,
          "Year Superstructure Fabricated must not be after this year (" + thisYear + ").");
    } else if (request.yearBuilt() != null && !errors.containsKey(YEAR_BUILT)
        && fabricated > request.yearBuilt()) {
      errors.put(YEAR_FABRICATED,
          "Year Superstructure Fabricated must not be after Year Superstructure Installed.");
    }
  }

  /**
   * Source: required once the structure has one, a code that exists (or the structure's own), and
   * some sources need Year Added to Inventory.
   */
  private void checkSource(Map<String, String> errors, CrossingStructureEntity structure,
      String source, StructureDetailsRequest request) {
    if (source == null) {
      if (trimmed(structure.getStructureSourceCode()) != null) {
        errors.put(SOURCE, "Source is required.");
      }
    } else if (!source.equals(structure.getStructureSourceCode())
        && !sourceCodes.existsById(source)) {
      errors.put(SOURCE, "Source is not one of the listed values.");
    } else if (SOURCES_NEEDING_YEAR_ADDED.contains(source) && request.inventoryAddedYear() == null
        && !errors.containsKey(INVENTORY_ADDED)) {
      errors.put(INVENTORY_ADDED, "Year Added to Inventory is required for this Source.");
    }
  }

  /**
   * Saves Replacement Details — a P.Eng's, as legacy's page.
   *
   * <p>As legacy ({@code StructureForm.validate}): Estimated Load Restriction is required once the
   * structure has one, and not after Estimated Replacement; neither is after Estimated Closure. The
   * years have four digits; the cost is whole dollars the column holds; the comment at most 2000
   * bytes. Legacy's range warnings — beyond the year installed plus 50 or 60, before 1950, a cost over
   * $2,000,000 — never blocked its save and do not block this one.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   with a message for each field at fault
   */
  @Transactional
  public void updateReplacement(long structureId, ReplacementDetailsRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    Map<String, String> errors = new LinkedHashMap<>();
    checkYear(errors, CLOSURE, "Estimated Closure", request.closureYear());
    checkYear(errors, REPLACEMENT, "Estimated Replacement", request.replacementYear());
    checkYear(errors, LOAD_RESTRICTION, "Estimated Load Restriction",
        request.loadRestrictionYear());
    if (request.loadRestrictionYear() == null && structure.getFullLogHaulReplacementYear() != null) {
      errors.put(LOAD_RESTRICTION, "Estimated Load Restriction is required.");
    }
    checkReplacementOrder(errors, request);
    Long cost = request.cost();
    if (cost != null && (cost < 0 || cost > REPLACEMENT_COST_MAX)) {
      errors.put("cost", "Estimated Replacement Cost must be whole dollars from $0 to $99,999,999.");
    }
    String comment = trimmed(request.costComment());
    if (comment != null && comment.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX) {
      errors.put("costComment", "Replacement Cost Comments can be at most " + TEXT_MAX
          + " characters.");
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Replacement details cannot be saved", errors);
    }
    structures.updateReplacementDetails(structureId, request.closureYear(),
        request.replacementYear(), request.loadRestrictionYear(), cost, comment,
        loggedUser.getLoggedUserId());
    log.info("Updated the replacement details of structure {}", structureId);
  }

  /**
   * Load restriction not after replacement, and neither after closure — of the years that are
   * valid.
   */
  private static void checkReplacementOrder(Map<String, String> errors,
      ReplacementDetailsRequest request) {
    Integer closure = errors.containsKey(CLOSURE) ? null : request.closureYear();
    Integer replacement = errors.containsKey(REPLACEMENT) ? null : request.replacementYear();
    Integer restriction = errors.containsKey(LOAD_RESTRICTION)
        ? null
        : request.loadRestrictionYear();
    if (replacement != null && closure != null && replacement > closure) {
      errors.put(REPLACEMENT, "Estimated Replacement must not be after Estimated Closure.");
    }
    if (restriction == null) {
      return;
    }
    if (replacement != null && restriction > replacement) {
      errors.putIfAbsent(LOAD_RESTRICTION,
          "Estimated Load Restriction must not be after Estimated Replacement.");
    }
    if (closure != null && restriction > closure) {
      errors.putIfAbsent(LOAD_RESTRICTION,
          "Estimated Load Restriction must not be after Estimated Closure.");
    }
  }

  /**
   * A four-digit year — 1000 to 9999 — or none. Legacy took any whole number its four-character box
   * held, so 200 was saved and only warned about.
   */
  private static void checkYear(Map<String, String> errors, String field, String label,
      Integer year) {
    if (year != null && (year < YEAR_MIN || year > YEAR_MAX)) {
      errors.put(field, label + " must be a 4-digit year, e.g. 2004.");
    }
  }

  /** A cost the column holds: whole dollars from 0 to 999,999, or none. */
  private static void checkCost(Map<String, String> errors, String field, String label,
      Long cost) {
    if (cost != null && (cost < 0 || cost > COST_MAX)) {
      errors.put(field, label + " must be from $0 to $999,999.");
    }
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
