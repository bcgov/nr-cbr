import { Edit } from '@carbon/icons-react';
import {
  Button,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import DateField from '@/pages/StructureDetail/DateField';
import { requiredLabel } from '@/utils/requiredLabel';

import {
  CARD_STATUSES,
  LOCKING_STATUSES,
  canChangeStatus,
  cardErrorsOf,
  requiredOf,
  toUpdateRequest,
  valuesOf,
} from './inspectionEditRules';
import {
  InspectionCard,
  InspectionCommentsCard,
  LoadRatingCard,
  PengCommentsCard,
  StatusTag,
  StructureCard,
} from './InspectionViews';
import ReviewDialog from './ReviewDialog';

import type { InspectionDetailResponse, InspectionReviewRequest } from './inspectionDetail';
import type { ReviewField } from './reviewRules';
import type { StructureDetailResponse } from '@/pages/StructureDetail/structureResponse';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useInspectionReportStatusCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import {
  useReviewInspection,
  useUpdateInspection,
  useUpdateInspectionComment,
  useUpdatePengComment,
} from '@/hooks/useInspectionSearch';
import {
  COMMENT_MAX,
  IN_PROGRESS,
  type InspectionValues,
} from '@/pages/NewInspection/inspectionRules';
import { describe } from '@/pages/StructureDetail/format';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength } from '@/utils/textLimits';

/** Cancel and Save, small at the right under the fields — the structure page's cards'. */
const EditActions: FC<{
  pending: boolean;
  onCancel: () => void;
  onSave: () => void;
  id: string;
}> = ({ pending, onCancel, onSave, id }) => (
  <div className="structure-detail__edit-actions">
    <Button
      onMouseDown={holdFocus}
      kind="tertiary"
      size="sm"
      onClick={onCancel}
      disabled={pending}
      data-testid={`${id}-cancel`}
    >
      Cancel
    </Button>
    <Button
      onMouseDown={holdFocus}
      kind="primary"
      size="sm"
      onClick={onSave}
      disabled={pending}
      data-testid={`${id}-save`}
    >
      {pending ? 'Saving…' : 'Save changes'}
    </Button>
  </div>
);

/** A save's outcome as the app reports one: a toast, unless a field was refused beside itself. */
const useSaved = (what: string) => {
  const { display } = useNotification();
  return {
    saved: () => display({ kind: 'success', title: `${what} saved`, timeout: 4000 }),
    failed: (error: unknown) => {
      if (Object.keys(apiFieldErrors(error)).length > 0) return;
      display({
        kind: 'error',
        title: `The ${what.toLowerCase()} were not saved`,
        subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
        timeout: 0,
      });
    },
  };
};

/** Reviewed — a P.Eng's, saved through the Review dialog rather than with the card. */
const REVIEWED = 'RVD';
/** The Review dialog's own fields; any other refusal is the card's. */
const REVIEW_FIELDS = new Set<string>([
  'loadRating',
  'reasonCode',
  'reasonComment',
  'nextRoutineDate',
  'routineFrequencyYears',
]);

/**
 * The Inspection card's form: status, date, inspector, company, cost, professional. A P.Eng may
 * pick Reviewed: Save then opens the Review dialog — legacy's Accept — which saves the card as it
 * stands, then the review.
 */
