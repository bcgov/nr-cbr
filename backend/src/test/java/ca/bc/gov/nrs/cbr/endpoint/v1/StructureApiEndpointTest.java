package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The structure endpoints' paths and gates — the contract a refactor must not change. */
class StructureApiEndpointTest {

  private static Method searchStructures() {
    return Arrays.stream(StructureApiEndpoint.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals("searchStructures"))
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
}
