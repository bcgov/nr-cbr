package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.SpanPierConflictException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.EngineeredClvrtMaterialCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.EngineeredCulvertTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.HeadwallLocationCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.OpnBtomClvrtSubstrctreCodeRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertDetailsRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Details tab's Culvert card — legacy's culvert rows, with the structure's Portable Structure.
 * A culvert's Type/Class cannot change (legacy refuses any change but Durable ↔ Non-Durable Bridge),
 * so it is not edited here.
 *
 * <p>Legacy's blocking rules ({@code StructureForm.validate}), each beside its field, plus values
 * that fit their columns: Culvert Number, Length, Opening Height and Width, Culvert Type and
 * Material are required once the culvert has them (legacy's tier rule); an open-bottom culvert
 * ({@code OPN}) needs its Open Bottom Substructure; Other material needs its details. Legacy's range
 * warnings ({@code Culvert.validate}) never blocked its save and do not block this one.
 *
 * <p><b>A Wood Log Culvert</b> ({@code WLC}) has no Culvert Type or Open Bottom Substructure on
 * legacy's page: it hides both and sets the type to {@code BOX}, which its next save stores. So
 * here — the type is saved as {@code BOX} and the open bottom left as it is.
 */
@Service
public class StructureCulvertService {

  private static final Logger log = LoggerFactory.getLogger(StructureCulvertService.class);

  private static final String WOOD_LOG = "WLC";
  private static final String WOOD_LOG_TYPE = "BOX";
  private static final String OPEN_BOTTOM = "OPN";
  private static final String OTHER = "OTH";
  /** {@code CULVERT_NUMBER NUMBER(5)}. */
  private static final int NUMBER_MAX = 99_999;
  /** {@code CULVERT_LENGTH NUMBER(6,1)}. */
  private static final BigDecimal LENGTH_MAX = new BigDecimal("99999.9");
  /** {@code SLOPE NUMBER(4,1)}. */
  private static final BigDecimal GRADIENT_MAX = new BigDecimal("999.9");
  /** The depths and openings are {@code NUMBER(10)}, whole millimetres. */
  private static final long MILLIMETRES_MAX = 9_999_999_999L;
  private static final int TEXT_MAX = 2000;
  private static final String YES = "Y";
  private static final String NO = "N";

  private static final String IS_REQUIRED = " is required.";
  private static final String TYPE = "culvertTypeCode";
  private static final String OPEN_BOTTOM_FIELD = "openBottomCode";
  private static final String MATERIAL_COMMENT = "materialComment";

  private final CrossingStructureRepository structures;
  private final ForestServiceCulvertRepository culverts;
  private final EngineeredCulvertTypeCodeRepository culvertTypes;
  private final EngineeredClvrtMaterialCodeRepository materials;
  private final HeadwallLocationCodeRepository headwalls;
  private final OpnBtomClvrtSubstrctreCodeRepository openBottoms;
  private final LoggedUserHelper loggedUser;

  public StructureCulvertService(
      CrossingStructureRepository structures,
      ForestServiceCulvertRepository culverts,
      EngineeredCulvertTypeCodeRepository culvertTypes,
      EngineeredClvrtMaterialCodeRepository materials,
      HeadwallLocationCodeRepository headwalls,
      OpnBtomClvrtSubstrctreCodeRepository openBottoms,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.culverts = culverts;
    this.culvertTypes = culvertTypes;
    this.materials = materials;
    this.headwalls = headwalls;
    this.openBottoms = openBottoms;
    this.loggedUser = loggedUser;
  }

  /** The card's four lists, current codes only, by description. */
  @Transactional(readOnly = true)
  public CulvertCodesResponse codes() {
    return new CulvertCodesResponse(
        culvertTypes.findAllCurrent().stream()
            .map(code -> new CodeOptionResponse(
                code.getEngineeredCulvertTypeCode(), code.getDescription()))
            .toList(),
        materials.findAllCurrent().stream()
            .map(code -> new CodeOptionResponse(
                code.getEngineeredClvrtMaterialCode(), code.getDescription()))
            .toList(),
        headwalls.findAllCurrent().stream()
            .map(code -> new CodeOptionResponse(code.getHeadwallLocationCode(),
                code.getDescription()))
            .toList(),
        openBottoms.findAllCurrent().stream()
            .map(code -> new CodeOptionResponse(
                code.getOpnBtomClvrtSubstrctreCode(), code.getDescription()))
            .toList());
  }

  /**
   * Saves the Culvert card.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws SpanPierConflictException  if the structure has no culvert record
   * @throws FieldValidationException   with a message for each field at fault
   */
  @Transactional
  public void update(long structureId, CulvertDetailsRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    ForestServiceCulvertEntity culvert = culverts
        .findFirstByCrossingStructureIdOrderByForestServiceCulvertId(structureId)
        .orElseThrow(() -> new SpanPierConflictException("Structure " + structureId
            + " has no culvert record, so it has no culvert details."));
    boolean woodLog = WOOD_LOG.equals(structure.getStructureTypeClassCode());

    Map<String, String> errors = new LinkedHashMap<>();
    validate(errors, culvert, request, woodLog);
    String type = woodLog ? WOOD_LOG_TYPE : trimmed(request.culvertTypeCode());
    String openBottom = woodLog
        ? culvert.getOpnBtomClvrtSubstrctreCode()
        : trimmed(request.openBottomCode());
    String material = trimmed(request.materialCode());
    String materialComment = OTHER.equals(material) ? trimmed(request.materialComment()) : null;
    String headwall = trimmed(request.headwallLocationCode());
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Culvert details cannot be saved", errors);
    }

    String user = loggedUser.getLoggedUserId();
    structures.updatePortable(structureId, Boolean.TRUE.equals(request.portable()) ? YES : NO,
        user);
    culverts.save(culvert.toBuilder()
        .culvertNumber(request.culvertNumber())
        .culvertLength(request.lengthMetres())
        .slope(request.gradient())
        .engineeredCulvertTypeCode(type)
        .engineeredCulvertMaterialCode(material)
        .culvertMaterialComment(materialComment)
        .inletCoverDepth(request.inletCoverDepthMm())
        .outletCoverDepth(request.outletCoverDepthMm())
        .openingHeight(request.openingHeightMm())
        .openingWidth(request.openingWidthMm())
        .headwallLocationCode(headwall)
        .opnBtomClvrtSubstrctreCode(openBottom)
        .build());
    log.info("Updated the culvert details of structure {}", structureId);
  }

  /**
   * The Culvert card's rules, against what the culvert has stored — Add Structure passes an empty
   * one, so nothing is "required once it has a value". A Wood Log Culvert has no Culvert Type or
   * Open Bottom Substructure to check.
   */
  void validate(Map<String, String> errors, ForestServiceCulvertEntity stored,
      CulvertDetailsRequest request, boolean woodLog) {
    if (request.portable() == null) {
      errors.put("portable", "Portable Structure" + IS_REQUIRED);
    }
    checkMeasurements(errors, stored, request);
    if (!woodLog) {
      checkTypeAndOpenBottom(errors, stored, trimmed(request.culvertTypeCode()),
          trimmed(request.openBottomCode()));
    }
    String material = trimmed(request.materialCode());
    checkMaterial(errors, stored, material,
        OTHER.equals(material) ? trimmed(request.materialComment()) : null);
    checkCode(errors, "headwallLocationCode", "Headwall Location",
        trimmed(request.headwallLocationCode()), stored.getHeadwallLocationCode(), false,
        headwalls::existsById);
  }

  /** Culvert Number, the length and gradient, and the depths and openings. */
  private static void checkMeasurements(Map<String, String> errors,
      ForestServiceCulvertEntity culvert, CulvertDetailsRequest request) {
    Integer number = request.culvertNumber();
    if (number == null) {
      if (culvert.getCulvertNumber() != null) {
        errors.put("culvertNumber", "Culvert Number" + IS_REQUIRED);
      }
    } else if (number < 0 || number > NUMBER_MAX) {
      errors.put("culvertNumber", "Culvert Number must be a whole number from 0 to " + NUMBER_MAX
          + ".");
    }
    checkDecimal(errors, "lengthMetres", "Culvert Length", request.lengthMetres(),
        culvert.getCulvertLength() != null, BigDecimal.ZERO, LENGTH_MAX);
    checkDecimal(errors, "gradient", "Gradient", request.gradient(), false,
        GRADIENT_MAX.negate(), GRADIENT_MAX);
    checkMillimetres(errors, "inletCoverDepthMm", "Inlet Cover Depth",
        request.inletCoverDepthMm(), false);
    checkMillimetres(errors, "outletCoverDepthMm", "Outlet Cover Depth",
        request.outletCoverDepthMm(), false);
    checkMillimetres(errors, "openingHeightMm", "Opening Height", request.openingHeightMm(),
        culvert.getOpeningHeight() != null);
    checkMillimetres(errors, "openingWidthMm", "Opening Width", request.openingWidthMm(),
        culvert.getOpeningWidth() != null);
  }

  /** Culvert Type, and Open Bottom Substructure — required for an open-bottom culvert. */
  private void checkTypeAndOpenBottom(Map<String, String> errors,
      ForestServiceCulvertEntity culvert, String type, String openBottom) {
    checkCode(errors, TYPE, "Culvert Type", type, culvert.getEngineeredCulvertTypeCode(),
        true, culvertTypes::existsById);
    checkCode(errors, OPEN_BOTTOM_FIELD, "Open Bottom Substructure", openBottom,
        culvert.getOpnBtomClvrtSubstrctreCode(), false, openBottoms::existsById);
    if (OPEN_BOTTOM.equals(type) && openBottom == null) {
      errors.put(OPEN_BOTTOM_FIELD,
          "Open Bottom Substructure is required for an open-bottom culvert.");
    }
  }

  /** Culvert Material, and an Other's details: required with it, at most 2000 bytes. */
  private void checkMaterial(Map<String, String> errors, ForestServiceCulvertEntity culvert,
      String material, String materialComment) {
    checkCode(errors, "materialCode", "Culvert Material", material,
        culvert.getEngineeredCulvertMaterialCode(), true, materials::existsById);
    if (!OTHER.equals(material)) {
      return;
    }
    if (materialComment == null) {
      errors.put(MATERIAL_COMMENT, "Culvert Details" + IS_REQUIRED);
    } else if (materialComment.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX) {
      errors.put(MATERIAL_COMMENT, "Culvert Details can be at most " + TEXT_MAX
          + " characters.");
    }
  }

  /** A number with one decimal place, in range, required once the culvert has one. */
  private static void checkDecimal(Map<String, String> errors, String field, String label,
      BigDecimal value, boolean requiredOnceSet, BigDecimal min, BigDecimal max) {
    if (value == null) {
      if (requiredOnceSet) {
        errors.put(field, label + IS_REQUIRED);
      }
    } else if (value.stripTrailingZeros().scale() > 1) {
      errors.put(field, label + " can have at most 1 decimal place.");
    } else if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
      errors.put(field, label + " must be from " + min.toPlainString() + " to "
          + max.toPlainString() + ".");
    }
  }

  /** Whole millimetres the column holds, required once the culvert has one. */
  private static void checkMillimetres(Map<String, String> errors, String field, String label,
      Long value, boolean requiredOnceSet) {
    if (value == null) {
      if (requiredOnceSet) {
        errors.put(field, label + IS_REQUIRED);
      }
    } else if (value < 0 || value > MILLIMETRES_MAX) {
      errors.put(field, label + " must be a whole number of millimetres from 0 to "
          + MILLIMETRES_MAX + ".");
    }
  }

  /**
   * A code that exists — the culvert's own, since expired, may be kept — and, when
   * {@code requiredOnceSet}, required once the culvert has one.
   */
  private static void checkCode(Map<String, String> errors, String field, String label,
      String code, String stored, boolean requiredOnceSet, Predicate<String> exists) {
    if (code == null) {
      if (requiredOnceSet && trimmed(stored) != null) {
        errors.put(field, label + IS_REQUIRED);
      }
    } else if (!Objects.equals(code, stored) && !exists.test(code)) {
      errors.put(field, label + " is not one of the listed values.");
    }
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
