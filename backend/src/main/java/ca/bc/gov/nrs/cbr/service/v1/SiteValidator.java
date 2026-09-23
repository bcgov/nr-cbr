package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.SiteValidationException;
import ca.bc.gov.nrs.cbr.repository.v1.CbrOrgUnitRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.SpecialAccessRequirementCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionStatusCodeRepository;
import ca.bc.gov.nrs.cbr.struct.v1.SiteCreateRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Everything the server checks before it will store a site.
 *
 * <p>Ported from legacy's two passes: {@code SiteForm.validate}, the only thing that can refuse
 * {@code addSite.do}, and {@code Site.validate}, which supplies the code-table and org-unit checks
 * the form pass leans on. <b>Warnings are not ported</b> — legacy's third tier (a coordinate outside
 * the province, a UTM zone British Columbia does not use) never refuses a save, so a server that
 * only ever answers yes or no has nothing to do with them. They live on the form, where they can be
 * shown in amber beside the box and ignored.
 *
 * <h2>Required, and legacy's disagreement with itself</h2>
 * The required set here is the eleven fields {@code site.jsp} marks with an asterisk, not the three
 * {@code SiteForm.validate} actually enforces on a create. Legacy's server-side checks are each
 * nested inside {@code if (site != null && …)} — the record as already stored — so on a create they
 * are skipped and a site can be saved with nothing but a number and a position. That is a
 * don't-regress rule rather than a required-field rule, and the screen has promised otherwise for
 * as long as it has existed. CBR enforces the promise. See cbr-site-form-rules.local.md §1.
 *
 * <h2>Where a rule is conditional</h2>
 * Three exemptions, all legacy's. A <b>storage site</b> holds portable structures rather than
 * spanning anything, so it has no crossing to name and no point on a road to measure to. A
 * <b>recreation site</b> is administered by a recreation district rather than a forest one, and has
 * no road — so no Forest District and no Br.
 */
@Component
public class SiteValidator {

  /** {@code CROSSING_SITE_ID} is {@code VARCHAR2(14 BYTE)}. */
  private static final int SITE_ID_MAX = 14;

  private static final int CROSSING_NAME_MAX = 255;
  private static final int POINT_OF_ACCESS_MAX = 255;
  private static final int FOREST_FILE_ID_MAX = 10;
  private static final int ROAD_SECTION_ID_MAX = 30;

  /** {@code NUMBER(8,2)} — six digits ahead of the point, two behind. */
  private static final int KILOMETRE_PRECISION = 8;

  private static final int KILOMETRE_SCALE = 2;

  private static final String CROSSING = "CRS";
  private static final String RECREATION = "REC";
  private static final String STORAGE = "STRG";

  private static final String INSPECT = "INS";
  private static final String DO_NOT_INSPECT = "DNI";

  /** Statuses under which a crossing is still standing and must therefore be inspected. */
  private static final Set<String> STANDING = Set.of("ACT", "BAR");

  /** Statuses under which a crossing must not be inspected. */
  private static final Set<String> NOT_STANDING =
      Set.of("TRN", "DAC", "UCON", "PP", "ARC", "LRM");

  private final CrossingSiteStatusCodeRepository siteStatusCodes;
  private final CrossingSiteTypeCodeRepository siteTypeCodes;
  private final StructureInspectionStatusCodeRepository inspectionStatusCodes;
  private final SpecialAccessRequirementCodeRepository specialAccessCodes;
  private final CbrOrgUnitRepository orgUnits;

  public SiteValidator(
      CrossingSiteStatusCodeRepository siteStatusCodes,
      CrossingSiteTypeCodeRepository siteTypeCodes,
      StructureInspectionStatusCodeRepository inspectionStatusCodes,
      SpecialAccessRequirementCodeRepository specialAccessCodes,
      CbrOrgUnitRepository orgUnits) {
    this.siteStatusCodes = siteStatusCodes;
    this.siteTypeCodes = siteTypeCodes;
    this.inspectionStatusCodes = inspectionStatusCodes;
    this.specialAccessCodes = specialAccessCodes;
    this.orgUnits = orgUnits;
  }

