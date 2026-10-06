package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.specification.v1.StructureSearchSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Structure Search.
 *
 * <p>Replaces {@code CBR_GENERAL.FIND_STRUCTURES_BY_CRITERIA} and its count. What each criterion
 * means is on {@link StructureSearchSpecifications}; this class fetches, pages and maps.
 *
 * <p><b>Paged on the server</b>, as Site and Inspection Search are, and with the same bound on a
 * page. Read-only transactions, for the reason {@link SiteSearchService} gives.
 */
@Service
public class StructureSearchService {

  private static final Logger log = LoggerFactory.getLogger(StructureSearchService.class);

  /** Guards against a caller asking for every structure in the province in one page. */
  private static final int MAX_PAGE_SIZE = 200;

  private static final int DEFAULT_PAGE_SIZE = 20;

  private final CrossingStructureRepository structures;
  private final ClientLocationRepository clients;
  private final StructureDeleteBlockers deleteBlockers;
  private final LoggedUserHelper loggedUser;

  public StructureSearchService(
      CrossingStructureRepository structures,
      ClientLocationRepository clients,
      StructureDeleteBlockers deleteBlockers,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.clients = clients;
    this.deleteBlockers = deleteBlockers;
    this.loggedUser = loggedUser;
  }

  /**
   * One page of matching structures, sorted by a results column first when one is given, then in
   * legacy's order — district, road, branch, kilometre.
   *
   * <p>The {@link Pageable} carries no sort: the keys are on left-joined tables, and Spring Data
   * would resolve them with inner joins, dropping the structures "Incomplete Data?" exists to find.
   * The specification orders instead — see Site Search, which does the same.
   *
   * @param pageNumber zero-based
   * @param pageSize   rows per page, capped at {@value #MAX_PAGE_SIZE}
   * @param sortBy     the results column the user sorted by, or null for legacy's order
   * @param direction  which way, when {@code sortBy} is set
   */
  @Transactional(readOnly = true)
  public PagedResponse<StructureSearchResult> search(
      StructureSearchCriteria criteria, int pageNumber, int pageSize,
      StructureSortColumn sortBy, Sort.Direction direction) {
    Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), boundedPageSize(pageSize));
    Page<CrossingStructureEntity> page = structures.findAll(
        StructureSearchSpecifications.matching(criteria, sortBy, direction), pageable);

    Map<String, String> names = maintainerNames(page);
    Map<Long, List<String>> blockers = blockersFor(page);

    log.debug("Structure search matched {} structure(s) (page {} of {})",
        page.getTotalElements(), pageable.getPageNumber(), page.getTotalPages());

    return new PagedResponse<>(
        page.getContent().stream()
            .map(structure -> toResult(structure, names, blockers))
            .toList(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  /**
   * The names of a page's maintainers, in one query.
   *
   * <p>Read here rather than joined: the search needs the client only to filter by name, which it
   * does by subquery, and a join for display would put Forest Client's view into every count too.
   * Legacy joins {@code FOREST_CLIENT}; CBR is granted {@code V_CLIENT_PUBLIC}, which carries the
   * name and withholds the rest.
   */
  private Map<String, String> maintainerNames(Page<CrossingStructureEntity> page) {
    var numbers = page.getContent().stream()
        .map(CrossingStructureEntity::getSite)
        .filter(Objects::nonNull)
        .map(CrossingSiteEntity::getClientNumber)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (numbers.isEmpty()) {
      return Map.of();
    }
    // First name wins: nothing constrains V_CLIENT_PUBLIC to one row per number, and a duplicate
    // there should not turn a search into a 500.
    return clients.findClientsByNumber(numbers).stream()
        .filter(client -> client.getClientName() != null)
        .collect(Collectors.toMap(
            ClientPublicEntity::getClientNumber, ClientPublicEntity::getClientName,
            (first, second) -> first));
  }

  /**
   * What blocks each structure's delete, for the screen to warn about before a delete is
   * confirmed. Six queries a page, so only for a caller who can delete; empty otherwise, which the
   * results carry as null rather than as "nothing in the way".
   */
  private Map<Long, List<String>> blockersFor(Page<CrossingStructureEntity> page) {
    if (!loggedUser.canDestroy() || page.isEmpty()) {
      return Map.of();
    }
    return deleteBlockers.of(
        page.getContent().stream().map(CrossingStructureEntity::getCrossingStructureId).toList());
  }

  private static StructureSearchResult toResult(
      CrossingStructureEntity structure,
      Map<String, String> names,
      Map<Long, List<String>> blockers) {
    CrossingSiteEntity site = structure.getSite();
    String clientNumber = from(site, CrossingSiteEntity::getClientNumber);
    return new StructureSearchResult(
        String.valueOf(structure.getCrossingStructureId()),
        structure.getCrossingStructureName(),
        from(site, CrossingSiteEntity::getCrossingSiteId),
        from(site, one -> from(one.getOrgUnit(), OrgUnitEntity::getOrgUnitCode)),
        from(site, one -> from(one.getOrgUnit(), OrgUnitEntity::getOrgUnitName)),
        from(site, one -> from(one.getRoadSection(), CbrRoadSectionEntity::getRoadSectName)),
        from(site, one -> text(one.getPointOfCommencementDistance())),
        from(site, CrossingSiteEntity::getCrossingName),
        from(structure.getTypeClass(), StructureTypeClassCodeEntity::getDescription),
        from(site, CrossingSiteEntity::getForestFileId),
        from(site, CrossingSiteEntity::getRoadSectionId),
        clientNumber,
        from(site, CrossingSiteEntity::getClientLocnCode),
        clientNumber == null ? null : names.get(clientNumber),
        blockers.get(structure.getCrossingStructureId()));
  }

  private static <T> String from(T association, Function<T, String> value) {
    return Optional.ofNullable(association).map(value).orElse(null);
  }

  /** A NUMBER as text at its column's scale — see {@code SiteSearchService.text}. */
  private static String text(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  private static int boundedPageSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
