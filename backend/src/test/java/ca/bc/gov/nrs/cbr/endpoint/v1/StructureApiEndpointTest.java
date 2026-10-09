package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

  /** Each endpoint of one structure: its method, verb, path and gate. */
  static Stream<Arguments> structureEndpoints() {
    return Stream.of(
        Arguments.of("deleteStructure", "DELETE", "/{structureId}", CbrAuthorities.DESTRUCTIVE),
        Arguments.of("getStructure", "GET", "/{structureId}", CbrAuthorities.READ),
        Arguments.of("getSpansAndPiers", "GET", "/{structureId}/spans-and-piers",
            CbrAuthorities.READ),
        Arguments.of("getDocuments", "GET", "/{structureId}/documents", CbrAuthorities.READ),
        Arguments.of("getDocumentFile", "GET", "/{structureId}/documents/{fileId}/file",
            CbrAuthorities.READ),
        Arguments.of("getInspectionSchedule", "GET", "/{structureId}/inspection-schedule",
            CbrAuthorities.READ),
        Arguments.of("getInspections", "GET", "/{structureId}/inspections", CbrAuthorities.READ),
        Arguments.of("getRepairs", "GET", "/{structureId}/repairs", CbrAuthorities.READ),
        Arguments.of("getRepairTypes", "GET", "/{structureId}/repair-types",
            CbrAuthorities.READ),
        Arguments.of("createRepair", "POST", "/{structureId}/repairs",
            CbrAuthorities.CONTENT_EDIT),
        Arguments.of("updateRepair", "PUT", "/{structureId}/repairs/{repairId}",
            CbrAuthorities.CONTENT_EDIT),
        Arguments.of("deleteRepair", "DELETE", "/{structureId}/repairs/{repairId}",
            CbrAuthorities.DESTRUCTIVE),
        Arguments.of("getMonitors", "GET", "/{structureId}/monitors", CbrAuthorities.READ),
        Arguments.of("createMonitor", "POST", "/{structureId}/monitors",
            CbrAuthorities.CONTENT_EDIT),
        Arguments.of("updateMonitor", "PUT", "/{structureId}/monitors/{monitorId}",
            CbrAuthorities.CONTENT_EDIT),
        Arguments.of("deleteMonitor", "DELETE", "/{structureId}/monitors/{monitorId}",
            CbrAuthorities.DESTRUCTIVE));
  }

  @ParameterizedTest(name = "{0} is {1} {2}")
  @MethodSource("structureEndpoints")
  @DisplayName("each endpoint of one structure is mapped and gated: read on READ, add and edit on "
      + "CONTENT_EDIT (Level 1), delete on DESTRUCTIVE (Level 2)")
  void structureEndpointIsMappedAndGated(String name, String verb, String path, String gate) {
    Method endpoint = method(name);
    assertThat(mapping(endpoint, verb)).containsExactly(path);
    assertThat(endpoint.getAnnotation(PreAuthorize.class).value()).isEqualTo(gate);
  }

  /** The paths of the endpoint's mapping for the verb, or none when it is mapped to another. */
  private static String[] mapping(Method endpoint, String verb) {
    Annotation found = switch (verb) {
      case "GET" -> endpoint.getAnnotation(GetMapping.class);
      case "POST" -> endpoint.getAnnotation(PostMapping.class);
      case "PUT" -> endpoint.getAnnotation(PutMapping.class);
      case "DELETE" -> endpoint.getAnnotation(DeleteMapping.class);
      default -> throw new IllegalArgumentException(verb);
    };
    return switch (found) {
      case GetMapping get -> get.value();
      case PostMapping post -> post.value();
      case PutMapping put -> put.value();
      case DeleteMapping delete -> delete.value();
      case null, default -> new String[0];
    };
  }
}
