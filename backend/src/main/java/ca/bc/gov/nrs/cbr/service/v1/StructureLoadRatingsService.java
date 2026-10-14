package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.LoadRatingNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignLoadRatingXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.LoadRatingReasonXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.DesignLoadRatingXrefRepository;
import ca.bc.gov.nrs.cbr.repository.v1.DesignVehicleLoadCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.LoadPostingSignWrrntdCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.LoadRatingReasonXrefRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StrctreLoadRatingRsnCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingCodesResponse.DesignVehicle;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingCodesResponse.Reason;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Details tab's Load Rating Details — its four fields, and the manual load ratings a P.Eng
 * adds, changes and deletes.
 *
 * <p><b>The fields</b>, by role as legacy's page: Load Rating Review Required at Level 1; Load
 * Posting Signs, Design Vehicle Configuration and a typed design rating at Level 2; the Other
 * vehicle's words by a P.Eng only — so a Level 2 user who picks Other cannot save it, as in legacy.
 * A vehicle that fixes a design load rating ({@code STRCTRE_DESGN_LOAD_RATING_XREF}) sets it.
 *
 * <p><b>The manual ratings</b> are written at once, where legacy held them in the page until its
 * Save, and with legacy's effects on the structure: the current rating is copied onto it; adding the
 * rating that becomes current clears Load Rating Review Required; deleting the current one sets it
 * again when the structure has no reviewed or accepted inspection ({@code INSERT_LOAD_RATING},
 * {@code UPDATE_CURRENT_LOAD_RATING}, {@code DELETE_LOAD_RATING}). Which rating is current is
 * {@link LoadRatingService}'s rule. A rating an inspection brought is not changed here.
 */
@Service
public class StructureLoadRatingsService {

  private static final Logger log = LoggerFactory.getLogger(StructureLoadRatingsService.class);

  private static final String OTHER_VEHICLE = "OTHER";
  private static final String OTHER_REASON = "OTHR";
  /** Legacy's placeholder for a new structure: a 0-tonne manual rating, reason NS. */
  private static final String NEW_STRUCTURE = "NS";
  private static final List<String> REVIEWED = List.of("RVD", "ACC");
  /** {@code LOAD_RATING} and {@code DESIGN_LOAD_RATING} are {@code NUMBER(6)}. */
  private static final long RATING_MAX = 999_999L;
  private static final int VEHICLE_TEXT_MAX = 2000;
  private static final int REASON_TEXT_MAX = 4000;
  private static final String YES = "Y";
  private static final String NO = "N";
  private static final String LEVEL_2_ONLY = " can be changed only at Level 2 and up.";

  private static final String RATING = "rating";
  private static final String REASON = "reasonCode";
  private static final String REASON_COMMENT = "reasonComment";
  private static final String POSTING_SIGNS = "loadPostingSignsCode";
  private static final String VEHICLE_COMMENT = "designVehicleComment";
  private static final String DESIGN_RATING = "designLoadRating";

  private final CrossingStructureRepository structures;
  private final StructureLoadRatingRepository loadRatings;
  private final StructureInspectionRepository inspections;
  private final LoadPostingSignWrrntdCodeRepository postingSigns;
  private final DesignVehicleLoadCodeRepository vehicles;
  private final DesignLoadRatingXrefRepository vehicleRatings;
  private final StrctreLoadRatingRsnCodeRepository reasons;
  private final LoadRatingReasonXrefRepository reasonDeltas;
  private final LoadRatingService current;
  private final LoggedUserHelper loggedUser;

  @SuppressWarnings("java:S107") // One repository per table the section reads or writes.
  public StructureLoadRatingsService(
      CrossingStructureRepository structures,
      StructureLoadRatingRepository loadRatings,
      StructureInspectionRepository inspections,
      LoadPostingSignWrrntdCodeRepository postingSigns,
      DesignVehicleLoadCodeRepository vehicles,
      DesignLoadRatingXrefRepository vehicleRatings,
      StrctreLoadRatingRsnCodeRepository reasons,
      LoadRatingReasonXrefRepository reasonDeltas,
      LoadRatingService current,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.loadRatings = loadRatings;
    this.inspections = inspections;
    this.postingSigns = postingSigns;
    this.vehicles = vehicles;
    this.vehicleRatings = vehicleRatings;
    this.reasons = reasons;
    this.reasonDeltas = reasonDeltas;
    this.current = current;
    this.loggedUser = loggedUser;
  }

