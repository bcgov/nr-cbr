package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.PierTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.SpecialEquipmentRequirementCodeRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionScheduleRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PierRequest;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SpanRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailsRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Add Structure's rules, field by field, each keyed as the page names its box: the top fields by
 * name ({@code structureName}), a section's by section and name ({@code bridge.deckWidth}), the
 * span and pier lists as {@code spans} and {@code piers}.
 *
 * <p>Each section is checked by its structure-page card's own rules, against an empty record — so
 * nothing is "required once it has a value" — and then, on top, the completeness rules: what the
 * structure page would otherwise list as outstanding is required here, so a structure is not born
 * incomplete. The one exception is Estimated Load Restriction, a P.Eng's, which stays optional, as
 * on legacy's new-structure page.
 *
 * <p>The site, as legacy's Site Detail button and save: it must exist and be complete, and not be
 * Proposed or closed. Legacy checked closed only in the browser and accepted no site at all.
 */
@Component
public class StructureCreateValidator {

  static final Set<String> BRIDGE_TYPES = Set.of("PB", "TB");
  static final Set<String> CULVERT_TYPES = Set.of("CUL", "WLC");
  static final String WOOD_LOG = "WLC";

  private static final String IS_REQUIRED = " is required.";
  private static final String CLOSED = "DAC";
  private static final int NAME_MAX = 14;
  private static final int TEXT_MAX = 2000;
  private static final int FREQUENCY_MIN = 1;
  private static final int FREQUENCY_MAX = 6;
  private static final long SPAN_NUMBER_MAX = 99_999L;
  private static final long PIER_NUMBER_MAX = 9_999_999_999L;
  private static final BigDecimal SPAN_LENGTH_MAX = new BigDecimal("99999.999");
  private static final int SPAN_LENGTH_SCALE = 3;
  private static final String SPANS = "spans";
  private static final String PIERS = "piers";

  private final CrossingStructureRepository structures;
  private final CrossingSiteRepository sites;
  private final PierTypeCodeRepository pierTypes;
  private final SpecialEquipmentRequirementCodeRepository equipment;
  private final StructureInformationService information;
  private final StructureDetailsService details;
  private final StructureBridgeService bridges;
  private final StructureCulvertService culverts;
  private final StructureLoadRatingsService loadRatings;
  private final LoggedUserHelper loggedUser;

  @SuppressWarnings("java:S107") // Each card's own rules, and the lookups the lists need.
  public StructureCreateValidator(
      CrossingStructureRepository structures,
      CrossingSiteRepository sites,
      PierTypeCodeRepository pierTypes,
      SpecialEquipmentRequirementCodeRepository equipment,
      StructureInformationService information,
      StructureDetailsService details,
      StructureBridgeService bridges,
      StructureCulvertService culverts,
      StructureLoadRatingsService loadRatings,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.sites = sites;
    this.pierTypes = pierTypes;
    this.equipment = equipment;
    this.information = information;
    this.details = details;
    this.bridges = bridges;
    this.culverts = culverts;
    this.loadRatings = loadRatings;
    this.loggedUser = loggedUser;
  }

  /**
   * What the checks found: every reason the structure cannot be added, keyed by field (empty when
   * it can), and the design load rating to save — fixed by the vehicle, or typed.
   */
  public record Result(Map<String, String> errors, Long designLoadRating) {}

  /** Checks a new structure; {@code request} has every section, as {@link #complete} makes it. */
  public Result validate(StructureCreateRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    checkName(errors, request.structureName());
    checkSite(errors, trimmed(request.siteId()));
    String typeClass = trimmed(request.typeClassCode());
    if (typeClass == null) {
      errors.put("typeClassCode", "Type/Class" + IS_REQUIRED);
    } else if (!BRIDGE_TYPES.contains(typeClass) && !CULVERT_TYPES.contains(typeClass)) {
      errors.put("typeClassCode", "Type/Class is not one of the listed values.");
    }

    CrossingStructureEntity empty = CrossingStructureEntity.builder().build();
    Map<String, String> section = new LinkedHashMap<>();
    details.validateStructure(section, empty, request.details());
    if (trimmed(request.details().sourceCode()) == null) {
      section.putIfAbsent("sourceCode", "Source" + IS_REQUIRED);
    }
    putAll(errors, "details", section);

    // Each card once Type/Class says which; with none chosen there is nothing to check them by.
    if (typeClass != null && BRIDGE_TYPES.contains(typeClass)) {
      checkBridge(errors, request, typeClass);
    } else if (typeClass != null && CULVERT_TYPES.contains(typeClass)) {
      checkCulvert(errors, request.culvert(), WOOD_LOG.equals(typeClass));
    }

    section = new LinkedHashMap<>();
    Long designRating = loadRatings.validateDetails(section, empty, request.loadRating());
    putAll(errors, "loadRating", section);
    checkReplacement(errors, empty, request.replacement());
    checkSchedule(errors, request.schedule());
    String comment = trimmed(request.comment());
    if (comment != null && comment.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX) {
      errors.put("comment", "Comment can be at most " + TEXT_MAX + " characters.");
    }
    return new Result(errors, designRating);
  }

