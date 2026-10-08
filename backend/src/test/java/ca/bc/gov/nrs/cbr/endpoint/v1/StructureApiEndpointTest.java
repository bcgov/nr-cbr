package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** The structure endpoints' paths and gates — the contract a refactor must not change. */
class StructureApiEndpointTest {

  private static Method searchStructures() {
    return method("searchStructures");
  }

  private static Method method(String name) {
    return Arrays.stream(StructureApiEndpoint.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("search is GET /api/v1/structures/search")
  void searchIsMapped() {
    assertThat(StructureApiEndpoint.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/structures");
    assertThat(searchStructures().getAnnotation(GetMapping.class).value())
        .containsExactly("/search");
  }

  @Test
  @DisplayName("search is gated on READ — legacy's /showStructureSearch")
  void searchRequiresRead() {
    assertThat(searchStructures().getAnnotation(PreAuthorize.class).value())
        .isEqualTo(CbrAuthorities.READ);
  }

  @Test
  @DisplayName("search takes the criteria as one bound object, then the paging and the sort")
  void searchBindsCriteriaAsAnObject() {
    assertThat(searchStructures().getParameterTypes()).containsExactly(
        StructureSearchCriteria.class, int.class, int.class,
        StructureSortColumn.class, Sort.Direction.class);
  }

  @Test
  @DisplayName("archive is PUT /api/v1/structures/archive")
  void archiveIsMapped() {
    assertThat(method("archiveStructures").getAnnotation(PutMapping.class).value())
        .containsExactly("/archive");
  }

  @Test
  @DisplayName("archive is gated on DESTRUCTIVE — legacy's /archiveStructure, Level 2")
  void archiveRequiresDestructive() {
    assertThat(method("archiveStructures").getAnnotation(PreAuthorize.class).value())
        .isEqualTo(CbrAuthorities.DESTRUCTIVE);
  }

  @Test
  @DisplayName("archive validates its body, so an empty selection is a 400")
  void archiveValidatesItsBody() {
    Parameter body = method("archiveStructures").getParameters()[0];
    assertThat(body.getType()).isEqualTo(StructureArchiveRequest.class);
    assertThat(body.isAnnotationPresent(Valid.class)).isTrue();
    assertThat(body.isAnnotationPresent(RequestBody.class)).isTrue();
  }

  @Test
  @DisplayName("delete is DELETE /api/v1/structures/{structureId}, gated on DESTRUCTIVE")
  void deleteIsMappedAndGated() {
    Method delete = method("deleteStructure");
    assertThat(delete.getAnnotation(DeleteMapping.class).value())
        .containsExactly("/{structureId}");
    assertThat(delete.getAnnotation(PreAuthorize.class).value())
        .isEqualTo(CbrAuthorities.DESTRUCTIVE);
  }

  @Test
  @DisplayName("repair responsibility is PUT /repair-responsibility, gated on CONTENT_EDIT")
  void repairResponsibilityIsMappedAndGated() {
    Method update = method("updateRepairResponsibility");
    assertThat(update.getAnnotation(PutMapping.class).value())
        .containsExactly("/repair-responsibility");
    // Legacy's /updateRepairResponsibility: Level 1 and up, wider than Archive and Delete.
    assertThat(update.getAnnotation(PreAuthorize.class).value())
        .isEqualTo(CbrAuthorities.CONTENT_EDIT);
    assertThat(update.getParameters()[0].isAnnotationPresent(Valid.class)).isTrue();
  }

  @Test
  @DisplayName("one structure is GET /api/v1/structures/{structureId}, gated on READ")
  void getIsMappedAndGated() {
    Method get = method("getStructure");
    assertThat(get.getAnnotation(GetMapping.class).value()).containsExactly("/{structureId}");
    assertThat(get.getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.READ);
  }

  @Test
  @DisplayName("spans and piers are GET /api/v1/structures/{structureId}/spans-and-piers, on READ")
  void spansAndPiersAreMappedAndGated() {
    Method get = method("getSpansAndPiers");
    assertThat(get.getAnnotation(GetMapping.class).value())
        .containsExactly("/{structureId}/spans-and-piers");
    assertThat(get.getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.READ);
  }

  @Test
  @DisplayName("documents and their files are GETs under /{structureId}/documents, on READ")
  void documentsAreMappedAndGated() {
    Method list = method("getDocuments");
    assertThat(list.getAnnotation(GetMapping.class).value())
        .containsExactly("/{structureId}/documents");
    assertThat(list.getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.READ);

    Method file = method("getDocumentFile");
    assertThat(file.getAnnotation(GetMapping.class).value())
        .containsExactly("/{structureId}/documents/{fileId}/file");
    assertThat(file.getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.READ);
  }
  @Test
  @DisplayName("the Inspections tab's schedule and table are GETs, on READ")
  void inspectionsAreMappedAndGated() {
    Method schedule = method("getInspectionSchedule");
    assertThat(schedule.getAnnotation(GetMapping.class).value())
        .containsExactly("/{structureId}/inspection-schedule");
    assertThat(schedule.getAnnotation(PreAuthorize.class).value())
        .isEqualTo(CbrAuthorities.READ);

    Method table = method("getInspections");
    assertThat(table.getAnnotation(GetMapping.class).value())
        .containsExactly("/{structureId}/inspections");
    assertThat(table.getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.READ);
  }
  @Test
  @DisplayName("the Repairs tab's page is GET /{structureId}/repairs, on READ")
  void repairsAreMappedAndGated() {
    Method repairs = method("getRepairs");
    assertThat(repairs.getAnnotation(GetMapping.class).value())
        .containsExactly("/{structureId}/repairs");
    assertThat(repairs.getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.READ);
  }
  @Test
  @DisplayName("the Monitoring tab's page is GET /{structureId}/monitors, on READ")
  void monitorsAreMappedAndGated() {
    Method monitors = method("getMonitors");
    assertThat(monitors.getAnnotation(GetMapping.class).value())
        .containsExactly("/{structureId}/monitors");
    assertThat(monitors.getAnnotation(PreAuthorize.class).value())
        .isEqualTo(CbrAuthorities.READ);
  }
  @Test
  @DisplayName("a monitoring item is deleted by DELETE /{structureId}/monitors/{monitorId}, on "
      + "DESTRUCTIVE")
  void monitorDeleteIsMappedAndGated() {
    Method delete = method("deleteMonitor");
    assertThat(delete.getAnnotation(DeleteMapping.class).value())
        .containsExactly("/{structureId}/monitors/{monitorId}");
    assertThat(delete.getAnnotation(PreAuthorize.class).value())
        .isEqualTo(CbrAuthorities.DESTRUCTIVE);
  }
}