  /**
   * Checks a new site, throwing if anything is wrong with it.
   *
   * <p>Collects every problem rather than stopping at the first. A user who has to press Save six
   * times to be told six things has been told the same thing six times.
   *
   * @param siteIdTaken   whether a site already exists with this number — passed in rather than
   *                      looked up here so the validator owns no transaction and stays a pure
   *                      function of its inputs and the code tables
   * @param roadSegmentId the segment the road resolves to, or null when it resolves to none. Same
   *                      reasoning: the lookup belongs to the caller, the rule belongs here
   * @throws SiteValidationException if anything fails
   */
  public void validate(SiteCreateRequest request, boolean siteIdTaken, Long roadSegmentId) {
    Map<String, String> errors = new LinkedHashMap<>();
    String type = trimmed(request.crossingSiteTypeCode());

    checkSiteId(request, siteIdTaken, errors);
    checkRequired(request, type, errors);
    checkLengths(request, errors);
    checkNumbers(request, errors);
    checkCoordinates(request, errors);
    checkCodes(request, errors);
    checkOrgUnits(request, errors);
    checkInspectionAgreesWithStatus(request, type, errors);
    checkRoadResolves(request, type, roadSegmentId, errors);

    if (!errors.isEmpty()) {
      throw new SiteValidationException(errors);
    }
  }

  /**
   * The site number: present, short enough, and not already used.
   *
   * <p>The uniqueness check is legacy's {@code errors.site.exists}, and it is a validation failure
   * rather than a conflict because that is how the user experiences it — a number they chose and can
   * change, on a form they are still filling in. The primary key is the real guard and stays so;
   * this exists to say which box is wrong before thirty other fields are filled in.
   */
  private void checkSiteId(
      SiteCreateRequest request, boolean siteIdTaken, Map<String, String> errors) {
    String siteId = trimmed(request.siteId());
    if (siteId.isEmpty()) {
      errors.put("siteId", "Site # is required.");
      return;
    }
    if (siteId.length() > SITE_ID_MAX) {
      errors.put("siteId", "Site # can be at most " + SITE_ID_MAX + " characters.");
      return;
    }
    if (siteIdTaken) {
      errors.put("siteId", "A site with this number already exists.");
    }
  }

  /** The eleven fields the legacy screen marks mandatory, with its three type-based exemptions. */
  private void checkRequired(
      SiteCreateRequest request, String type, Map<String, String> errors) {
    requireText(errors, "crossingSiteStatusCode", request.crossingSiteStatusCode(), "Status");
    requireText(errors, "crossingSiteTypeCode", request.crossingSiteTypeCode(), "Site Type");
    requireText(
        errors,
        "structureInspectionStatusCode",
        request.structureInspectionStatusCode(),
        "Inspection Status");
    requireText(errors, "forestFileId", request.forestFileId(), "Project File ID#");

    // A storage site has no crossing to name and no point on a road to measure to.
    if (!STORAGE.equals(type)) {
      requireText(errors, "crossingName", request.crossingName(), "Crossing Name");
      if (request.pointOfCommencementDistance() == null) {
        errors.put("pointOfCommencementDistance", "Kilometres is required.");
      }
    }

    // A recreation site has no road, so neither a Br. nor a forest district.
    if (!RECREATION.equals(type)) {
      requireText(errors, "roadSectionId", request.roadSectionId(), "Br.");
      if (request.orgUnitNo() == null) {
        errors.put("orgUnitNo", "Forest District is required.");
      }
    }

    // Both coordinates, for every site type. Legacy says so in its own words — "// Making longitude
    // mandatory" above the check in SiteForm.validate, and the same again for latitude. The columns
    // are nullable, so this is an application rule rather than a constraint.
    if (request.longitude() == null) {
      errors.put("longitude", "Longitude is required.");
    }
    if (request.latitude() == null) {
      errors.put("latitude", "Latitude is required.");
    }
  }

