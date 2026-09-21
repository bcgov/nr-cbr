package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.specification.v1.InspectionSearchSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Inspection Search.
 *
 * <p>Replaces {@code CBR.FIND_INSPECTIONS_BY_CRITERIA} and {@code COUNT_INSPECTIONS_BY_CRITERIA}
 * with a JPA {@link org.springframework.data.jpa.domain.Specification}. What each criterion means,
 * and the four places it diverges from legacy, are documented on
 * {@link InspectionSearchSpecifications} — this class fetches, pages and maps, and holds no search
 * rules of its own beyond the empty-criteria refusal below.
 *
 * <p><b>Read-only transactions.</b> Neither {@link StructureInspectionEntity} nor
 * {@link InspectionReportStatusEntity} is {@code @Immutable} — CBR owns both tables and writes to
 * them — so the constraint that searching never writes is stated here, where it is true, rather than
 * on the entities, where the first edit screen would have to undo it.
 */
@Service
public class InspectionSearchService {

  private static final Logger log = LoggerFactory.getLogger(InspectionSearchService.class);

  /** Guards against a caller asking for the whole table in one page. */
  private static final int MAX_PAGE_SIZE = 200;

  private final StructureInspectionRepository structureInspectionRepository;

  public InspectionSearchService(StructureInspectionRepository structureInspectionRepository) {
    this.structureInspectionRepository = structureInspectionRepository;
  }

  /**
   * Returns a page of matching inspections, ordered as the form asked.
   *
   * <p><b>An empty criteria object is answered, not refused.</b> Legacy refuses it — every one of
   * its five search forms raises {@code errors.search.select} — but that guard exists because the
   * legacy query was unpaginated: "no criteria" meant building a result set of every inspection in
   * the province. Paging server-side removes the reason for it, and {@link SiteSearchService} made
   * the same call. Two search screens in one application cannot disagree about this.
   *
   * <p>The {@link Pageable} carries no {@link org.springframework.data.domain.Sort} on purpose —
   * every sort key is on a joined table. See
   * {@code InspectionSearchSpecifications.fetchAndOrder}.
   *
   * @param criteria   the caller's filters; every field optional
   * @param pageNumber zero-based
   * @param pageSize   rows per page, capped at {@value #MAX_PAGE_SIZE}
   */
  @Transactional(readOnly = true)
  public PagedResponse<InspectionSearchResult> search(
      InspectionSearchCriteria criteria, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), boundedPageSize(pageSize));

    Page<StructureInspectionEntity> results = structureInspectionRepository.findAll(
        InspectionSearchSpecifications.matching(criteria), pageable);

    log.debug("Inspection search matched {} inspection(s) (page {} of {}, criteriaEmpty={})",
        results.getTotalElements(), results.getNumber(), results.getTotalPages(),
        criteria.isEmpty());

    return new PagedResponse<>(
        results.getContent().stream().map(InspectionSearchService::toResult).toList(),
        results.getTotalElements(),
        results.getTotalPages(),
        results.getNumber(),
        results.getSize());
  }

  private static int boundedPageSize(int requested) {
    if (requested < 1) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }

  /**
   * Maps one inspection to a results row.
   *
   * <p>The structure, its site and the current status are all reached through inner joins, so they
   * are present on every row the query returns. Everything further out — the road section, the org
   * unit, the status's code-table row — is optional, and a missing one is a blank column rather than
   * a failed page. Reading them through {@link #from} is what keeps that true.
   */
  private static InspectionSearchResult toResult(StructureInspectionEntity inspection) {
    CrossingStructureEntity structure = inspection.getStructure();
    CrossingSiteEntity site = structure == null ? null : structure.getSite();
    InspectionReportStatusEntity status = inspection.getCurrentStatus();

    return new InspectionSearchResult(
        String.valueOf(inspection.getInspectionId()),
        text(inspection.getInspectionDate()),
        from(status, InspectionReportStatusEntity::getInspectionReportStatusCode),
        from(status, InspectionSearchService::describe),
        inspection.getSiteAtTimeOfInspection(),
        from(structure, CrossingStructureEntity::getCrossingStructureName),
        from(site, one -> from(one.getOrgUnit(), OrgUnitEntity::getOrgUnitCode)),
        from(site, one -> from(one.getOrgUnit(), OrgUnitEntity::getOrgUnitName)),
        from(site, one -> from(one.getRoadSection(), CbrRoadSectionEntity::getRoadSectName)),
        from(site, one -> text(one.getPointOfCommencementDistance())),
        from(site, CrossingSiteEntity::getCrossingName),
        from(site, CrossingSiteEntity::getForestFileId),
        from(site, CrossingSiteEntity::getRoadSectionId));
  }

  private static String describe(InspectionReportStatusEntity status) {
    return from(status.getStatusCode(), InspectionReportStatusCodeEntity::getDescription);
  }

  private static <T> String from(T association, Function<T, String> value) {
    return Optional.ofNullable(association).map(value).orElse(null);
  }

  /** ISO {@code yyyy-MM-dd}; the table prints it as {@code yyyy/MM/dd}, as legacy's tag does. */
  private static String text(LocalDate value) {
    return value == null ? null : value.toString();
  }

  /**
   * A NUMBER as text, keeping the column's own scale.
   *
   * <p>{@code toPlainString} rather than {@code toString} so 11.00 stays "11.00" instead of becoming
   * "1.1E+1" — the same reasoning as {@code SiteSearchService.text}.
   */
  private static String text(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
