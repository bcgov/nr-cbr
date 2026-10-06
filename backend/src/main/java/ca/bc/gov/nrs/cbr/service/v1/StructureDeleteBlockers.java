package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What stops a structure being deleted.
 *
 * <p>Legacy refuses a structure with inspections, documents or photos, repairs or monitors — four
 * checks in {@code StructureSearchAction.delete}. Two more foreign keys reach the structure and
 * legacy checked neither, so a structure with one failed at the constraint instead of being
 * refused: a close-proximity inspection, and a replacement link on either side. All six are checked
 * here. Everything else that hangs off a structure is deleted with it — see
 * {@link StructureService#delete}.
 *
 * <p>Answered for many structures at once, one query per table, so a results page costs six
 * queries rather than six per row.
 */
@Component
public class StructureDeleteBlockers {

  /** The attribute that holds the structure's id on most of the blocking tables. */
  private static final String STRUCTURE_ID = "crossingStructureId";

  /** Each blocking table, the attribute holding its structure id, and how a message names it. */
  private record Source(Class<?> entity, String attribute, String label) {}

  private static final List<Source> SOURCES = List.of(
      new Source(StructureInspectionEntity.class, STRUCTURE_ID, "inspections"),
      new Source(CrossingStructureFileDetailEntity.class, STRUCTURE_ID, "documents or photos"),
      new Source(StructureRepairEntity.class, STRUCTURE_ID, "repairs"),
      new Source(StructureMonitorItemEntity.class, STRUCTURE_ID, "monitors"),
      new Source(CloseProximityInspectionEntity.class, STRUCTURE_ID,
          "close proximity inspections"),
      new Source(StructureReplacementXrefEntity.class, "replacedStructureNumber",
          "a replacement record"),
      new Source(StructureReplacementXrefEntity.class, "replacesStructureNumber",
          "a replacement record"));

  private final EntityManager entityManager;

  public StructureDeleteBlockers(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * What blocks each structure's delete, by structure id. A structure with nothing in the way maps
   * to an empty list; every id asked about is in the answer.
   *
   * <p>Ids beyond Oracle's thousand-item {@code IN} list are asked about in batches.
   */
  @Transactional(readOnly = true)
  public Map<Long, List<String>> of(Collection<Long> structureIds) {
    Map<Long, List<String>> blockers = new LinkedHashMap<>();
    structureIds.forEach(id -> blockers.put(id, new ArrayList<>()));
    List<Long> ids = List.copyOf(blockers.keySet());

    for (Source source : SOURCES) {
      for (int from = 0; from < ids.size(); from += StructureService.IN_LIST_LIMIT) {
        List<Long> batch =
            ids.subList(from, Math.min(from + StructureService.IN_LIST_LIMIT, ids.size()));
        for (Long id : holding(source, batch)) {
          List<String> labels = blockers.get(id);
          if (!labels.contains(source.label())) {
            labels.add(source.label());
          }
        }
      }
    }
    return blockers;
  }

  /**
   * The ones among {@code ids} that have at least one row in {@code source}.
   *
   * <p>Built with the criteria API rather than a JPQL string, so the entity and attribute names are
   * never text spliced into a query — both come from {@link #SOURCES}, but a string query would
   * leave that to be taken on trust by whoever reads it next.
   */
  private Set<Long> holding(Source source, List<Long> ids) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<?> row = query.from(source.entity());
    Path<Long> structureId = row.get(source.attribute());
    query.select(structureId).distinct(true).where(structureId.in(ids));
    return entityManager.createQuery(query).getResultStream().collect(Collectors.toSet());
  }
}