  /** Free text that would not fit its column. */
  private void checkLengths(SiteCreateRequest request, Map<String, String> errors) {
    limit(errors, "crossingName", request.crossingName(), CROSSING_NAME_MAX, "Crossing Name");
    limit(
        errors,
        "pointOfAccessDescription",
        request.pointOfAccessDescription(),
        POINT_OF_ACCESS_MAX,
        "Site Details");
    limit(errors, "forestFileId", request.forestFileId(), FOREST_FILE_ID_MAX, "Project File ID#");
    limit(errors, "roadSectionId", request.roadSectionId(), ROAD_SECTION_ID_MAX, "Br.");
  }

  /**
   * The two kilometre values, which must fit {@code NUMBER(8,2)}.
   *
   * <p>Narrower than legacy, which parses with {@code new Double(...)} and lets Oracle decide.
   * A value Oracle cannot hold is {@code ORA-01438} at insert time — a stack trace where the user
   * deserves a sentence — and one it merely rounds is a number they did not type being stored
   * without being told.
   */
  private void checkNumbers(SiteCreateRequest request, Map<String, String> errors) {
    fitsKilometreColumn(
        errors, "pointOfCommencementDistance", request.pointOfCommencementDistance(), "Kilometres");
    fitsKilometreColumn(errors, "userKm", request.userKm(), "User Kilometres");
  }

  /**
   * The coordinates, as the notation bounds them.
   *
   * <p>Only the bounds legacy treats as errors. Its provincial-range checks — longitude 114 to 140,
   * latitude 48 to 60, and the three UTM ranges — are warnings in {@code Site.validate} and do not
   * refuse the save, so a crossing a kilometre over the Alberta border stores here as it does there.
   */
  private void checkCoordinates(SiteCreateRequest request, Map<String, String> errors) {
    BigDecimal longitude = request.longitude();
    if (longitude != null && longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
      errors.put("longitude", "Longitude must be between -180 and 180 degrees.");
    } else if (longitude != null && longitude.signum() > 0) {
      // Not a silent negation: a positive longitude is a real place, and guessing that the caller
      // meant its mirror image would store a site on the wrong side of the world without saying so.
      errors.put(
          "longitude", "Longitude must be negative — every site in British Columbia is west of "
              + "Greenwich.");
    }

    BigDecimal latitude = request.latitude();
    if (latitude != null && latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0) {
      errors.put("latitude", "Latitude must be between -90 and 90 degrees.");
    }
  }

  /** Every code must be one the matching table still carries. */
  private void checkCodes(SiteCreateRequest request, Map<String, String> errors) {
    codeExists(
        errors, "crossingSiteStatusCode", request.crossingSiteStatusCode(),
        siteStatusCodes::existsById, "Status");
    codeExists(
        errors, "crossingSiteTypeCode", request.crossingSiteTypeCode(),
        siteTypeCodes::existsById, "Site Type");
    codeExists(
        errors, "structureInspectionStatusCode", request.structureInspectionStatusCode(),
        inspectionStatusCodes::existsById, "Inspection Status");
    codeExists(
        errors, "specialAccessRqmtCode", request.specialAccessRqmtCode(),
        specialAccessCodes::existsById, "Special Access Requirements");
  }

  /**
   * The three org units, each of which must exist.
   *
   * <p>Legacy's {@code isValidOrgUnit}, which asks the same question of all three without caring
   * which branch of {@code CBR_ORG_UNIT} the number came from — so a Forest District number in the
   * Management Area box passes there and passes here. Tightening it would refuse rows legacy has
   * been writing for years, including the ones the road puts in the district field: a road supplies
   * a *region* number, which is not a district at all.
   */
  private void checkOrgUnits(SiteCreateRequest request, Map<String, String> errors) {
    orgUnitExists(errors, "orgUnitNo", request.orgUnitNo(), "Forest District");
    orgUnitExists(errors, "managementOrgUnitNo", request.managementOrgUnitNo(), "Management Area");
    orgUnitExists(
        errors, "businessAreaOrgUnitNo", request.businessAreaOrgUnitNo(), "BCTS Business Area");
  }

