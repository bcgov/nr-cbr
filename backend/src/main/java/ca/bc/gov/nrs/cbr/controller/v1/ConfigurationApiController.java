package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ConfigurationApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.ConfigurationService;
import ca.bc.gov.nrs.cbr.service.v1.StructureCulvertService;
import ca.bc.gov.nrs.cbr.service.v1.StructureLoadRatingsService;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertCodesResponse;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingCodesResponse;
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
  private final StructureLoadRatingsService loadRatingsService;
  private final StructureCulvertService culvertService;

  public ConfigurationApiController(
      ConfigurationService configurationService,
      StructureLoadRatingsService loadRatingsService,
      StructureCulvertService culvertService) {
    this.configurationService = configurationService;
    this.loadRatingsService = loadRatingsService;
    this.culvertService = culvertService;
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
  public ResponseEntity<List<CodeOptionResponse>> getDeckTypeCodes() {
    return ResponseEntity.ok(configurationService.getDeckTypeCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getCulvertMaterialCodes() {
    return ResponseEntity.ok(configurationService.getCulvertMaterialCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getPortableSuperstructureStatusCodes() {
    return ResponseEntity.ok(configurationService.getPortableSuperstructureStatusCodes());
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
  public ResponseEntity<List<CodeOptionResponse>> getRepairClassCodes() {
    return ResponseEntity.ok(configurationService.getRepairClassCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getRepairGroupCodes() {
    return ResponseEntity.ok(configurationService.getRepairGroupCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getFileAttachmentTypeCodes() {
    return ResponseEntity.ok(configurationService.getFileAttachmentTypeCodes());
  }

  @Override
  public ResponseEntity<List<String>> getDocumentExtensions() {
    return ResponseEntity.ok(configurationService.getDocumentExtensions());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getPierTypeCodes() {
    return ResponseEntity.ok(configurationService.getPierTypeCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getBuiltByCodes() {
    return ResponseEntity.ok(configurationService.getBuiltByCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getStructureSourceCodes() {
    return ResponseEntity.ok(configurationService.getStructureSourceCodes());
  }

  @Override
  public ResponseEntity<BridgeCodesResponse> getBridgeCodes() {
    return ResponseEntity.ok(configurationService.getBridgeCodes());
  }

  @Override
  public ResponseEntity<LoadRatingCodesResponse> getLoadRatingCodes() {
    return ResponseEntity.ok(loadRatingsService.codes());
  }

  @Override
  public ResponseEntity<CulvertCodesResponse> getCulvertCodes() {
    return ResponseEntity.ok(culvertService.codes());
  }
}
