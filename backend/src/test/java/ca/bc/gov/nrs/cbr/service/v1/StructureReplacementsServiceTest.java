package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.ReplacementNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureReplacementXrefRepository;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementRequest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** The Details tab's Replacement History, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureReplacementsService.class)
class StructureReplacementsServiceTest {

  @Autowired
  private StructureReplacementsService service;

  @Autowired
  private StructureReplacementXrefRepository links;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("StructureReplacementXrefEntity", "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    givenStructure(7L, "Y");
    givenStructure(8L, "Y");
    givenStructure(9L, "N");
  }

  private void givenStructure(long id, String active) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName("B" + id).structureTypeClassCode("TB")
        .activeInd(active).closeProximityInd("N").portableStructureInd("N")
        .asBuiltInfoPresentInd("N").build());
  }

  /** Every link, as "replacing>replaced". */
  private List<String> saved() {
    entityManager.flush();
    entityManager.clear();
    return links.findAll().stream()
        .map(link -> link.getReplacesStructureNumber() + ">" + link.getReplacedStructureNumber())
        .sorted().toList();
  }

  private Map<String, String> refused(Runnable save) {
    try {
      save.run();
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the replacement was saved");
  }

  @Test
  @DisplayName("records that this structure replaced another, by Structure #, and deletes it")
  void addsAndDeletes() {
    service.add(7L, new ReplacementRequest(" B8 "));
    service.add(7L, new ReplacementRequest("B9"));
    assertThat(saved()).containsExactly("7>8", "7>9");

    service.delete(7L, 8L);
    assertThat(saved()).containsExactly("7>9");
  }

  @Test
  @DisplayName("needs a structure that exists, not this one, not already recorded either way")
  void rules() {
    links.save(new StructureReplacementXrefEntity(8L, 7L));
    links.save(new StructureReplacementXrefEntity(7L, 9L));

    assertThat(refused(() -> service.add(7L, new ReplacementRequest(" ")))).containsExactly(
        Map.entry("replacedName", "Replaced Structure # is required."));
    assertThat(refused(() -> service.add(7L, new ReplacementRequest("B404")))).containsExactly(
        Map.entry("replacedName", "Structure # B404 does not exist."));
    assertThat(refused(() -> service.add(7L, new ReplacementRequest("B7")))).containsExactly(
        Map.entry("replacedName", "A structure cannot replace itself."));
    assertThat(refused(() -> service.add(7L, new ReplacementRequest("B8")))).containsExactly(
        Map.entry("replacedName", "Replacement already exists."));
    assertThat(refused(() -> service.add(7L, new ReplacementRequest("B9")))).containsExactly(
        Map.entry("replacedName", "Structure # B9 is already recorded as replacing B7."));
  }

  @Test
  @DisplayName("deletes only what this structure replaced, and needs the structure")
  void notFound() {
    links.save(new StructureReplacementXrefEntity(7L, 9L));
    ReplacementRequest request = new ReplacementRequest("B8");

    assertThatThrownBy(() -> service.delete(7L, 9L))
        .isInstanceOf(ReplacementNotFoundException.class);
    assertThatThrownBy(() -> service.add(404L, request))
        .isInstanceOf(StructureNotFoundException.class);
  }

  @Test
  @DisplayName("suggests structures by the start of their Structure #, any case, archived marked")
  void suggestsNames() {
    assertThat(service.suggestNames(" b ")).isEmpty();
    assertThat(service.suggestNames("b8")).extracting("structureName", "archived")
        .containsExactly(tuple("B8", false));
    assertThat(service.suggestNames("B9")).extracting("structureName", "archived")
        .containsExactly(tuple("B9", true));
  }
}
