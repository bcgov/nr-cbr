package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.repository.v1.CbrRoadSectionRepository;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the road search does when it cannot read {@code FOREST_FILE_CLIENT}.
 *
 * <p>Mocked rather than run against a database: the condition being handled is a missing grant,
 * which H2 has no way to reproduce — every table the tests create is readable.
 */
@ExtendWith(MockitoExtension.class)
class RoadSectionServiceFallbackTest {

  private static final RoadSearchResult BOWRON =
      new RoadSearchResult("Bowron FSR", "R00123", "01", "B40", null, null);

  @Mock
  private CbrRoadSectionRepository roadSections;

  @InjectMocks
  private RoadSectionService service;

  private void givenTheGrantIsMissing() {
    when(roadSections.search(
            nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(String.class), nullable(String.class), nullable(String.class),
            any(Pageable.class)))
        .thenThrow(new InvalidDataAccessResourceUsageException("ORA-00942"));
  }

  private void givenTheFallbackAnswers() {
    when(roadSections.searchWithoutClient(
            nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(String.class), any(Pageable.class)))
        .thenReturn(List.of(BOWRON));
  }

  @Test
  @DisplayName("answers the roads anyway, without their tenure holder")
  void fallsBack() {
    givenTheGrantIsMissing();
    givenTheFallbackAnswers();

    assertThat(service.search(RoadSearchCriteria.builder().build())).containsExactly(BOWRON);
  }

  @Test
  @DisplayName("stops asking once it knows, rather than failing again on every search")
  void remembersTheAnswer() {
    givenTheGrantIsMissing();
    givenTheFallbackAnswers();

    service.search(RoadSearchCriteria.builder().build());
    service.search(RoadSearchCriteria.builder().forestFileId("R00123").build());

    verify(roadSections, times(1)).search(
        nullable(String.class), nullable(String.class), nullable(String.class),
        nullable(String.class), nullable(String.class), nullable(String.class),
        any(Pageable.class));
    verify(roadSections, times(2)).searchWithoutClient(
        nullable(String.class), nullable(String.class), nullable(String.class),
        nullable(String.class), any(Pageable.class));
  }

  @Test
  @DisplayName("asks the full query while the grant is there, and never the fallback")
  void prefersTheFullQuery() {
    when(roadSections.search(
            nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(String.class), nullable(String.class), nullable(String.class),
            any(Pageable.class)))
        .thenReturn(List.of(BOWRON));

    service.search(RoadSearchCriteria.builder().build());
    service.search(RoadSearchCriteria.builder().build());

    verify(roadSections, never()).searchWithoutClient(
        nullable(String.class), nullable(String.class), nullable(String.class),
        nullable(String.class), any(Pageable.class));
  }

  @Test
  @DisplayName("runs outside a transaction, which is what lets it recover at all")
  void isNotTransactional() throws NoSuchMethodException {
    // A failed statement marks its transaction rollback-only, so catching the failure inside one
    // and carrying on ends in UnexpectedRollbackException at commit — a less legible error than
    // the one being handled. Asserted because `@Transactional` on a service method is the norm in
    // this package and would be entirely reasonable to add back.
    assertThat(
            AnnotatedElementUtils.findMergedAnnotation(
                RoadSectionService.class.getMethod("search", RoadSearchCriteria.class),
                Transactional.class))
        .isNull();
  }
}