  /** The section's lists: posting signs, design vehicles with their fixed ratings, and reasons. */
  @Transactional(readOnly = true)
  public LoadRatingCodesResponse codes() {
    Map<String, Long> fixed = vehicleRatings.findAll().stream()
        .filter(row -> row.getDesignLoadRating() != null && row.getDesignLoadRating() >= 0)
        .collect(Collectors.toMap(DesignLoadRatingXrefEntity::getDesignVehicleLoadCode,
            DesignLoadRatingXrefEntity::getDesignLoadRating, (first, second) -> first));
    Map<String, List<String>> deltas = reasonDeltas.findAll().stream()
        .collect(Collectors.groupingBy(LoadRatingReasonXrefEntity::getReasonCode,
            Collectors.mapping(LoadRatingReasonXrefEntity::getDeltaCode, Collectors.toList())));
    return new LoadRatingCodesResponse(
        postingSigns.findAllCurrent().stream()
            .map(code -> new CodeOptionResponse(
                code.getLoadPostingSignWrrntdCode(), code.getDescription()))
            .toList(),
        vehicles.findAllCurrent().stream()
            .map(code -> new DesignVehicle(code.getDesignVehicleLoadCode(), code.getDescription(),
                fixed.get(code.getDesignVehicleLoadCode())))
            .toList(),
        reasons.findAllCurrent().stream()
            .map(code -> new Reason(code.getStrctreLoadRatingRsnCode(), code.getDescription(),
                deltas.getOrDefault(code.getStrctreLoadRatingRsnCode(), List.of())))
            .toList());
  }

  /**
   * Saves the section's fields.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   with a message for each field at fault
   */
  @Transactional
  public void updateDetails(long structureId, LoadRatingDetailsRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    boolean level2 = loggedUser.canDestroy();
    Map<String, String> errors = new LinkedHashMap<>();

    if (request.reviewRequired() == null) {
      errors.put("reviewRequired", "Load Rating Review Required is required.");
    }
    String posting = trimmed(request.loadPostingSignsCode());
    checkPostingSigns(errors, structure, posting, level2);
    String vehicle = trimmed(request.designVehicleCode());
    boolean vehicleChanged = !Objects.equals(vehicle, trimmed(structure.getDesignVehicleLoadCode()));
    if (vehicleChanged && !level2) {
      errors.put("designVehicleCode", "Design Vehicle Configuration" + LEVEL_2_ONLY);
    } else if (vehicleChanged && vehicle != null && !vehicles.existsById(vehicle)) {
      errors.put("designVehicleCode", "Design Vehicle Configuration is not one of the listed values.");
    }
    String comment = OTHER_VEHICLE.equals(vehicle) ? trimmed(request.designVehicleComment()) : null;
    if (OTHER_VEHICLE.equals(vehicle)) {
      checkVehicleComment(errors, structure, comment);
    }
    Long design = designRating(errors, structure, request, vehicle, vehicleChanged, level2);
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Load rating details cannot be saved", errors);
    }

