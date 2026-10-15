import { Edit, Renew } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import { blankToEmpty } from './formFields';
import { money, number } from './format';
import ReplacementFields from './ReplacementFields';
import {
  replacementErrorsOf,
  replacementWarningsOf,
  toReplacementRequest,
  type ReplacementValues,
} from './replacementRules';

import type { ReplacementDetails } from './structureResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateReplacementDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

type FormProps = {
  structureId: string;
  replacement: ReplacementDetails;
  yearBuilt: number | null;
  onDone: () => void;
};

/**
 * The card's values as a form, in place of the view, every field where the view has it. Each
 * error shows once its field is left, then as it is corrected; legacy's warnings as typed.
 */
const ReplacementForm: FC<FormProps> = ({ structureId, replacement, yearBuilt, onDone }) => {
  const update = useUpdateReplacementDetails(structureId);
  const { display } = useNotification();
  const [values, setValues] = useState<ReplacementValues>({
    closureYear: blankToEmpty(replacement.estimatedClosureYear),
    replacementYear: blankToEmpty(replacement.estimatedReplacementYear),
    loadRestrictionYear: blankToEmpty(replacement.estimatedLoadRestrictionYear),
    cost: blankToEmpty(replacement.estimatedReplacementCost),
    costComment: replacement.replacementCostComment ?? '',
  });
  // Legacy's tier rule: required once the structure has one.
  const restrictionRequired = replacement.estimatedLoadRestrictionYear !== null;
  const form = useFieldErrors<keyof ReplacementValues>(
    replacementErrorsOf(values, restrictionRequired),
    update.fieldErrors,
  );

  const change = (field: keyof ReplacementValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    update.mutate(toReplacementRequest(values), {
      onSuccess: () => {
        display({ kind: 'success', title: 'Replacement details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The replacement details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  return (
    <div className="structure-detail__edit" data-testid="replacement-details-form">
      <ReplacementFields
        values={values}
        shown={form.shown}
        warnings={replacementWarningsOf(values, yearBuilt)}
        change={change}
        leave={form.leave}
        restrictionRequired={restrictionRequired}
      />

      {/* The form's actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button
          kind="tertiary"
          size="sm"
          onMouseDown={holdFocus}
          onClick={onDone}
          disabled={update.isPending}
        >
          Cancel
        </Button>
        <Button
          kind="primary"
          size="sm"
          onMouseDown={holdFocus}
          onClick={save}
          disabled={update.isPending}
          data-testid="replacement-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

type Props = {
  structureId: string;
  replacement: ReplacementDetails;
  /** Year Superstructure Installed, which legacy's year warnings count from. */
  yearBuilt: number | null;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/**
 * The Details tab's Replacement Details — legacy's estimates of closure, replacement and load
 * restriction, and the cost — with its own "Edit replacement details" for a P.Eng, legacy's
 * `/pEngAccess` on every one of its fields. The edit replaces the values in place, as the other
 * cards do. The replacement history is a card of its own, `ReplacementHistoryCard`.
 */
const ReplacementCard: FC<Props> = ({
  structureId,
  replacement,
  yearBuilt,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { isPeng } = useAuthorization();
  return (
    <Card
      title="Replacement Details"
      icon={Renew}
      testId="structure-section-replacement"
      action={
        isPeng &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="replacement-details-edit"
            onClick={onEdit}
          >
            Edit replacement details
          </Button>
        )
      }
    >
      {editing ? (
        <ReplacementForm
          structureId={structureId}
          replacement={replacement}
          yearBuilt={yearBuilt}
          onDone={onDone}
        />
      ) : (
        <>
          <div className="structure-detail__fields">
            <ReadOnlyField
              label="Estimated Closure (year)"
              value={number(replacement.estimatedClosureYear)}
            />
            <ReadOnlyField
              label="Estimated Replacement (year)"
              value={number(replacement.estimatedReplacementYear)}
            />
            <ReadOnlyField
              label="Estimated Load Restriction (year)"
              value={number(replacement.estimatedLoadRestrictionYear)}
            />
            <ReadOnlyField
              label="Estimated Replacement Cost ($)"
              value={money(replacement.estimatedReplacementCost)}
            />
          </div>
          <div className="structure-detail__wide">
            <ReadOnlyField
              label="Replacement Cost Comments"
              value={replacement.replacementCostComment}
            />
          </div>
        </>
      )}
    </Card>
  );
};

export default ReplacementCard;
