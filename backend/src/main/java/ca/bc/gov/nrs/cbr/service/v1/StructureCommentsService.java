package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.CommentNotFoundException;
import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCommentRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CommentRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CreatedResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comments on a structure — legacy's comment lists on the structure page: the Details tab's general
 * comments and the Inspections tab's planned-inspection ones, one table told apart by
 * {@code PLANNED_INSPECTION_CMT_IND}. Adds planned-inspection comments and edits either kind;
 * legacy deletes none.
 */
@Service
public class StructureCommentsService {

  private static final Logger log = LoggerFactory.getLogger(StructureCommentsService.class);

  /** A planned-inspection comment, as against a general one. */
  private static final String PLANNED_INSPECTION = "Y";
  /** The comment column, in bytes. */
  private static final int TEXT_MAX = 2000;
  private static final String FIELD = "comment";

  private final CrossingStructureRepository structures;
  private final StructureCommentRepository comments;
  private final LoggedUserHelper loggedUser;

  public StructureCommentsService(
      CrossingStructureRepository structures,
      StructureCommentRepository comments,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.comments = comments;
    this.loggedUser = loggedUser;
  }

  /**
   * Adds a planned-inspection comment — legacy's add on the Inspections tab
   * ({@code addPlannedInspectionComment}, {@code CBR.INSERT_STRUCTURE_COMMENT}), written here at
   * once where legacy held it for the page's Save. Entered and last updated by the user, now; its
   * id from {@code STRUCTURE_COMMENT_SEQ} through the entity's generator.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   when the text is blank or over 2000 bytes
   */
  @Transactional
  public CreatedResponse addPlannedInspectionComment(long structureId,
      CommentRequest request) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    String text = validated(request);
    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    StructureCommentEntity saved = comments.save(StructureCommentEntity.builder()
        .crossingStructureId(structureId)
        .structureComment(text)
        .plannedInspectionCmtInd(PLANNED_INSPECTION)
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build());
    log.info("Added planned-inspection comment {} to structure {}",
        saved.getStructureCommentId(), structureId);
    return new CreatedResponse(String.valueOf(saved.getStructureCommentId()));
  }

  /**
   * Changes a comment's text — legacy's edit pencil ({@code updateComment},
   * {@code CBR.UPDATE_STRUCTURE_COMMENT}): the text, and who last updated it and when; its kind and
   * who entered it are kept. As legacy, anyone who may edit may change any comment — no ownership
   * check.
   *
   * @throws CommentNotFoundException if the structure has no such comment
   * @throws FieldValidationException when the text is blank or over 2000 bytes
   */
  @Transactional
  public void update(long structureId, long commentId, CommentRequest request) {
    StructureCommentEntity comment = comments.findById(commentId)
        .filter(found -> Objects.equals(found.getCrossingStructureId(), structureId))
        .orElseThrow(() -> new CommentNotFoundException(structureId, commentId));
    String text = validated(request);
    comments.save(comment.toBuilder()
        .structureComment(text)
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated comment {} of structure {}", commentId, structureId);
  }

  /** The text, trimmed, or a refusal naming the comment field. */
  private static String validated(CommentRequest request) {
    String text = request.comment() == null ? "" : request.comment().trim();
    if (text.isEmpty()) {
      throw new FieldValidationException("Comment cannot be saved",
          Map.of(FIELD, "Comment is required."));
    }
    // Bytes, because the column is declared in bytes and an accented character costs two.
    if (text.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX) {
      throw new FieldValidationException("Comment cannot be saved",
          Map.of(FIELD, "Comment can be at most " + TEXT_MAX + " characters."));
    }
    return text;
  }
}
