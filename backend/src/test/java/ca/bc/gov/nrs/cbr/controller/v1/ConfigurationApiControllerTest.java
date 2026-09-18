package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.service.v1.ConfigurationService;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Checks that each lookup reaches the matching service method.
 *
 * <p>Ten near-identical one-line delegations is exactly where a copy-paste slip lives — and because
 * every one of them returns a {@code List} of the same type, the wrong call compiles and, today,
 * even returns the same empty list. Giving each stub a distinguishable value is what makes the
 * mix-up visible.
 */
class ConfigurationApiControllerTest {

  private final ConfigurationService service = mock(ConfigurationService.class);
  private final ConfigurationApiController controller = new ConfigurationApiController(service);

  private static List<CodeOptionResponse> codes(String marker) {
    return List.of(new CodeOptionResponse(marker, marker));
  }

  private static List<OrgUnitResponse> orgUnits(String marker) {
    return List.of(new OrgUnitResponse(marker, marker, marker));
  }

  @Test
  @DisplayName("each code list comes from its own service method")
  void codeListsDelegate() {
    when(service.getSiteStatusCodes()).thenReturn(codes("status"));
    when(service.getStructureInspectionStatusCodes()).thenReturn(codes("inspection"));
    when(service.getSpecialAccessCodes()).thenReturn(codes("access"));
    when(service.getSiteTypeCodes()).thenReturn(codes("type"));
    when(service.getStructureTypeClassCodes()).thenReturn(codes("typeClass"));
    when(service.getInspectionTypeCodes()).thenReturn(codes("inspectionType"));
    when(service.getInspectionReportStatusCodes()).thenReturn(codes("reportStatus"));

    assertThat(controller.getSiteStatusCodes().getBody()).isEqualTo(codes("status"));
    assertThat(controller.getStructureInspectionStatusCodes().getBody())
        .isEqualTo(codes("inspection"));
    assertThat(controller.getSpecialAccessCodes().getBody()).isEqualTo(codes("access"));
    assertThat(controller.getSiteTypeCodes().getBody()).isEqualTo(codes("type"));
    assertThat(controller.getStructureTypeClassCodes().getBody()).isEqualTo(codes("typeClass"));
    assertThat(controller.getInspectionTypeCodes().getBody()).isEqualTo(codes("inspectionType"));
    assertThat(controller.getInspectionReportStatusCodes().getBody())
        .isEqualTo(codes("reportStatus"));
  }

  @Test
  @DisplayName("each org-unit list comes from its own service method")
  void orgUnitListsDelegate() {
    when(service.getForestDistricts()).thenReturn(orgUnits("district"));
    when(service.getBusinessAreas()).thenReturn(orgUnits("business"));
    when(service.getManagementAreas("1809")).thenReturn(orgUnits("area"));

    assertThat(controller.getForestDistricts().getBody()).isEqualTo(orgUnits("district"));
    assertThat(controller.getBusinessAreas().getBody()).isEqualTo(orgUnits("business"));
    assertThat(controller.getManagementAreas("1809").getBody()).isEqualTo(orgUnits("area"));
  }

  @Test
  @DisplayName("passes the selected district through rather than dropping it")
  void passesTheDistrictThrough() {
    // Dropping the argument would silently widen the lookup to every management area once the
    // query exists — the response would still be a valid list, just the wrong one.
    controller.getManagementAreas("1812");

    verify(service).getManagementAreas("1812");
  }

  @Test
  @DisplayName("answers 200, including for the empty lists the stub service returns today")
  void answersOk() {
    when(service.getSiteStatusCodes()).thenReturn(List.of());

    ResponseEntity<List<CodeOptionResponse>> response = controller.getSiteStatusCodes();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }
}
