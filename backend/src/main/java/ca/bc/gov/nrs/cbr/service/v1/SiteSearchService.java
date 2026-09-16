package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.specification.v1.SiteSearchSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchResult;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Site Search.
 *
 * <p>Replaces {@code CBR.FIND_SITES_BY_CRITERIA} and {@code COUNT_SITES_BY_CRITERIA} with a JPA
 * {@link org.springframework.data.jpa.domain.Specification}. What each criterion means, and the
 * three places it diverges from legacy, are documented on
 * {@link SiteSearchSpecifications} — this class fetches, pages and maps, and holds no search rules
 * of its own.
 *
 * <p><b>Read-only transactions.</b> {@link CrossingSiteEntity} is deliberately not
 * {@code @Immutable} — CBR owns the table and will write to it — so the constraint that searching
 * never writes is stated here, where it is true, rather than on the entity, where it would have to
 * be undone by the first edit screen. It also spares Hibernate dirty-checking a page of results.
 *
 * <p><b>No row cap.</b> Legacy wraps its unpaginated query in {@code ROWNUM <= 200} — a backstop
 * for a screen whose non-paginated path could otherwise return the province. Paging is the answer
 * here instead, and a cap would silently truncate a legitimate result set: a user searching one
 * district for every site would be told there are 200.
 */
@Service
public class SiteSearchService {

  private static final Logger log = LoggerFactory.getLogger(SiteSearchService.class);

  /** Guards against a caller asking for the whole table in one page. */
  private static final int MAX_PAGE_SIZE = 200;

  private final CrossingSiteRepository crossingSiteRepository;

  public SiteSearchService(CrossingSiteRepository crossingSiteRepository) {
    this.crossingSiteRepository = crossingSiteRepository;
  }

  /**
   * Returns a page of matching sites, ordered as the legacy screen orders them.
   *
   * <p>Server-side paging because the legacy screen is: the result of an unfiltered search is every
   * site in the province, and CBR has no scoping that would narrow it.
   *
   * <p>The {@link Pageable} carries no {@link org.springframework.data.domain.Sort} on purpose. Two
   * of the four sort keys are on joined tables, and Spring Data resolves a sort path with an inner
   * join, which would drop every site missing that join — the ones "Incomplete Data?" exists to
   * find. The ordering is applied inside the specification, where the join type can be stated.
   *
   * @param criteria   the caller's filters; every field optional
   * @param pageNumber zero-based
   * @param pageSize   rows per page, capped at {@value #MAX_PAGE_SIZE}
   */
  @Transactional(readOnly = true)
  public PagedResponse<SiteSearchResult> search(
      SiteSearchCriteria criteria, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), boundedPageSize(pageSize));

    Page<CrossingSiteEntity> page =
        crossingSiteRepository.findAll(SiteSearchSpecifications.matching(criteria), pageable);

    log.debug("Site search matched {} site(s) (page {} of {}, criteriaEmpty={})",
        page.getTotalElements(), pageable.getPageNumber(), page.getTotalPages(), criteria.isEmpty());

    return new PagedResponse<>(
        page.getContent().stream().map(SiteSearchService::toResult).toList(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  private static int boundedPageSize(int requested) {
    if (requested < 1) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }

  /**
   * Maps one site to a results row.
   *
   * <p>Every joined value is optional, and a site missing its status, org unit or road section is a
   * normal result rather than an error — those are precisely what an "incomplete data" search
   * returns. Reading them through {@link #from} keeps a single missing join from failing the whole
   * page, which is how the legacy left joins behave.
   */
  private static SiteSearchResult toResult(CrossingSiteEntity site) {
    return new SiteSearchResult(
        site.getCrossingSiteId(),
        from(site.getOrgUnit(), OrgUnitEntity::getOrgUnitCode),
        from(site.getOrgUnit(), OrgUnitEntity::getOrgUnitName),
        from(site.getRoadSection(), CbrRoadSectionEntity::getRoadSectName),
        text(site.getPointOfCommencementDistance()),
        site.getCrossingName(),
        site.getForestFileId(),
        site.getRoadSectionId(),
        site.getCrossingSiteStatusCode(),
        from(site.getStatus(), CrossingSiteStatusCodeEntity::getDescription));
  }

  private static <T> String from(T association, Function<T, String> value) {
    return Optional.ofNullable(association).map(value).orElse(null);
  }

  /**
   * A NUMBER as text, keeping the column's own scale.
   *
   * <p>{@code toPlainString} rather than {@code toString} so 11.00 stays "11.00" instead of becoming
   * "1.1E+1" — the same reasoning as {@code AbstractCbrRepository.numberString}, which exists
   * because a single unreadable value once failed a whole read.
   */
  private static String text(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
