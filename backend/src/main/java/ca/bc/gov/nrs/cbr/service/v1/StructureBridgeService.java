package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.SpanPierConflictException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.repository.v1.AbutmentCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.DeckTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeSpanRepository;
import ca.bc.gov.nrs.cbr.repository.v1.RunningSurfaceCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCurbTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.SuperstructureTypeCodeRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeDetailsRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Details tab's Bridge card — legacy's bridge rows, with the structure's Type/Class and
 * Portable Structure, which are edited here because their rules check this card's own fields.
 *
 * <p>Legacy's blocking rules ({@code StructureForm.validate}, {@code Bridge.validate}), each beside
 * its field, plus values that fit their columns:
 *
 * <ul>
 *   <li>Type/Class changes only between Durable (PB) and Non-Durable (TB) Bridge.
 *   <li>A <b>Durable</b> Bridge needs a Curb Type, and no temporary superstructure (LGST, RAIL, TU)
 *       or abutment (CLT, PT, ST).
 *   <li>A <b>Portable</b> structure's superstructure is Steel Box Beam, Steel I-beam, Steel
 *       Truss/Bailey/Acrow or timber stringers (SBB, SIB, STBA, TU, TT).
 *   <li>Bridge Length, Deck Width, Superstructure, Deck Type, Running Surface and the abutments are
 *       required once the bridge has them (legacy's tier rule); an Other needs its details.
 *   <li>Bridge Length is at least the spans' total.
 * </ul>
 *
 * <p>Legacy's range warnings — Bridge Length outside 0–400, Deck Width outside 0–12 — never blocked
 * its save and do not block this one; the page shows them. A comment is kept only with its Other,
 * where legacy kept whatever a hidden box still held. The bridge's unmapped columns (its portable
 * storage location and org unit) are not touched, where legacy's save blanked them.
 */
@Service
public class StructureBridgeService {

  private static final Logger log = LoggerFactory.getLogger(StructureBridgeService.class);

  private static final String DURABLE = "PB";
  private static final String NON_DURABLE = "TB";
  private static final Set<String> BRIDGE_TYPES = Set.of(DURABLE, NON_DURABLE);
  private static final String OTHER = "OTH";
  private static final Set<String> TEMPORARY_SUPERSTRUCTURES = Set.of("LGST", "RAIL", "TU");
  private static final Set<String> TEMPORARY_ABUTMENTS = Set.of("CLT", "PT", "ST");
  private static final Set<String> PORTABLE_SUPERSTRUCTURES =
      Set.of("SBB", "SIB", "STBA", "TU", "TT");
  /** {@code TOTAL_BRIDGE_LENGTH NUMBER(8,3)}. */
  private static final BigDecimal LENGTH_MAX = new BigDecimal("99999.999");
  /** {@code DECK_WIDTH NUMBER(7,2)}. */
  private static final BigDecimal WIDTH_MAX = new BigDecimal("99999.99");
  private static final int TEXT_MAX = 2000;
  private static final String YES = "Y";
  private static final String NO = "N";

  private static final String TYPE_CLASS = "typeClassCode";
  private static final String LENGTH = "totalBridgeLength";
  private static final String SUPERSTRUCTURE = "superstructureTypeCode";
  private static final String CURB = "curbTypeCode";
  private static final String LEFT = "leftAbutmentCode";
  private static final String RIGHT = "rightAbutmentCode";

  private final CrossingStructureRepository structures;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceBridgeSpanRepository spans;
  private final SuperstructureTypeCodeRepository superstructureTypes;
  private final DeckTypeCodeRepository deckTypes;
  private final RunningSurfaceCodeRepository runningSurfaces;
  private final StructureCurbTypeCodeRepository curbTypes;
  private final AbutmentCodeRepository abutments;
  private final LoggedUserHelper loggedUser;

  @SuppressWarnings("java:S107") // One repository per code table the card offers.
  public StructureBridgeService(
      CrossingStructureRepository structures,
      ForestServiceBridgeRepository bridges,
      ForestServiceBridgeSpanRepository spans,
      SuperstructureTypeCodeRepository superstructureTypes,
      DeckTypeCodeRepository deckTypes,
      RunningSurfaceCodeRepository runningSurfaces,
      StructureCurbTypeCodeRepository curbTypes,
      AbutmentCodeRepository abutments,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.bridges = bridges;
    this.spans = spans;
    this.superstructureTypes = superstructureTypes;
    this.deckTypes = deckTypes;
    this.runningSurfaces = runningSurfaces;
    this.curbTypes = curbTypes;
    this.abutments = abutments;
    this.loggedUser = loggedUser;
  }