  /**
   * Whether a site gets inspected has to agree with what it is and what state it is in.
   *
   * <p>All three rules are legacy's, and all three land on Inspection Status — {@code
   * dto.setFieldName("structureInspectionStatusCode")} in {@code SiteAction.validate}, for each of
   * them. In every case it is the inspection setting that is wrong for the site rather than the site
   * that is wrong for the setting.
   */
  private void checkInspectionAgreesWithStatus(
      SiteCreateRequest request, String type, Map<String, String> errors) {
    String status = trimmed(request.crossingSiteStatusCode());
    String inspection = trimmed(request.structureInspectionStatusCode());

    if (CROSSING.equals(type) && STANDING.contains(status) && DO_NOT_INSPECT.equals(inspection)) {
      errors.put(
          "structureInspectionStatusCode",
          "An Active or Barricaded/Closed Crossing site must be set to Inspect.");
    } else if (CROSSING.equals(type)
        && NOT_STANDING.contains(status)
        && INSPECT.equals(inspection)) {
      errors.put(
          "structureInspectionStatusCode",
          "A Crossing site that is Transferred, Deactivated, Under Construction, Proposed, "
              + "Archived or LRMOPS must be set to Do Not Inspect.");
    } else if (STORAGE.equals(type) && INSPECT.equals(inspection)) {
      errors.put(
          "structureInspectionStatusCode", "A Storage site must be set to Do Not Inspect.");
    }
  }

  /**
   * The road named by Project File ID# and Br. must be a road that exists.
   *
   * <p>Legacy states this as a rule about the hidden Road Segment field — {@code Site.validate}
   * raises it when the segment is null while the two visible boxes are filled — but what it is
   * really testing is that the section the user named has at least one segment. The message lands
   * on Project File ID#, as legacy's does, because that is the box they can act on.
   *
   * <p>Not for a recreation site: its Project File ID# names a recreation project rather than a
   * road file, and legacy skips the whole block for {@code REC}.
   *
   * <p>Only when both boxes are filled. Either one empty has already been reported as required, and
   * saying "this road does not exist" about a road half named would be noise.
   */
  private void checkRoadResolves(
      SiteCreateRequest request, String type, Long roadSegmentId, Map<String, String> errors) {
    if (RECREATION.equals(type) || roadSegmentId != null) {
      return;
    }
    if (StringUtils.hasText(request.forestFileId())
        && StringUtils.hasText(request.roadSectionId())) {
      errors.putIfAbsent(
          "forestFileId", "No road matches this Project File ID# and Br.");
    }
  }

  private void requireText(
      Map<String, String> errors, String field, String value, String label) {
    if (!StringUtils.hasText(value)) {
      errors.put(field, label + " is required.");
    }
  }

  private void limit(
      Map<String, String> errors, String field, String value, int max, String label) {
    // Bytes, because the columns are declared in bytes and an accented character costs two.
    if (value != null && value.getBytes(StandardCharsets.UTF_8).length > max) {
      errors.putIfAbsent(field, label + " can be at most " + max + " characters.");
    }
  }

  private void fitsKilometreColumn(
      Map<String, String> errors, String field, BigDecimal value, String label) {
    if (value == null) {
      return;
    }
    if (value.signum() < 0) {
      errors.put(field, label + " cannot be negative.");
      return;
    }
    if (value.scale() > KILOMETRE_SCALE
        || value.precision() - value.scale() > KILOMETRE_PRECISION - KILOMETRE_SCALE) {
      errors.put(field, label + " must be a number with at most two decimal places, e.g. 12.5");
    }
  }

  /**
   * A code must exist, and is only looked up when there is one.
   *
   * <p>Takes the lookup rather than its result so a blank field costs no query: "required" has
   * already been said about it, and asking the database whether the empty string is a status adds a
   * round trip to say nothing.
   */
  private void codeExists(
      Map<String, String> errors,
      String field,
      String value,
      Predicate<String> exists,
      String label) {
    String code = trimmed(value);
    if (!code.isEmpty() && !exists.test(code)) {
      errors.putIfAbsent(field, label + " is not a recognized code.");
    }
  }

  private void orgUnitExists(
      Map<String, String> errors, String field, Long orgUnitNo, String label) {
    if (orgUnitNo != null && !orgUnits.existsById(orgUnitNo)) {
      errors.put(field, label + " is not a recognized org unit.");
    }
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }
}
