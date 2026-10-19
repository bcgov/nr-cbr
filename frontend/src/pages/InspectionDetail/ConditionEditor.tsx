import { Button } from '@carbon/react';
import { useState } from 'react';

import { SubsectionCard } from '@/pages/NewInspection/ConditionSection';

import type { InspectionItem } from './inspectionDetail';
import type {
  InspectionFormResponse,
  InspectionSubsection,
} from '@/pages/NewInspection/inspectionForm';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateInspectionCondition } from '@/hooks/useInspectionSearch';
import { answerErrorsOf, itemKey, type Answers } from '@/pages/NewInspection/inspectionRules';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

const id = (value: string | null) => (value === null ? null : Number(value));

/** A group's stored answers, as Add Inspection's form keeps them. */
const answersOf = (items: InspectionItem[], subsectionCode: string): Answers =>
  Object.fromEntries(
    items
      .filter((item) => item.subsectionCode === subsectionCode)
      .map((item) => {
        const answer = {
          subsectionCode: item.subsectionCode,
          questionCode: item.questionCode,
          spanId: id(item.spanId),
          pierId: id(item.pierId),
          stringerNumber: item.stringerNumber,
          answer: item.answer ?? '',
          comment: item.comment ?? '',
        };
        return [itemKey(answer), answer];
      }),
  );

/**
 * One Condition card, edited in place — Add Inspection's card: its questions at each span, pier
 * and stringer, its stringers as a table with one stringer's form at a time, and its rules. Save
 * sends every answer the group has; what it no longer has is taken out.
 */
const ConditionEditor: FC<{
  inspectionId: string;
  subsection: InspectionSubsection;
  form: InspectionFormResponse;
  items: InspectionItem[];
  onDone: () => void;
}> = ({ inspectionId, subsection, form, items, onDone }) => {
  const [answers, setAnswers] = useState<Answers>(() => answersOf(items, subsection.code));
  const update = useUpdateInspectionCondition(inspectionId, subsection.code);
  const { display } = useNotification();
  const fields = useFieldErrors<string>(answerErrorsOf(answers, [subsection]), update.fieldErrors);

  const save = () => {
    if (!fields.submit()) return;
    update.mutate(
      Object.values(answers).filter((entry) => entry.answer.trim() || entry.comment.trim()),
      {
        onSuccess: () => {
          display({ kind: 'success', title: `${subsection.description} saved`, timeout: 4000 });
          onDone();
        },
        onError: (error) => {
          // Answers refused show beside themselves; anything else is a toast.
          if (Object.keys(apiFieldErrors(error)).length > 0) return;
          display({
            kind: 'error',
            title: `${subsection.description} was not saved`,
            subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
            timeout: 0,
          });
        },
      },
    );
  };

  return (
    <div className="structure-detail__edit">
      <SubsectionCard
        subsection={subsection}
        spans={form.spans}
        piers={form.piers}
        answers={answers}
        onAnswer={(entry) => {
          const key = itemKey(entry);
          setAnswers((current) => ({ ...current, [key]: entry }));
          fields.change(key);
        }}
        shown={fields.shown}
        onLeave={fields.leave}
        footer={
          // The card's actions, as the structure page's: small, at the right, under the fields.
          <div className="structure-detail__edit-actions">
            <Button
              onMouseDown={holdFocus}
              kind="tertiary"
              size="sm"
              onClick={onDone}
              disabled={update.isPending}
              data-testid={`condition-${subsection.code}-cancel`}
            >
              Cancel
            </Button>
            <Button
              onMouseDown={holdFocus}
              kind="primary"
              size="sm"
              onClick={save}
              disabled={update.isPending}
              data-testid={`condition-${subsection.code}-save`}
            >
              {update.isPending ? 'Saving…' : 'Save changes'}
            </Button>
          </div>
        }
      />
    </div>
  );
};

export default ConditionEditor;