  /**
   * Saves the Bridge card.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws SpanPierConflictException  if the structure has no bridge record
   * @throws FieldValidationException   with a message for each field at fault
   */
  @Transactional
  public void update(long structureId, BridgeDetailsRequest request) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    ForestServiceBridgeEntity bridge = bridges
        .findFirstByCrossingStructureIdOrderByForestServiceBridgeId(structureId)
        .orElseThrow(() -> new SpanPierConflictException(
            "Structure " + structureId + " has no bridge record, so it has no bridge details."));

    Map<String, String> errors = new LinkedHashMap<>();
    String typeClass = trimmed(request.typeClassCode());
    if (typeClass == null) {
      errors.put(TYPE_CLASS, "Type/Class is required.");
    } else if (!BRIDGE_TYPES.contains(typeClass)) {
      errors.put(TYPE_CLASS,
          "Type/Class can only be changed between Durable Bridge and Non-Durable Bridge.");
    }
    boolean durable = DURABLE.equals(typeClass);
    if (request.portable() == null) {
      errors.put("portable", "Portable Structure is required.");
    }
    if (request.needleBeams() == null) {
      errors.put("needleBeams", "Needle Beams is required.");
    }

    BigDecimal length = request.totalBridgeLength();
    checkMetres(errors, LENGTH, "Bridge Length", length, bridge.getTotalBridgeLength(),
        LENGTH_MAX, 3);
    if (length != null && !errors.containsKey(LENGTH)) {
      BigDecimal spanTotal = spans
          .findByForestServiceBridgeIdOrderBySpanNumberAsc(bridge.getForestServiceBridgeId())
          .stream()
          .map(ForestServiceBridgeSpanEntity::getSpanLength)
          .filter(Objects::nonNull)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (length.compareTo(spanTotal) < 0) {
        errors.put(LENGTH, "Bridge Length must be at least the spans' total of "
            + spanTotal.stripTrailingZeros().toPlainString() + " metres.");
      }
    }
    checkMetres(errors, "deckWidth", "Deck Width", request.deckWidth(), bridge.getDeckWidth(),
        WIDTH_MAX, 2);

    String superstructure = trimmed(request.superstructureTypeCode());
    checkCode(errors, SUPERSTRUCTURE, "Superstructure", superstructure,
        bridge.getSuperstructureTypeCode(), superstructureTypes::existsById);
    checkOther(errors, "superstructureComment", "Superstructure Details", superstructure,
        request.superstructureComment());
    String deckType = trimmed(request.deckTypeCode());
    checkCode(errors, "deckTypeCode", "Deck Type", deckType, bridge.getDeckTypeCode(),
        deckTypes::existsById);
    checkOther(errors, "deckTypeComment", "Deck Type Details", deckType,
        request.deckTypeComment());
    checkCode(errors, "runningSurfaceCode", "Running Surface",
        trimmed(request.runningSurfaceCode()), bridge.getRunningSurfaceCode(),
        runningSurfaces::existsById);
    String curb = trimmed(request.curbTypeCode());
    if (curb == null && durable) {
      errors.put(CURB, "Curb Type is required for a Durable Bridge.");
    } else {
      checkCode(errors, CURB, "Curb Type", curb, bridge.getStructureCurbTypeCode(),
          curbTypes::existsById);
    }
    checkOther(errors, "curbTypeComment", "Curb Type Description", curb,
        request.curbTypeComment());
    String left = trimmed(request.leftAbutmentCode());
    String right = trimmed(request.rightAbutmentCode());
    checkCode(errors, LEFT, "Left Abutment", left, bridge.getLeftAbutmentCode(),
        abutments::existsById);
    checkCode(errors, RIGHT, "Right Abutment", right, bridge.getRightAbutmentCode(),
        abutments::existsById);
    checkOther(errors, "abutmentComment", "Abutment Details",
        OTHER.equals(left) || OTHER.equals(right) ? OTHER : null, request.abutmentComment());

