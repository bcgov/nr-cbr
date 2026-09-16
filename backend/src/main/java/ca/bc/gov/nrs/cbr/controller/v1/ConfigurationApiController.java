package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ConfigurationApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.ConfigurationService;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference lookups used to populate UI dropdowns. Mappings and authorization are declared on
 * {@link ConfigurationApiEndpoint}; the legacy equivalents are itemized in
 * {@link ConfigurationService}.
 */
@RestController
public class ConfigurationApiController implements ConfigurationApiEndpoint {

  private final ConfigurationService configurationService;

  public ConfigurationApiController(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSiteStatusCodes() {
    return ResponseEntity.ok(configurationService.getSiteStatusCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getStructureInspectionStatusCodes() {
    return ResponseEntity.ok(configurationService.getStructureInspectionStatusCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSpecialAccessCodes() {
    return ResponseEntity.ok(configurationService.getSpecialAccessCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSiteTypeCodes() {
    return ResponseEntity.ok(configurationService.getSiteTypeCodes());
  }

  @Override
  public ResponseEntity<List<OrgUnitResponse>> getForestDistricts() {
    return ResponseEntity.ok(configurationService.getForestDistricts());
  }

  @Override
  public ResponseEntity<List<OrgUnitResponse>> getManagementAreas(String forestDistrictOrgUnitNo) {
    return ResponseEntity.ok(configurationService.getManagementAreas(forestDistrictOrgUnitNo));
  }
}
