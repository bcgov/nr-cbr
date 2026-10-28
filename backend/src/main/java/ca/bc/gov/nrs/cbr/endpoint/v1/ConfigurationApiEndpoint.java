package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionReviewerResponse;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RecreationProjectResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the reference lookups that populate the UI's dropdowns. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.ConfigurationApiController}.
 *
 * <p>The mappings and the authorization live here rather than on the controller:
 * the interface is the contract, the controller is one way of satisfying it.
 *
 * <p>One endpoint per list rather than a single bundle. The lists are cached independently, change
 * on entirely different schedules, and only one of them — management areas — takes a parameter; a
 * bundle would have to re-fetch the other nine every time a district changed.
 *
 * <p><b>Every method is gated on {@link CbrAuthorities#READ}</b> rather than left at "any
 * authenticated caller". The stricter line is taken
 * because these lists exist to fill in the Site Search and Inspection Search forms, and both are
 * READ-gated: a token holder with no CBR role has nothing to do with them. Read is the weakest
 * capability CBR has, so this excludes only callers who could not use the answer.
 */
@RequestMapping("/api/v1/configuration")
public interface ConfigurationApiEndpoint {

  /** Site statuses — the Status select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/site-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getSiteStatusCodes();

  /** Structure inspection statuses — the Inspection Status select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-inspection-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getStructureInspectionStatusCodes();

  /** Special access requirements — the Special Access Requirements select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/special-access-codes")
  ResponseEntity<List<CodeOptionResponse>> getSpecialAccessCodes();

  /** Site types — the Site Type select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/site-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getSiteTypeCodes();

  /** Structure types and classes — the Type/Class select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-type-class-codes")
  ResponseEntity<List<CodeOptionResponse>> getStructureTypeClassCodes();

  /** Inspection types — the Inspection Type select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspection-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getInspectionTypeCodes();

  /** Inspection report statuses — the Inspection Report Status select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspection-report-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getInspectionReportStatusCodes();

  /** Forest districts — the Forest District select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/forest-districts")
  ResponseEntity<List<OrgUnitResponse>> getForestDistricts();

  /** BCTS business areas — the BCTS Business Area select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/business-areas")
  ResponseEntity<List<OrgUnitResponse>> getBusinessAreas();

  /**
   * Inspection reviewers, from CSS — the Reviewed By select. Empty when CSS is not configured or
   * cannot be reached.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspection-reviewers")
  ResponseEntity<List<InspectionReviewerResponse>> getInspectionReviewers();

  /**
   * The name of the recreation project a file id names — "Project Name" on the site form.
   *
   * <p>What a recreation site shows where a crossing shows its Forest Service Road, and the reason
   * the two are never on screen together.
   *
   * <p>200 with a null name when the file is unknown, not a 404: a recreation file id is typed by
   * hand, so a half-typed one is the ordinary state of this field rather than a failure. Legacy's
   * DAO answers the same way, returning {@code ""} from an empty result set.
   *
   * @param forestFileId "Project File ID#" on the site form
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/recreation-project-name")
  ResponseEntity<RecreationProjectResponse> getRecreationProjectName(
      @RequestParam(name = "forestFileId", defaultValue = "") String forestFileId);

  /**
   * The recreation districts a project file belongs to — the Recreation District list.
   *
   * <p>A recreation site is administered by one of these rather than by a forest district, and the
   * file decides which are on offer. That makes this the one org-unit list keyed on something
   * other than another org unit.
   *
   * <p>Empty until a Project File ID# is given, as the management areas are empty until a district
   * is: an unfiltered list would be every recreation district in the province, none of which the
   * file may belong to.
   *
   * @param forestFileId "Project File ID#" on the site form
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/recreation-districts")
  ResponseEntity<List<OrgUnitResponse>> getRecreationDistricts(
      @RequestParam(name = "forestFileId", defaultValue = "") String forestFileId);

  /**
   * Management areas within one forest district — the Management Area select, which is repopulated
   * whenever Forest District changes and is empty until a district is chosen.
   *
   * @param forestDistrictOrgUnitNo the selected district's {@code ORG_UNIT_NO}; required, because
   *                                the screen never asks for every management area at once
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/management-areas")
  ResponseEntity<List<OrgUnitResponse>> getManagementAreas(
      @RequestParam(name = "forestDistrictOrgUnitNo") String forestDistrictOrgUnitNo);

  /** Superstructure types — the Superstructure Type select on Structure Search. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/superstructure-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getSuperstructureTypeCodes();

  /** Bridge curb types — the Curb Type select on Structure Search. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-curb-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getStructureCurbTypeCodes();

  /** Culvert types — the Culvert Type select on Structure Search. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/culvert-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getCulvertTypeCodes();

  /** Deck types, expired ones included — the Deck Type filter on Structure Inventory – Summary. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/deck-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getDeckTypeCodes();

  /**
   * Culvert materials, expired ones included — the Culvert Material filter on Structure Inventory –
   * Summary.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/culvert-material-codes")
  ResponseEntity<List<CodeOptionResponse>> getCulvertMaterialCodes();

  /** Portable superstructure statuses — the filter on the Structure Inventory reports. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/portable-superstructure-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getPortableSuperstructureStatusCodes();

  /** Special equipment requirements — the Special Equipment Requirements select. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/special-equipment-codes")
  ResponseEntity<List<CodeOptionResponse>> getSpecialEquipmentCodes();

  /** Monitoring statuses — the Monitoring Status select on a monitoring item. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/monitoring-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getMonitoringStatusCodes();

  /** Monitoring frequencies — the Monitoring Frequency select on a monitoring item. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/monitor-frequency-codes")
  ResponseEntity<List<CodeOptionResponse>> getMonitorFrequencyCodes();

  /** Repair statuses — the Repair Status select on a repair. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/repair-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getRepairStatusCodes();

  /** Repair priorities — the Repair Priority select on a repair. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/repair-priority-codes")
  ResponseEntity<List<CodeOptionResponse>> getRepairPriorityCodes();

  /** Repair classes — the Structural Safety filter on the Repairs Summary report. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/repair-class-codes")
  ResponseEntity<List<CodeOptionResponse>> getRepairClassCodes();

  /** Repair groups — the checkboxes that narrow a repair's Repair Type list. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/repair-group-codes")
  ResponseEntity<List<CodeOptionResponse>> getRepairGroupCodes();

  /** Document types — the "Photo View" select on a document or photo. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/file-attachment-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getFileAttachmentTypeCodes();

  /** The file extensions a document may have, upper case — the upload dialog's early check. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/document-extensions")
  ResponseEntity<List<String>> getDocumentExtensions();

  /** Pier types — the Type select on a pier. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/pier-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getPierTypeCodes();

  /** Built For — the select on the Details tab's Structure card. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/built-by-codes")
  ResponseEntity<List<CodeOptionResponse>> getBuiltByCodes();

  /** Structure sources — the Source select on the Details tab's Structure card. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-source-codes")
  ResponseEntity<List<CodeOptionResponse>> getStructureSourceCodes();

  /** The Bridge card's five lists, current codes only. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/bridge-codes")
  ResponseEntity<BridgeCodesResponse> getBridgeCodes();

  /** Load Rating Details' lists: posting signs, design vehicles with their ratings, reasons. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/load-rating-codes")
  ResponseEntity<LoadRatingCodesResponse> getLoadRatingCodes();

  /** The Culvert card's lists: culvert type, material, headwall location, open bottom. */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/culvert-codes")
  ResponseEntity<CulvertCodesResponse> getCulvertCodes();
}
