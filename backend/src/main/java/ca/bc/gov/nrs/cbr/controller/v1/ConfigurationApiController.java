package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ConfigurationApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.ConfigurationService;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RecreationProjectResponse;
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
  public ResponseEntity<List<CodeOptionResponse>> getStructureTypeClassCodes() {
    return ResponseEntity.ok(configurationService.getStructureTypeClassCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getInspectionTypeCodes() {
    return ResponseEntity.ok(configurationService.getInspectionTypeCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getInspectionReportStatusCodes() {
    return ResponseEntity.ok(configurationService.getInspectionReportStatusCodes());
  }

  @Override
  public ResponseEntity<List<OrgUnitResponse>> getBusinessAreas() {
    return ResponseEntity.ok(configurationService.getBusinessAreas());
  }

  @Override
  public ResponseEntity<List<OrgUnitResponse>> getForestDistricts() {
    return ResponseEntity.ok(configurationService.getForestDistricts());
  }

  @Override
  public ResponseEntity<RecreationProjectResponse> getRecreationProjectName(String forestFileId) {
    return ResponseEntity.ok(configurationService.getRecreationProjectName(forestFileId));
  }

  @Override
  public ResponseEntity<List<OrgUnitResponse>> getRecreationDistricts(String forestFileId) {
    return ResponseEntity.ok(configurationService.getRecreationDistricts(forestFileId));
  }

  @Override
  public ResponseEntity<List<OrgUnitResponse>> getManagementAreas(String forestDistrictOrgUnitNo) {
    return ResponseEntity.ok(configurationService.getManagementAreas(forestDistrictOrgUnitNo));
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSuperstructureTypeCodes() {
    return ResponseEntity.ok(configurationService.getSuperstructureTypeCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getStructureCurbTypeCodes() {
    return ResponseEntity.ok(configurationService.getStructureCurbTypeCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getCulvertTypeCodes() {
    return ResponseEntity.ok(configurationService.getCulvertTypeCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSpecialEquipmentCodes() {
    return ResponseEntity.ok(configurationService.getSpecialEquipmentCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getMonitoringStatusCodes() {
    return ResponseEntity.ok(configurationService.getMonitoringStatusCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getMonitorFrequencyCodes() {
    return ResponseEntity.ok(configurationService.getMonitorFrequencyCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getRepairStatusCodes() {
    return ResponseEntity.ok(configurationService.getRepairStatusCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getRepairPriorityCodes() {
    return ResponseEntity.ok(configurationService.getRepairPriorityCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getRepairGroupCodes() {
    return ResponseEntity.ok(configurationService.getRepairGroupCodes());
  }
}
