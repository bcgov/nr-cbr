package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.service.v1.InspectionReportMaps;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsBlankService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedDetailService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsDueDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsDueSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.MonitorsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailReportService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class ReportDistrictsTest {

  private static final String EIGHT = "1,2,3,4,5,6,7,8";
  private static final String NINE = EIGHT + ",9";

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", ",", " , "})
  @DisplayName("refuses a run with no Area and no Forest District")
  void refusesNoAreaOrDistrict(String orgUnit) {
    assertThatThrownBy(() -> ReportDistricts.require(null, orgUnit))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors()).isEqualTo(
                Map.of("orgUnit", "Select an Area or at least one Forest District.")));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("a blank Area is no Area")
  void refusesABlankArea(String area) {
    assertThatThrownBy(() -> ReportDistricts.require(area, null))
        .isInstanceOf(FieldValidationException.class);
  }

  @Test
  @DisplayName("runs with an Area alone, or an Area and districts in it")
  void acceptsAnArea() {
    assertThatCode(() -> ReportDistricts.require("1833", null)).doesNotThrowAnyException();
    assertThatCode(() -> ReportDistricts.require("1833", "18")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("refuses more than eight Forest Districts")
  void refusesMoreThanEight() {
    // An Area does not lift the limit on districts.
    assertThatThrownBy(() -> ReportDistricts.require("1833", NINE))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors())
                .isEqualTo(Map.of("orgUnit", "Select no more than 8 Forest Districts.")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1832", EIGHT, EIGHT + ",8,1"})
  @DisplayName("runs with one to eight Forest Districts — a district picked twice counts once")
  void acceptsOneToEight(String orgUnit) {
    assertThatCode(() -> ReportDistricts.require(null, orgUnit)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(ReportFormat.class)
  @DisplayName("Inspections Conducted – Detail is refused before it is made, in every format")
  void conductedDetailIsRefused(ReportFormat format) {
    InspectionsConductedDetailService detail = mock(InspectionsConductedDetailService.class);
    ReportApiController controller = new ReportApiController(
        mock(StructureInventorySummaryService.class), mock(StructureInventoryDetailedService.class),
        mock(StructureDetailReportService.class), mock(RepairsSummaryService.class),
        mock(MonitorsSummaryService.class), mock(InspectionsConductedSummaryService.class), detail);

    InspectionsConductedCriteria criteria =
        InspectionsConductedCriteria.builder().orgUnit(NINE).build();
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> controller.inspectionsConductedDetail(criteria, format, response))
        .isInstanceOf(FieldValidationException.class);
    verifyNoInteractions(detail);
  }

  @Test
  @DisplayName("Inspections Conducted – Detail's KMZ is refused too")
  void conductedDetailKmzIsRefused() {
    InspectionReportMaps maps = mock(InspectionReportMaps.class);
    ReportMapApiController controller =
        new ReportMapApiController(mock(StructureInventoryDetailedService.class), maps);

    InspectionsConductedCriteria criteria = InspectionsConductedCriteria.builder().build();

    assertThatThrownBy(() -> controller.inspectionsConductedDetailKmz(criteria))
        .isInstanceOf(FieldValidationException.class);
    verifyNoInteractions(maps);
  }

  @ParameterizedTest
  @EnumSource(ReportFormat.class)
  @DisplayName("Inspections – Blank Report is refused before it is made, in every format")
  void blankIsRefused(ReportFormat format) {
    InspectionsBlankService blank = mock(InspectionsBlankService.class);
    InspectionsDueReportApiController controller = new InspectionsDueReportApiController(
        mock(InspectionsDueSummaryService.class), mock(InspectionsDueDetailedService.class), blank);

    InspectionsDueCriteria criteria = InspectionsDueCriteria.builder().build();
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> controller.inspectionsBlank(criteria, format, response))
        .isInstanceOf(FieldValidationException.class);
    verifyNoInteractions(blank);
  }

  @Test
  @DisplayName("the other reports still run without a Forest District")
  void otherReportsDoNot() {
    InspectionReportMaps maps = mock(InspectionReportMaps.class);
    InspectionsConductedCriteria none = InspectionsConductedCriteria.builder().build();
    when(maps.inspectionsConductedSummary(none)).thenReturn(new ReportFile(
        new byte[] {80, 75}, "InspectionsConductedSummaryReport_2026-11-27.kmz",
        MediaType.parseMediaType("application/vnd.google-earth.kmz")));
    ReportMapApiController controller =
        new ReportMapApiController(mock(StructureInventoryDetailedService.class), maps);

    assertThat(controller.inspectionsConductedSummaryKmz(none).getBody()).isNotEmpty();
  }
}
