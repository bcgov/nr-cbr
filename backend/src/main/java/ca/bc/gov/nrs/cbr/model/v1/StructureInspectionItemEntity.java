package ca.bc.gov.nrs.cbr.model.v1;

import ca.bc.gov.nrs.cbr.model.id.AssignableSequence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.STRUCTURE_INSPECTION_ITEM} — One answered question of a filled-in inspection form — the inspection form engine's leaf row.
 *
 * <p>Written by Add Inspection: one row per answered question — per span, pier or stringer where the
 * question is asked of each.
 */
@Entity
@Table(name = "STRUCTURE_INSPECTION_ITEM", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureInspectionItemId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StructureInspectionItemEntity {

  @Id
  @AssignableSequence
  @Column(name = "STRUCTURE_INSPECTION_ITEM_ID")
  private Long structureInspectionItemId;

  @Column(name = "INSPECTION_ID")
  private Long inspectionId;

  /** The span this answer is about, if any — such a span cannot be deleted. */
  @Column(name = "FOREST_SERVICE_BRIDGE_SPAN_ID")
  private Long forestServiceBridgeSpanId;

  /** The pier this answer is about, if any — such a pier cannot be deleted. */
  @Column(name = "FOREST_SERVICE_BRIDGE_PIER_ID")
  private Long forestServiceBridgePierId;

  /** The question's group — legacy's subsection: APP, DECK, SS, STR, PIER, WDC, … */
  @Column(name = "STRUCTURE_INSPCTN_SUBSCTN_CODE", length = 10)
  private String subsectionCode;

  @Column(name = "STRUCTURE_INSPCTN_QSTN_CODE", length = 10)
  private String questionCode;

  /** An answer code, or typed text for a question with no listed answers. Never blank. */
  @Column(name = "ANSWER_VALUE", length = 255)
  private String answerValue;

  @Column(name = "ITEM_COMMENT", length = 2000)
  private String itemComment;

  /** For a question asked of each stringer — a log-stringer bridge's, or a wood box culvert's. */
  @Column(name = "STRINGER_NUMBER")
  private Long stringerNumber;

  @Column(name = "ENTRY_USERID", length = 30, updatable = false)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP", updatable = false)
  private LocalDateTime entryTimestamp;

  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
