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
  StatusTag,
  StructureCard,
} from './InspectionViews';

import type { InspectionDetailResponse } from './inspectionDetail';
import type { StructureDetailResponse } from '@/pages/StructureDetail/structureResponse';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useInspectionReportStatusCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateInspection, useUpdateInspectionComment } from '@/hooks/useInspectionSearch';
import { COMMENT_MAX, type InspectionValues } from '@/pages/NewInspection/inspectionRules';
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

/** The Inspection card's form: status, date, inspector, company, cost, professional. */
const InspectionEditor: FC<{ inspection: InspectionDetailResponse; onDone: () => void }> = ({
  inspection,
  onDone,
}) => {
  const { isPeng, canDelete: level2 } = useAuthorization();
  const statuses = useInspectionReportStatusCodes();
  const update = useUpdateInspection(inspection.id);
  const report = useSaved('Inspection');
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
    update.mutate(toUpdateRequest(values), {
      onSuccess: () => {
        report.saved();
        onDone();
      },
      onError: report.failed,
    });
  };

  // In Progress and Submitted, and the status it has if neither (Rejected), so it reads right.
  const offered = (statuses.data ?? []).filter(
    (status) => CARD_STATUSES.has(status.code) || status.code === current,
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
      <EditActions id="inspection" pending={update.isPending} onCancel={onDone} onSave={save} />
    </div>
  );
};

/** The Inspection Comments card's form. */
const CommentsEditor: FC<{ inspection: InspectionDetailResponse; onDone: () => void }> = ({
  inspection,
  onDone,
}) => {
  const update = useUpdateInspectionComment(inspection.id);
  const report = useSaved('Inspection comments');
  const [comment, setComment] = useState(inspection.inspectionComment ?? '');
  const tooLong = byteLength(comment.trim()) > COMMENT_MAX;
  const form = useFieldErrors<string>(
    {
      inspectionComment: tooLong
        ? `Inspection Comments can be at most ${COMMENT_MAX} characters.`
        : undefined,
    },
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
          id="inspection-edit-comment"
          data-testid="inspection-edit-comment"
          labelText="Comments"
          rows={6}
          value={comment}
          invalid={Boolean(form.shown.inspectionComment)}
          invalidText={form.shown.inspectionComment}
          onChange={(event) => {
            setComment(event.target.value);
            form.change('inspectionComment');
          }}
          onBlur={() => form.leave('inspectionComment')}
        />
      </FieldWithCounter>
      <EditActions
        id="inspection-comment"
        pending={update.isPending}
        onCancel={onDone}
        onSave={save}
      />
    </div>
  );
};

/**
 * The Inspection tab: the structure and its site, then the inspection and its comments — each
 * card with its own Edit, one at a time, the structure page's pattern. Level 0 and up edit
 * (legacy's `/level0Access`); Professional Inspection Required is Level 2's. No Edit once the
 * inspection is Reviewed or Accepted, as legacy disables its page then.
 */
const InspectionTab: FC<{
  inspection: InspectionDetailResponse;
  structure: StructureDetailResponse | undefined;
}> = ({ inspection, structure }) => {
  const { canWriteInspection } = useAuthorization();
  const [editing, setEditing] = useState<'inspection' | 'comments' | null>(null);
  const canEdit = canWriteInspection && !LOCKING_STATUSES.has(inspection.status.code ?? '');
  const edit = (card: 'inspection' | 'comments', label: string) =>
    canEdit &&
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

  return (
    <>
      <StructureCard inspection={inspection} structure={structure} />
      {/* The editor in place of the card's values while it is edited; the values otherwise. */}
      <InspectionCard inspection={inspection} action={edit('inspection', 'Edit inspection')}>
        {editing === 'inspection' ? (
          <InspectionEditor inspection={inspection} onDone={() => setEditing(null)} />
        ) : undefined}
      </InspectionCard>
      <InspectionCommentsCard inspection={inspection} action={edit('comments', 'Edit comments')}>
        {editing === 'comments' ? (
          <CommentsEditor inspection={inspection} onDone={() => setEditing(null)} />
        ) : undefined}
      </InspectionCommentsCard>
    </>
  );
};

export default InspectionTab;
