package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.CommentNotFoundException;
import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CommentRequest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Adding and editing structure comments, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureCommentsService.class)
class StructureCommentsServiceTest {

  private static final LocalDateTime WRITTEN = LocalDateTime.of(2020, 1, 1, 9, 0);

  @Autowired
  private StructureCommentsService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    for (String entity : List.of("StructureCommentEntity", "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (long id : List.of(7L, 8L)) {
      entityManager.persist(CrossingStructureEntity.builder()
          .crossingStructureId(id).crossingStructureName("B" + id).structureTypeClassCode("TB")
          .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
    }
  }

  private long givenComment(String kind) {
    StructureCommentEntity comment = StructureCommentEntity.builder()
        .crossingStructureId(7L).structureComment("Bring a boat.").plannedInspectionCmtInd(kind)
        .entryUserid("IDIR\\AUTHOR").entryTimestamp(WRITTEN)
        .updateUserid("IDIR\\AUTHOR").updateTimestamp(WRITTEN).build();
    entityManager.persist(comment);
    entityManager.flush();
    entityManager.clear();
    return comment.getStructureCommentId();
  }

  private StructureCommentEntity stored(long id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(StructureCommentEntity.class, id);
  }

  @Test
  @DisplayName("adds a planned-inspection comment, trimmed, entered and updated by the user")
  void addsPlanned() {
    String id = service.addPlannedInspectionComment(7L, new CommentRequest("  Use the UBIU.  "))
        .id();

    StructureCommentEntity comment = stored(Long.parseLong(id));
    assertThat(comment.getCrossingStructureId()).isEqualTo(7L);
    assertThat(comment.getStructureComment()).isEqualTo("Use the UBIU.");
    assertThat(comment.getPlannedInspectionCmtInd()).isEqualTo("Y");
    assertThat(comment.getEntryUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(comment.getEntryTimestamp()).isNotNull();
    assertThat(comment.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(comment.getUpdateTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("changes the text and who last updated it, keeping its kind and who entered it — "
      + "anyone's comment, as legacy")
  void updates() {
    long id = givenComment("Y");

    service.update(7L, id, new CommentRequest(" Bring two boats. "));

    StructureCommentEntity comment = stored(id);
    assertThat(comment.getStructureComment()).isEqualTo("Bring two boats.");
    assertThat(comment.getPlannedInspectionCmtInd()).isEqualTo("Y");
    assertThat(comment.getEntryUserid()).isEqualTo("IDIR\\AUTHOR");
    assertThat(comment.getEntryTimestamp()).isEqualTo(WRITTEN);
    assertThat(comment.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(comment.getUpdateTimestamp()).isAfter(WRITTEN);
  }

  @Test
  @DisplayName("needs some text, and at most 2000 bytes of it")
  void refusesText() {
    CommentRequest blank = new CommentRequest("  ");
    CommentRequest tooLong = new CommentRequest("é".repeat(1001));

    assertThatThrownBy(() -> service.addPlannedInspectionComment(7L, blank))
        .isInstanceOfSatisfying(FieldValidationException.class, refused ->
            assertThat(refused.getFieldErrors()).containsEntry("comment", "Comment is required."));
    assertThatThrownBy(() -> service.addPlannedInspectionComment(7L, tooLong))
        .isInstanceOfSatisfying(FieldValidationException.class, refused ->
            assertThat(refused.getFieldErrors()).containsEntry("comment",
                "Comment can be at most 2000 characters."));
  }

  @Test
  @DisplayName("edits a comment only through its own structure")
  void updateOnlyThroughItsStructure() {
    long id = givenComment("Y");
    CommentRequest request = new CommentRequest("Text.");

    assertThatThrownBy(() -> service.update(8L, id, request))
        .isInstanceOf(CommentNotFoundException.class);
    assertThatThrownBy(() -> service.update(7L, id + 1000, request))
        .isInstanceOf(CommentNotFoundException.class);
  }

  @Test
  @DisplayName("adds nothing to a structure that does not exist")
  void missingStructure() {
    CommentRequest request = new CommentRequest("Text.");

    assertThatThrownBy(() -> service.addPlannedInspectionComment(404L, request))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
