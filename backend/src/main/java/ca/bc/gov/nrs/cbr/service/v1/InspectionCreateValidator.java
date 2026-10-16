package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.repository.v1.StrctreInspectionTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Part;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Question;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Scope;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Subsection;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Add Inspection's rules — legacy's {@code InspectionForm.validate} and its per-question checks
 * (InspectionAction.java:823-905) — for an inspection saved In Progress, when the date, inspector
 * and company may wait, or Submitted, when they are required. Keyed as the page names its fields: the inspection's by name, an answer as
 * {@link #itemKey}.
 */
@Component
public class InspectionCreateValidator {

  static final String ROUTINE = "ROUT";
  /** The fields more than one rule reports on. */
  private static final String TYPE_CODE = "typeCode";
  private static final String INSPECTION_DATE = "inspectionDate";
  static final String IN_PROGRESS = "PRO";
  static final String SUBMITTED = "SUB";
  /**
   * The statuses an inspection may be added at. Legacy's list also offered Reviewed and Rejected,
   * but those are a P.Eng's verdict on a submitted inspection, set on its own page.
   */
  static final Set<String> ADDABLE_STATUSES = Set.of(IN_PROGRESS, SUBMITTED);
  /** Legacy's answers that need a comment: Poor and Unrated (InspectionItem.java:32-33). */
  private static final Set<String> NEEDS_COMMENT = Set.of("P", "UNR");
  private static final int NAME_MAX = 255;
  private static final int COMMENT_MAX = 4000;
  private static final int TEXT_MAX = 2000;
  private static final long COST_MAX = 999_999L;
  /** The channel measurements: metres to one decimal place, as legacy's ######.# box. */
  private static final BigDecimal METRES_MAX = new BigDecimal("99999.9");
  private static final long STRINGER_MAX = 99_999L;

  private final StructureInspectionRepository inspections;
  private final StrctreInspectionTypeCodeRepository types;
  private final LoggedUserHelper loggedUser;

  public InspectionCreateValidator(StructureInspectionRepository inspections,
      StrctreInspectionTypeCodeRepository types, LoggedUserHelper loggedUser) {
    this.inspections = inspections;
    this.types = types;
    this.loggedUser = loggedUser;
  }

  /** The key an answer's message is under: {@code items.DECK.Q1.span-12}, …stringer-3. */
  public static String itemKey(Item item) {
    StringBuilder key = new StringBuilder("items.")
        .append(item.subsectionCode()).append('.').append(item.questionCode());
    if (item.spanId() != null) {
      key.append(".span-").append(item.spanId());
    }
    if (item.pierId() != null) {
      key.append(".pier-").append(item.pierId());
    }
    if (item.stringerNumber() != null) {
      key.append(".stringer-").append(item.stringerNumber());
    }
    return key.toString();
  }

  /**
   * Every reason the inspection cannot be added; empty when it can.
   *
   * @param form the questions the structure is asked — {@link InspectionFormService#form}
   */
  public Map<String, String> validate(InspectionCreateRequest request, InspectionFormResponse form) {
    Map<String, String> errors = new LinkedHashMap<>();
    checkType(errors, trimmed(request.typeCode()));
    String status = statusOf(request);
    checkStatus(errors, status);
    // Legacy's FRM.validate: past In Progress, the date, inspector and company are required.
    if (status != null && !IN_PROGRESS.equals(status)) {
      required(errors, INSPECTION_DATE, "Date of Inspection", request.inspectionDate());
      required(errors, "inspectorName", "Inspector Name", trimmed(request.inspectorName()));
      required(errors, "inspectorCompanyName", "Inspection Company Name",
          trimmed(request.inspectorCompanyName()));
    }
    checkDate(errors, request);
    tooLong(errors, "inspectorName", "Inspector Name", request.inspectorName(), NAME_MAX);
    tooLong(errors, "inspectorCompanyName", "Inspection Company Name",
        request.inspectorCompanyName(), NAME_MAX);
    Long cost = request.cost();
    if (cost != null && (cost < 0 || cost > COST_MAX)) {
      errors.put("cost", "Cost of Inspection must be whole dollars from $0 to $999,999.");
    }
    if (Boolean.TRUE.equals(request.professionalInspectionRequired()) && !loggedUser.canDestroy()) {
      errors.put("professionalInspectionRequired",
          "Only Level 2 and up can set Professional Inspection Required.");
    }
    tooLong(errors, "inspectionComment", "Inspection Comments", request.inspectionComment(),
        COMMENT_MAX);
    metres(errors, "presentWaterDepth", "Estimated Present Water Level Depth",
        request.presentWaterDepth());
    metres(errors, "presentWaterWidth", "Estimated Present Water Level Width",
        request.presentWaterWidth());
    metres(errors, "highWaterClearance", "Estimated High Water Level Clearance",
        request.highWaterClearance());
    tooLong(errors, "hazardComment", "Hazard Comments", request.hazardComment(), TEXT_MAX);
    checkItems(errors, request.items() == null ? List.of() : request.items(), form);
    return errors;
  }

  /** Required and listed; Routine is Level 1's — Unplanned Level 0's, as the structure's buttons. */
  private void checkType(Map<String, String> errors, String type) {
    if (type == null) {
      errors.put(TYPE_CODE, "Inspection Type is required.");
    } else if (!types.existsById(type)) {
      errors.put(TYPE_CODE, "Inspection Type is not one of the listed types.");
    } else if (ROUTINE.equals(type) && !loggedUser.canEdit()) {
      errors.put(TYPE_CODE, "Only Level 1 and up can add a Routine inspection.");
    }
  }

  /** The status asked for, upper-cased, or null when none is. */
  static String statusOf(InspectionCreateRequest request) {
    String status = trimmed(request.statusCode());
    return status == null ? null : status.toUpperCase(Locale.ROOT);
  }

  private static void checkStatus(Map<String, String> errors, String status) {
    if (status == null) {
      errors.put("statusCode", "Inspection Report Status is required.");
    } else if (!ADDABLE_STATUSES.contains(status)) {
      errors.put("statusCode",
          "An inspection is added In Progress or Submitted; it is reviewed once saved.");
    }
  }

  private static void required(Map<String, String> errors, String field, String label,
      Object value) {
    if (value == null) {
      errors.put(field, label + " is required.");
    }
  }

  /** Not in the future, and not a day the structure was already inspected — when it is entered. */
  private void checkDate(Map<String, String> errors, InspectionCreateRequest request) {
    LocalDate date = request.inspectionDate();
    if (date == null) {
      return;
    }
    if (date.isAfter(LocalDate.now(ZoneId.systemDefault()))) {
      errors.put(INSPECTION_DATE, "Date of Inspection must not be after today.");
    } else if (inspections.existsOnDate(request.structureId(), date)) {
      errors.put(INSPECTION_DATE,
          "Inspection with this date already exists. Please choose another Inspection date.");
    }
  }

  private void checkItems(Map<String, String> errors, List<Item> items,
      InspectionFormResponse form) {
    Set<String> seen = new HashSet<>();
    for (Item item : items) {
      String key = itemKey(item);
      String problem = seen.add(key) ? problemWith(item, form) : "This question is answered twice.";
      if (problem != null) {
        errors.put(key, problem);
      }
    }
  }

  /** Why an answer cannot be saved, or null. */
  private static String problemWith(Item item, InspectionFormResponse form) {
    Optional<Subsection> group = form.subsections().stream()
        .filter(each -> each.code().equals(item.subsectionCode())).findFirst();
    Optional<Question> asked = group.flatMap(each -> each.questions().stream()
        .filter(question -> question.code().equals(item.questionCode())).findFirst());
    if (asked.isEmpty()) {
      return "This question is not asked of this structure.";
    }
    Question question = asked.get();
    String where = whereProblem(item, group.get().scope(), question, form);
    if (where != null) {
      return where;
    }
    String label = "(" + group.get().description() + ") " + question.description();
    String answer = trimmed(item.answer());
    String comment = trimmed(item.comment());
    if (answer == null) {
      // Legacy's message: a comment needs an answer to go with it.
      return comment == null ? null : label + " requires an answer.";
    }
    List<CodeOptionResponse> choices = question.answers();
    if (!choices.isEmpty() && choices.stream().noneMatch(choice -> choice.code().equals(answer))) {
      return label + " is not one of the listed answers.";
    }
    if (choices.isEmpty() && bytes(answer) > NAME_MAX) {
      return atMost(label, NAME_MAX);
    }
    if (NEEDS_COMMENT.contains(answer) && comment == null) {
      return label + " requires a comment.";
    }
    return comment != null && bytes(comment) > TEXT_MAX
        ? atMost("The comment", TEXT_MAX)
        : null;
  }

  /** The span, pier or stringer an answer is for, as its question is asked. */
  private static String whereProblem(Item item, Scope scope, Question question,
      InspectionFormResponse form) {
    boolean spanOk = scope == Scope.SPAN
        ? isOneOf(item.spanId(), form.spans())
        : item.spanId() == null;
    boolean pierOk = scope == Scope.PIER
        ? isOneOf(item.pierId(), form.piers())
        : item.pierId() == null;
    Long stringer = item.stringerNumber();
    boolean stringerOk = question.perStringer()
        ? stringer != null && stringer > 0 && stringer <= STRINGER_MAX
        : stringer == null;
    if (!spanOk) {
      return "Choose one of the bridge's spans for this question.";
    }
    if (!pierOk) {
      return "Choose one of the bridge's piers for this question.";
    }
    return stringerOk ? null
        : "A stringer number from 1 to " + STRINGER_MAX + " is needed for this question.";
  }

  private static boolean isOneOf(Long id, List<Part> parts) {
    return id != null && parts.stream().anyMatch(part -> part.id().equals(String.valueOf(id)));
  }

  private static void metres(Map<String, String> errors, String field, String label,
      BigDecimal value) {
    if (value != null && (value.signum() < 0 || value.stripTrailingZeros().scale() > 1
        || value.compareTo(METRES_MAX) > 0)) {
      errors.put(field, label + " must be metres from 0 to 99999.9, with 1 decimal place at most.");
    }
  }

  private static void tooLong(Map<String, String> errors, String field, String label,
      String value, int max) {
    String text = trimmed(value);
    if (text != null && bytes(text) > max) {
      errors.put(field, atMost(label, max));
    }
  }

  /** "Inspector Name can be at most 255 characters." */
  private static String atMost(String subject, int max) {
    return subject + " can be at most " + max + " characters.";
  }

  private static int bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