    structures.updateLoadRatingDetails(structureId, posting,
        Boolean.TRUE.equals(request.reviewRequired()) ? YES : NO, vehicle, comment,
        design == null ? null : BigDecimal.valueOf(design), loggedUser.getLoggedUserId());
    log.info("Updated the load rating details of structure {}", structureId);
  }

  /** Are Load Posting Signs Warranted?: required, a listed value, changed only at Level 2. */
  private void checkPostingSigns(Map<String, String> errors, CrossingStructureEntity structure,
      String posting, boolean level2) {
    boolean postingChanged = !Objects.equals(posting, structure.getLoadPostingSignWrrntdCode());
    if (postingChanged && !level2) {
      errors.put(POSTING_SIGNS, "Are Load Posting Signs Warranted?" + LEVEL_2_ONLY);
    } else if (posting == null) {
      errors.put(POSTING_SIGNS, "Are Load Posting Signs Warranted? is required.");
    } else if (postingChanged && !postingSigns.existsById(posting)) {
      errors.put(POSTING_SIGNS,
          "Are Load Posting Signs Warranted? is not one of the listed values.");
    }
  }

  /** An Other vehicle's details: a P.Eng's to change, required, at most 2000 bytes. */
  private void checkVehicleComment(Map<String, String> errors, CrossingStructureEntity structure,
      String comment) {
    boolean commentChanged = !Objects.equals(comment, trimmed(structure.getDesignVehicleCmt()));
    if (commentChanged && !loggedUser.isPeng()) {
      errors.put(VEHICLE_COMMENT,
          "Only a P.Eng can enter the Design Vehicle Configuration details.");
    } else if (comment == null) {
      errors.put(VEHICLE_COMMENT, "Design Vehicle Configuration Details is required.");
    } else if (tooLong(comment, VEHICLE_TEXT_MAX)) {
      errors.put(VEHICLE_COMMENT,
          "Design Vehicle Configuration Details can be at most " + VEHICLE_TEXT_MAX
              + " characters.");
    }
  }

  /**
   * The design load rating to save. Choosing a vehicle that fixes it sets it, as legacy's page did
   * on the change; a vehicle left as it was keeps the rating stored. Otherwise the rating is typed:
   * Level 2 to change, 1 to the column's limit.
   */
  private Long designRating(Map<String, String> errors, CrossingStructureEntity structure,
      LoadRatingDetailsRequest request, String vehicle, boolean vehicleChanged, boolean level2) {
    Long stored = structure.getDesignLoadRating() == null
        ? null
        : structure.getDesignLoadRating().longValue();
    Optional<Long> fixed = fixedRating(vehicle);
    if (fixed.isPresent()) {
      return vehicleChanged ? fixed.get() : stored;
    }
    Long design = request.designLoadRating();
    if (!Objects.equals(design, stored) && !level2) {
      errors.put(DESIGN_RATING, "Original Design Load Rating" + LEVEL_2_ONLY);
    } else if (design != null && design < 1) {
      errors.put(DESIGN_RATING,
          "Original Design Load Rating GVW (tonnes) must be greater than or equal to 1.");
    } else if (design != null && design > RATING_MAX) {
      errors.put(DESIGN_RATING,
          "Original Design Load Rating GVW (tonnes) can be at most " + RATING_MAX + ".");
    }
    return design;
  }

  /**
   * Adds a manual load rating — legacy's Add Load Rating, a P.Eng's. Refused while the newest rating
   * is legacy's 0-tonne New Structure placeholder, which is to be edited instead.
   *
   * @return the new rating's id
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   with a message for each field at fault
   */
  @Transactional
  public String add(long structureId, LoadRatingRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    Map<String, String> errors = new LinkedHashMap<>();
    if (structure.getCrossingSiteId() == null) {
      errors.put(RATING, "This structure has no site, so a load rating cannot be recorded.");
    }
    current.currentLoadRating(structureId)
        .filter(rating -> rating.getInspectionId() == null
            && NEW_STRUCTURE.equals(rating.getReasonCode())
            && rating.getLoadRating() != null && rating.getLoadRating().signum() == 0)
        .ifPresent(placeholder -> errors.put(RATING,
            "Edit the 0-tonne New Structure rating rather than adding another."));
    validate(errors, request, true);
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Load rating cannot be added", errors);
    }

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    StructureLoadRatingEntity saved = loadRatings.save(StructureLoadRatingEntity.builder()
        .crossingStructureId(structureId)
        .loadRating(BigDecimal.valueOf(request.rating()))
        .siteAtTimeOfLoadRating(structure.getCrossingSiteId())
        .reasonCode(request.reasonCode().trim())
        .reasonComment(reasonComment(request))
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build());
    // As INSERT_LOAD_RATING: only when it is now the current rating does it reach the structure.
    current.currentLoadRating(structureId)
        .filter(rating -> rating.getStructureLoadRatingId()
            .equals(saved.getStructureLoadRatingId()))
        .ifPresent(rating -> structures.updateCurrentLoadRating(structureId,
            rating.getLoadRating(), NO));
    log.info("Added load rating {} to structure {}", saved.getStructureLoadRatingId(),
        structureId);
    return String.valueOf(saved.getStructureLoadRatingId());
  }

  /**
   * Changes a manual load rating — legacy's Edit Manual Load Rating — and copies the structure's
   * current rating onto it again, as {@code UPDATE_CURRENT_LOAD_RATING}.
   *
   * @throws LoadRatingNotFoundException if the structure has no such manual rating
   * @throws FieldValidationException    with a message for each field at fault
   */
  @Transactional
  public void update(long structureId, long loadRatingId, LoadRatingRequest request) {
    StructureLoadRatingEntity rating = manualRating(structureId, loadRatingId);
    Map<String, String> errors = new LinkedHashMap<>();
    validate(errors, request, !Objects.equals(trimmed(request.reasonCode()),
        rating.getReasonCode()));
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Load rating cannot be saved", errors);
    }
    loadRatings.save(rating.toBuilder()
        .loadRating(BigDecimal.valueOf(request.rating()))
        .reasonCode(request.reasonCode().trim())
        .reasonComment(reasonComment(request))
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    structures.updateCurrentLoadRating(structureId, currentRating(structureId), null);
    log.info("Updated load rating {} of structure {}", loadRatingId, structureId);
  }

  /**
   * Deletes a manual load rating — legacy's delete on the Edit Manual Load Rating dialog. When it
   * was the current rating, Load Rating Review Required is set again unless the structure has a
   * reviewed or accepted inspection, as {@code DELETE_LOAD_RATING}.
   *
   * @throws LoadRatingNotFoundException if the structure has no such manual rating
   */
  @Transactional
  public void delete(long structureId, long loadRatingId) {
    StructureLoadRatingEntity rating = manualRating(structureId, loadRatingId);
    boolean wasCurrent = current.currentLoadRating(structureId)
        .map(StructureLoadRatingEntity::getStructureLoadRatingId)
        .filter(id -> id.equals(loadRatingId))
        .isPresent();
    loadRatings.delete(rating);
    loadRatings.flush();
    if (wasCurrent) {
      boolean reviewed = inspections.findLatestReviewedInspectionDate(structureId, REVIEWED)
          .isPresent();
      structures.updateCurrentLoadRating(structureId, currentRating(structureId),
          reviewed ? null : YES);
    }
    log.info("Deleted load rating {} of structure {}", loadRatingId, structureId);
  }

  private StructureLoadRatingEntity manualRating(long structureId, long loadRatingId) {
    return loadRatings.findById(loadRatingId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId)
            && found.getInspectionId() == null)
        .orElseThrow(() -> new LoadRatingNotFoundException(structureId, loadRatingId));
  }

  /**
   * A rating's own rules: a whole number from 0 to 999999, a reason, and Other's words.
   *
   * @param newReason whether the reason must be a current one — a rating may keep its own since
   *                  expired
   */
  private void validate(Map<String, String> errors, LoadRatingRequest request, boolean newReason) {
    if (request.rating() == null) {
      errors.putIfAbsent(RATING, "Load Rating is required.");
    } else if (request.rating() < 0 || request.rating() > RATING_MAX) {
      errors.putIfAbsent(RATING, "Load Rating must be a whole number from 0 to " + RATING_MAX + ".");
    }
    String reason = trimmed(request.reasonCode());
    if (reason == null) {
      errors.put(REASON, "Load Rating Reason is required.");
    } else if (newReason && reasons.findAllCurrent().stream()
        .noneMatch(code -> code.getStrctreLoadRatingRsnCode().equals(reason))) {
      errors.put(REASON, "Load Rating Reason is not one of the listed reasons.");
    }
    if (OTHER_REASON.equals(reason)) {
      String comment = trimmed(request.reasonComment());
      if (comment == null) {
        errors.put(REASON_COMMENT, "Load Rating Reason Comment is required for Other.");
      } else if (tooLong(comment, REASON_TEXT_MAX)) {
        errors.put(REASON_COMMENT,
            "Load Rating Reason Comment can be at most " + REASON_TEXT_MAX + " characters.");
      }
    }
  }

  private BigDecimal currentRating(long structureId) {
    return current.currentLoadRating(structureId)
        .map(StructureLoadRatingEntity::getLoadRating)
        .orElse(null);
  }

  private Optional<Long> fixedRating(String vehicle) {
    return vehicle == null
        ? Optional.empty()
        : vehicleRatings.findByDesignVehicleLoadCode(vehicle).stream()
            .map(DesignLoadRatingXrefEntity::getDesignLoadRating)
            .filter(rating -> rating != null && rating >= 0)
            .findFirst();
  }

  /** The reason's words, kept only with Other. */
  private static String reasonComment(LoadRatingRequest request) {
    return OTHER_REASON.equals(trimmed(request.reasonCode()))
        ? trimmed(request.reasonComment())
        : null;
  }

  private static boolean tooLong(String text, int max) {
    return text.getBytes(StandardCharsets.UTF_8).length > max;
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