const InspectionEditor: FC<{
  inspection: InspectionDetailResponse;
  structure: StructureDetailResponse | undefined;
  onDone: () => void;
}> = ({ inspection, structure, onDone }) => {
  const { isPeng, canDelete: level2 } = useAuthorization();
  const statuses = useInspectionReportStatusCodes();
  const update = useUpdateInspection(inspection.id);
  const review = useReviewInspection(inspection.id);
  const report = useSaved('Inspection');
  const { display } = useNotification();
  const [reviewing, setReviewing] = useState(false);
  const [values, setValues] = useState<InspectionValues>(() => valuesOf(inspection));
  const form = useFieldErrors<string>(cardErrorsOf(values, inspection), update.fieldErrors);
  const { shown } = form;
  const required = requiredOf(values, inspection);
  const current = inspection.status.code;

  const change = <K extends keyof InspectionValues>(field: K, value: InspectionValues[K]) => {
    setValues((previous) => ({ ...previous, [field]: value }));
    form.change(field);
  };
  const text = (field: 'inspectorName' | 'inspectorCompanyName' | 'cost', label: ReactNode) => (
    <TextInput
      id={`inspection-edit-${field}`}
      data-testid={`inspection-edit-${field}`}
      labelText={label}
      inputMode={field === 'cost' ? 'numeric' : undefined}
      maxLength={field === 'cost' ? 6 : 255}
      value={values[field]}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
      onBlur={() => form.leave(field)}
    />
  );

  const save = () => {
    if (!form.submit()) return;
    if (values.statusCode === REVIEWED) {
      setReviewing(true);
      return;
    }
    update.mutate(toUpdateRequest(values), {
      onSuccess: () => {
        report.saved();
        onDone();
      },
      onError: report.failed,
    });
  };

  /** Accept: the card as it stands, at the status it has, then the review. */
  const accept = async (request: InspectionReviewRequest) => {
    try {
      await update.mutateAsync(toUpdateRequest({ ...values, statusCode: current ?? IN_PROGRESS }));
    } catch (error) {
      // The card's refusals show beside its fields, under the dialog.
      setReviewing(false);
      report.failed(error);
      return;
    }
    review.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Inspection reviewed', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        const refused = Object.keys(apiFieldErrors(error));
        // The dialog's own fields show their refusals; anything else is a toast.
        if (refused.length > 0 && refused.every((field) => REVIEW_FIELDS.has(field))) return;
        display({
          kind: 'error',
          title: 'The inspection was not reviewed',
          subtitle:
            refused.length > 0
              ? Object.values(apiFieldErrors(error)).join(' ')
              : apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  // In Progress and Submitted, and the status it has if neither (Rejected), so it reads right;
  // Reviewed for a P.Eng, through the Review dialog.
  const offered = (statuses.data ?? []).filter(
    (status) =>
      CARD_STATUSES.has(status.code) ||
      status.code === current ||
      (isPeng && status.code === REVIEWED),
  );

  return (
    <div className="structure-detail__edit">
      <div className="structure-detail__fields">
        <ReadOnlyField label="Inspection Type" value={describe(inspection.type)} />
        {canChangeStatus(current, isPeng) ? (
          <Select
            id="inspection-edit-statusCode"
            data-testid="inspection-edit-statusCode"
            labelText={requiredLabel('Inspection Report Status', true)}
            value={values.statusCode}
            invalid={Boolean(shown.statusCode)}
            invalidText={shown.statusCode}
            onChange={(event) => change('statusCode', event.target.value)}
            onBlur={() => form.leave('statusCode')}
          >
            {offered.map((status) => (
              <SelectItem key={status.code} value={status.code} text={status.description} />
            ))}
          </Select>
        ) : (
          // Out of Rejected, a P.Eng's only — legacy's permission.
          <ReadOnlyField
            label="Inspection Report Status"
            value={<StatusTag status={inspection.status} />}
          />
        )}
        <DateField
          id="inspection-edit-date"
          labelText={requiredLabel('Date of Inspection', required.inspectionDate)}
          value={values.inspectionDate}
          invalidText={shown.inspectionDate}
          onChange={(value) => change('inspectionDate', value)}
          onBlur={() => form.leave('inspectionDate')}
        />
        {text('inspectorName', requiredLabel('Inspector Name', required.inspectorName))}
        {text(
          'inspectorCompanyName',
          requiredLabel('Inspection Company Name', required.inspectorCompanyName),
        )}
        {text('cost', 'Cost of Inspection ($)')}
        {level2 ? (
          <RadioButtonGroup
            name="inspection-edit-professional"
            legendText="Professional Inspection Required?"
            valueSelected={values.professionalInspectionRequired ? 'Y' : 'N'}
            onChange={(selected) => change('professionalInspectionRequired', selected === 'Y')}
          >
            <RadioButton id="inspection-edit-professional-yes" labelText="Yes" value="Y" />
            <RadioButton id="inspection-edit-professional-no" labelText="No" value="N" />
          </RadioButtonGroup>
        ) : (
          <ReadOnlyField
            label="Professional Inspection Required?"
            value={values.professionalInspectionRequired ? 'Yes' : 'No'}
          />
        )}
      </div>
      <EditActions
        id="inspection"
        pending={update.isPending || review.isPending}
        onCancel={onDone}
        onSave={save}
      />
      {reviewing && (
        <ReviewDialog
          inspection={inspection}
          structure={structure}
          pending={update.isPending || review.isPending}
          serverErrors={review.fieldErrors as Partial<Record<ReviewField, string>>}
          onAccept={(request) => void accept(request)}
          onCancel={() => setReviewing(false)}
        />
      )}
    </div>
  );
};

/** A comments card's form: the inspector's comments, or the P.Eng's. */
const CommentsEditor: FC<{
  /** The field the server refuses it under: `inspectionComment`, `pengComment`. */
  field: string;
  /** The id its box and buttons are named from: `inspection-edit-{id}`, `{actions}-save`. */
  id: string;
  actions: string;
  label: string;
  /** What the toast calls it: "Inspection comments". */
  what: string;
  initial: string | null;
  update: ReturnType<typeof useUpdateInspectionComment>;
  onDone: () => void;
}> = ({ field, id, actions, label, what, initial, update, onDone }) => {
  const report = useSaved(what);
  const [comment, setComment] = useState(initial ?? '');
  const tooLong = byteLength(comment.trim()) > COMMENT_MAX;
  const form = useFieldErrors<string>(
    { [field]: tooLong ? `${label} can be at most ${COMMENT_MAX} characters.` : undefined },
    update.fieldErrors,
  );

  const save = () => {
    if (!form.submit()) return;
    update.mutate(comment, {
      onSuccess: () => {
        report.saved();
        onDone();
      },
      onError: report.failed,
    });
  };

  return (
    <div className="structure-detail__edit">
      <FieldWithCounter used={byteLength(comment)} limit={COMMENT_MAX}>
        <TextArea
          id={`inspection-edit-${id}`}
          data-testid={`inspection-edit-${id}`}
          labelText="Comments"
          rows={6}
          value={comment}
          invalid={Boolean(form.shown[field])}
          invalidText={form.shown[field]}
          onChange={(event) => {
            setComment(event.target.value);
            form.change(field);
          }}
          onBlur={() => form.leave(field)}
        />
      </FieldWithCounter>
      <EditActions id={actions} pending={update.isPending} onCancel={onDone} onSave={save} />
    </div>
  );
};

/** The Inspection Comments card's form. */
const InspectionCommentsEditor: FC<{
  inspection: InspectionDetailResponse;
  onDone: () => void;
}> = ({ inspection, onDone }) => (
  <CommentsEditor
    field="inspectionComment"
    id="comment"
    actions="inspection-comment"
    label="Inspection Comments"
    what="Inspection comments"
    initial={inspection.inspectionComment}
    update={useUpdateInspectionComment(inspection.id)}
    onDone={onDone}
  />
);

/** The PEng Comments card's form — a P.Eng's. */
const PengCommentsEditor: FC<{ inspection: InspectionDetailResponse; onDone: () => void }> = ({
  inspection,
  onDone,
}) => (
  <CommentsEditor
    field="pengComment"
    id="peng-comment"
    actions="peng-comment"
    label="PEng Comments"
    what="PEng comments"
    initial={inspection.review.pengComment}
    update={useUpdatePengComment(inspection.id)}
    onDone={onDone}
  />
);

/**
 * The Inspection tab: the structure and its site, the inspection and its comments, then the P.Eng's
 * part — the load rating the review recorded and the PEng Comments — each card with its own Edit,
 * one at a time, the structure page's pattern. Level 0 and up edit (legacy's `/level0Access`);
 * Professional Inspection Required is Level 2's; the PEng Comments and Reviewed a P.Eng's. No Edit
 * once the inspection is Reviewed or Accepted, as legacy disables its page then.
 */
const InspectionTab: FC<{
  inspection: InspectionDetailResponse;
  structure: StructureDetailResponse | undefined;
}> = ({ inspection, structure }) => {
  const { canWriteInspection, isPeng } = useAuthorization();
  const [editing, setEditing] = useState<Card | null>(null);
  const open = !LOCKING_STATUSES.has(inspection.status.code ?? '');
  const edit = (card: Card, label: string, allowed = canWriteInspection) =>
    allowed &&
    open &&
    editing === null && (
      <Button
        kind="tertiary"
        size="sm"
        renderIcon={Edit}
        data-testid={`${card}-edit`}
        onClick={() => setEditing(card)}
      >
        {label}
      </Button>
    );
  const done = () => setEditing(null);

  return (
    <>
      <StructureCard inspection={inspection} structure={structure} />
      {/* The editor in place of the card's values while it is edited; the values otherwise. */}
      <InspectionCard inspection={inspection} action={edit('inspection', 'Edit inspection')}>
        {editing === 'inspection' ? (
          <InspectionEditor inspection={inspection} structure={structure} onDone={done} />
        ) : undefined}
      </InspectionCard>
      <InspectionCommentsCard inspection={inspection} action={edit('comments', 'Edit comments')}>
        {editing === 'comments' ? (
          <InspectionCommentsEditor inspection={inspection} onDone={done} />
        ) : undefined}
      </InspectionCommentsCard>
      <LoadRatingCard inspection={inspection} />
      <PengCommentsCard
        inspection={inspection}
        action={edit('peng-comments', 'Edit PEng comments', isPeng)}
      >
        {editing === 'peng-comments' ? (
          <PengCommentsEditor inspection={inspection} onDone={done} />
        ) : undefined}
      </PengCommentsCard>
    </>
  );
};

/** The tab's cards that edit. */
type Card = 'inspection' | 'comments' | 'peng-comments';

export default InspectionTab;
