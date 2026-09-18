package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
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
 * <p>The mappings and the authorization live here rather than on the controller, following nr-frep:
 * the interface is the contract, the controller is one way of satisfying it.
 *
 * <p>One endpoint per list rather than a single bundle. The lists are cached independently, change
 * on entirely different schedules, and only one of them — management areas — takes a parameter; a
 * bundle would have to re-fetch the other nine every time a district changed.
 *
 * <p><b>Every method is gated on {@link CbrAuthorities#READ}</b>, which nr-frep's equivalent does
 * not do — it leaves its lookups at "any authenticated caller". The stricter line is taken here
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
}