  /** The request with an empty record for any section not sent, so each can be checked. */
  public static StructureCreateRequest complete(StructureCreateRequest request) {
    return new StructureCreateRequest(
        request.structureName(),
        request.siteId(),
        request.typeClassCode(),
        Objects.requireNonNullElse(request.details(),
            new StructureDetailsRequest(null, null, null, null, null, null, null, null, null)),
        Objects.requireNonNullElse(request.bridge(),
            new BridgeDetailsRequest(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null)),
        Objects.requireNonNullElse(request.culvert(),
            new CulvertDetailsRequest(null, null, null, null, null, null, null, null, null, null,
                null, null, null)),
        Objects.requireNonNullElse(request.spans(), List.of()),
        Objects.requireNonNullElse(request.piers(), List.of()),
        Objects.requireNonNullElse(request.loadRating(),
            new LoadRatingDetailsRequest(null, null, null, null, null)),
        Objects.requireNonNullElse(request.replacement(),
            new ReplacementDetailsRequest(null, null, null, null, null)),
        Objects.requireNonNullElse(request.schedule(),
            new InspectionScheduleRequest(false, null, null, null, null)),
        request.comment());
  }

  /** Structure #: required, fits its column, and no other structure's — archived ones included. */
  private void checkName(Map<String, String> errors, String typed) {
    String name = trimmed(typed);
    if (name == null) {
      errors.put("structureName", "Structure #" + IS_REQUIRED);
    } else if (name.getBytes(StandardCharsets.UTF_8).length > NAME_MAX) {
      errors.put("structureName", "Structure # can be at most " + NAME_MAX + " characters.");
    } else if (structures.findByCrossingStructureName(name.toUpperCase(Locale.ROOT)).isPresent()) {
      errors.put("structureName", "Structure " + name.toUpperCase(Locale.ROOT)
          + " already exists.");
    }
  }

  /** Site #: required, and a site a structure may stand on — and not closed. */
  private void checkSite(Map<String, String> errors, String siteId) {
    if (siteId == null) {
      errors.put("siteId", "Site #" + IS_REQUIRED);
      return;
    }
    String problem = information.siteProblem(siteId);
    if (problem == null && sites.findById(siteId)
        .filter(site -> CLOSED.equals(site.getCrossingSiteStatusCode())).isPresent()) {
      problem = "Site " + siteId + " is closed (deactivated). A structure cannot be added to it.";
    }
    if (problem != null) {
      errors.put("siteId", problem);
    }
  }

  /** The Bridge card, its completeness, and the spans and piers. */
  private void checkBridge(Map<String, String> errors, StructureCreateRequest request,
      String typeClass) {
    BridgeDetailsRequest given = request.bridge();
    BridgeDetailsRequest bridge = new BridgeDetailsRequest(typeClass, given.portable(),
        given.needleBeams(), given.totalBridgeLength(), given.superstructureTypeCode(),
        given.superstructureComment(), given.deckTypeCode(), given.deckTypeComment(),
        given.deckWidth(), given.runningSurfaceCode(), given.curbTypeCode(),
        given.curbTypeComment(), given.leftAbutmentCode(), given.rightAbutmentCode(),
        given.abutmentComment());
    List<SpanRequest> spans = request.spans();
    List<PierRequest> piers = request.piers();
    BigDecimal spanTotal = spans.stream()
        .map(SpanRequest::lengthMetres)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, String> section = new LinkedHashMap<>();
    bridges.validate(section, ForestServiceBridgeEntity.builder().build(), bridge, spanTotal);
    required(section, "totalBridgeLength", "Bridge Length", bridge.totalBridgeLength());
    required(section, "deckWidth", "Deck Width", bridge.deckWidth());
    required(section, "superstructureTypeCode", "Superstructure",
        trimmed(bridge.superstructureTypeCode()));
    required(section, "deckTypeCode", "Deck Type", trimmed(bridge.deckTypeCode()));
    required(section, "runningSurfaceCode", "Running Surface",
        trimmed(bridge.runningSurfaceCode()));
    required(section, "leftAbutmentCode", "Left Abutment", trimmed(bridge.leftAbutmentCode()));
    required(section, "rightAbutmentCode", "Right Abutment", trimmed(bridge.rightAbutmentCode()));
    putAll(errors, "bridge", section);
    checkSpans(errors, spans, piers.size());
    checkPiers(errors, piers);
  }

  /** At least one span, more spans than piers, and each span as the Spans & Piers tab's. */
  private static void checkSpans(Map<String, String> errors, List<SpanRequest> spans,
      int pierCount) {
    if (spans.isEmpty()) {
      errors.put(SPANS, "At least one span is required.");
      return;
    }
    if (spans.size() <= pierCount) {
      errors.put(SPANS, "Number of spans must be greater than number of piers.");
      return;
    }
    Set<Long> numbers = new HashSet<>();
    for (SpanRequest span : spans) {
      String problem = spanProblem(span, numbers);
      if (problem != null) {
        errors.put(SPANS, problem);
        return;
      }
    }
  }