    if (Boolean.TRUE.equals(request.portable()) && !errors.containsKey(SUPERSTRUCTURE)
        && !PORTABLE_SUPERSTRUCTURES.contains(superstructure)) {
      errors.put(SUPERSTRUCTURE, "Only Steel Box Beam, Steel I-beam, Steel Truss/Bailey/Acrow, "
          + "Timber - Untreated Stringers and Timber - Treated Stringers are allowed when Portable "
          + "Structure is checked.");
    }
    if (durable) {
      if (!errors.containsKey(SUPERSTRUCTURE) && TEMPORARY_SUPERSTRUCTURES.contains(superstructure)) {
        errors.put(SUPERSTRUCTURE,
            "Superstructure: temporary materials are not permitted in a Durable Bridge.");
      }
      if (!errors.containsKey(LEFT) && TEMPORARY_ABUTMENTS.contains(left)) {
        errors.put(LEFT, "Left Abutment: temporary materials are not permitted in a Durable Bridge.");
      }
      if (!errors.containsKey(RIGHT) && TEMPORARY_ABUTMENTS.contains(right)) {
        errors.put(RIGHT,
            "Right Abutment: temporary materials are not permitted in a Durable Bridge.");
      }
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Bridge details cannot be saved", errors);
    }

    String user = loggedUser.getLoggedUserId();
    structures.updateBridgeIdentity(structureId, typeClass,
        Boolean.TRUE.equals(request.portable()) ? YES : NO, user);
    bridges.save(bridge.toBuilder()
        .needleBeamInd(Boolean.TRUE.equals(request.needleBeams()) ? YES : NO)
        .totalBridgeLength(length)
        .superstructureTypeCode(superstructure)
        .superstructureComment(otherComment(superstructure, request.superstructureComment()))
        .deckTypeCode(deckType)
        .deckTypeComment(otherComment(deckType, request.deckTypeComment()))
        .deckWidth(request.deckWidth())
        .runningSurfaceCode(trimmed(request.runningSurfaceCode()))
        .structureCurbTypeCode(curb)
        .structureCurbTypeCmt(otherComment(curb, request.curbTypeComment()))
        .leftAbutmentCode(left)
        .rightAbutmentCode(right)
        .abutmentComment(otherComment(OTHER.equals(left) || OTHER.equals(right) ? OTHER : null,
            request.abutmentComment()))
        .build());
    log.info("Updated the bridge details of structure {}", structureId);
  }

  /**
   * A length in metres the column holds, required once the bridge has one (legacy's tier rule).
   */
  private static void checkMetres(Map<String, String> errors, String field, String label,
      BigDecimal value, BigDecimal stored, BigDecimal max, int scale) {
    if (value == null) {
      if (stored != null) {
        errors.put(field, label + " is required.");
      }
    } else if (value.signum() < 0) {
      errors.put(field, label + " cannot be negative.");
    } else if (value.stripTrailingZeros().scale() > scale) {
      errors.put(field, label + " can have at most " + scale + " decimal places.");
    } else if (value.compareTo(max) > 0) {
      errors.put(field, label + " can be at most " + max.toPlainString() + " metres.");
    }
  }

  /**
   * A code that exists, required once the bridge has one. The bridge's own code, since expired,
   * may be kept.
   */
  private static void checkCode(Map<String, String> errors, String field, String label,
      String code, String stored, Predicate<String> exists) {
    if (code == null) {
      if (trimmed(stored) != null) {
        errors.put(field, label + " is required.");
      }
    } else if (!code.equals(stored) && !exists.test(code)) {
      errors.put(field, label + " is not one of the listed values.");
    }
  }

  /** An Other's details: required with it, at most 2000 bytes. */
  private static void checkOther(Map<String, String> errors, String field, String label,
      String code, String comment) {
    if (!OTHER.equals(code)) {
      return;
    }
    String text = trimmed(comment);
    if (text == null) {
      errors.put(field, label + " is required.");
    } else if (text.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX) {
      errors.put(field, label + " can be at most " + TEXT_MAX + " characters.");
    }
  }

  /** A comment kept only with its Other. */
  private static String otherComment(String code, String comment) {
    return OTHER.equals(code) ? trimmed(comment) : null;
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