  private static String spanProblem(SpanRequest span, Set<Long> numbers) {
    Long number = span.number();
    if (number == null || number <= 0 || number > SPAN_NUMBER_MAX) {
      return "Each span needs a Span Number from 1 to " + SPAN_NUMBER_MAX + ".";
    }
    if (!numbers.add(number)) {
      return "Span Number " + number + " is used twice.";
    }
    BigDecimal length = span.lengthMetres();
    if (length == null || length.signum() < 0
        || length.stripTrailingZeros().scale() > SPAN_LENGTH_SCALE
        || length.compareTo(SPAN_LENGTH_MAX) > 0) {
      return "Span " + number + " needs a Length from 0 to " + SPAN_LENGTH_MAX.toPlainString()
          + " metres, with at most " + SPAN_LENGTH_SCALE + " decimal places.";
    }
    return null;
  }

  /** Each pier as the Spans & Piers tab's: a number, unique, and a listed type. */
  private void checkPiers(Map<String, String> errors, List<PierRequest> piers) {
    Set<Long> numbers = new HashSet<>();
    for (PierRequest pier : piers) {
      Long number = pier.number();
      String type = trimmed(pier.typeCode());
      String problem = null;
      if (number == null || number <= 0 || number > PIER_NUMBER_MAX) {
        problem = "Each pier needs a Pier Number from 1 to " + PIER_NUMBER_MAX + ".";
      } else if (!numbers.add(number)) {
        problem = "Pier Number " + number + " is used twice.";
      } else if (type == null || !pierTypes.isCurrent(type)) {
        problem = "Pier " + number + " needs one of the listed types.";
      }
      if (problem != null) {
        errors.put(PIERS, problem);
        return;
      }
    }
  }

  /** The Culvert card and its completeness. */
  private void checkCulvert(Map<String, String> errors, CulvertDetailsRequest culvert,
      boolean woodLog) {
    Map<String, String> section = new LinkedHashMap<>();
    culverts.validate(section, ForestServiceCulvertEntity.builder().build(), culvert, woodLog);
    required(section, "culvertNumber", "Culvert Number", culvert.culvertNumber());
    required(section, "lengthMetres", "Culvert Length", culvert.lengthMetres());
    required(section, "openingHeightMm", "Opening Height", culvert.openingHeightMm());
    required(section, "openingWidthMm", "Opening Width", culvert.openingWidthMm());
    required(section, "materialCode", "Culvert Material", trimmed(culvert.materialCode()));
    if (!woodLog) {
      required(section, "culvertTypeCode", "Culvert Type", trimmed(culvert.culvertTypeCode()));
    }
    putAll(errors, "culvert", section);
  }

  /** Replacement Details: a P.Eng's to set, by its card's rules; Load Restriction optional. */
  private void checkReplacement(Map<String, String> errors, CrossingStructureEntity empty,
      ReplacementDetailsRequest replacement) {
    if (isEmpty(replacement)) {
      return;
    }
    if (!loggedUser.isPeng()) {
      errors.put("replacement", "Only a P.Eng can enter Replacement Details.");
      return;
    }
    Map<String, String> section = new LinkedHashMap<>();
    details.validateReplacement(section, empty, replacement);
    putAll(errors, "replacement", section);
  }

  private static boolean isEmpty(ReplacementDetailsRequest replacement) {
    return replacement.closureYear() == null && replacement.replacementYear() == null
        && replacement.loadRestrictionYear() == null && replacement.cost() == null
        && trimmed(replacement.costComment()) == null;
  }

  /**
   * The inspection schedule: Next Planned Routine Inspection and the frequency required; a close
   * proximity inspection's equipment, when one is required, a listed one.
   */
  private void checkSchedule(Map<String, String> errors, InspectionScheduleRequest schedule) {
    Integer frequency = schedule.routineFrequencyYears();
    if (frequency == null) {
      errors.put("schedule.routineFrequencyYears", "Routine Inspection Frequency" + IS_REQUIRED);
    } else if (frequency < FREQUENCY_MIN || frequency > FREQUENCY_MAX) {
      errors.put("schedule.routineFrequencyYears",
          "Routine Inspection Frequency must be from 1 to 6.");
    }
    if (schedule.nextRoutineDate() == null) {
      errors.put("schedule.nextRoutineDate", "Next Planned Routine Inspection" + IS_REQUIRED);
    }
    String code = trimmed(schedule.closeProximityEquipmentCode());
    if (schedule.closeProximityRequired() && code != null && !equipment.existsById(code)) {
      errors.put("schedule.closeProximityEquipmentCode", "Close Proximity Special Equipment "
          + "Requirements is not one of the listed requirements.");
    }
  }

  /** "Label is required." for a value not given, unless the field already has a message. */
  private static void required(Map<String, String> errors, String field, String label,
      Object value) {
    if (value == null) {
      errors.putIfAbsent(field, label + IS_REQUIRED);
    }
  }

  private static void putAll(Map<String, String> errors, String prefix,
      Map<String, String> section) {
    section.forEach((field, message) -> errors.put(prefix + "." + field, message));
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
