import { Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState } from 'react';

import { HazardFields } from '@/pages/NewInspection/InspectionFields';

import { LABELS, requiredMeasurementsOf, valuesOf, type Measurement } from './hazardsRules';
import { HazardsCard } from './InspectionViews';

import type { InspectionDetailResponse } from './inspectionDetail';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateInspectionHazards } from '@/hooks/useInspectionSearch';
import {
  hazardErrorsOf,
  hazardWarningsOf,
  toMetres,
  type HazardValues,
} from '@/pages/NewInspection/inspectionRules';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

/** The statuses after which legacy's page is disabled — Reviewed and Accepted, not Rejected. */
const LOCKING_STATUSES = new Set(['RVD', 'ACC']);
/** The card's form, in place of its values while Edit is on. */
const HazardsEditor: FC<{ inspection: InspectionDetailResponse; onDone: () => void }> = ({
  inspection,
  onDone,
}) => {
  const [values, setValues] = useState<HazardValues>(() => valuesOf(inspection));
  const update = useUpdateInspectionHazards(inspection.id);
  const { display } = useNotification();
  const required = requiredMeasurementsOf(inspection);

  const errors: Record<string, string | undefined> = { ...hazardErrorsOf(values) };
  for (const field of Object.keys(LABELS) as Measurement[]) {
    if (required[field] && !values[field].trim()) {
      errors[field] = `${LABELS[field]} is required.`;
    }
  }
  const form = useFieldErrors<string>(errors, update.fieldErrors);

  const save = () => {
    if (!form.submit()) return;
    update.mutate(
      {
        presentWaterDepth: toMetres(values.presentWaterDepth),
        presentWaterWidth: toMetres(values.presentWaterWidth),
        highWaterClearance: toMetres(values.highWaterClearance),
        highWater: values.highWater,
        debris: values.debris,
        scour: values.scour,
        ice: values.ice,
        aggradation: values.aggradation,
        comment: values.hazardComment,
      },
      {
        onSuccess: () => {
          display({ kind: 'success', title: 'Hydraulic hazards saved', timeout: 4000 });
          onDone();
        },
        onError: (error) => {
          // Field refusals show beside their fields; anything else is a toast.
          if (Object.keys(apiFieldErrors(error)).length > 0) return;
          display({
            kind: 'error',
            title: 'The hydraulic hazards were not saved',
            subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
            timeout: 0,
          });
        },
      },
    );
  };

  return (
    <HazardFields
      values={values}
      shown={form.shown}
      warnings={hazardWarningsOf(values)}
      required={required}
      change={(field, value) => {
        setValues((current) => ({ ...current, [field]: value }));
        form.change(field);
      }}
      leave={form.leave}
      actions={
        // The form's actions, as the structure page's cards: small, at the right, under the fields.
        <div className="structure-detail__edit-actions">
          <Button
            onMouseDown={holdFocus}
            kind="tertiary"
            size="sm"
            onClick={onDone}
            disabled={update.isPending}
            data-testid="hazards-cancel"
          >
            Cancel
          </Button>
          <Button
            onMouseDown={holdFocus}
            kind="primary"
            size="sm"
            onClick={save}
            disabled={update.isPending}
            data-testid="hazards-save"
          >
            {update.isPending ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      }
    />
  );
};

/**
 * The Hydraulic Hazards tab — legacy's: read, with "Edit hydraulic hazards" in the card's header
 * for whoever may write inspections (Level 0 and up, legacy's `/level0Access`), until the
 * inspection is Reviewed or Accepted, as legacy disables its page then.
 */
const HazardsTab: FC<{ inspection: InspectionDetailResponse }> = ({ inspection }) => {
  const { canWriteInspection } = useAuthorization();
  const [editing, setEditing] = useState(false);
  const canEdit = canWriteInspection && !LOCKING_STATUSES.has(inspection.status.code ?? '');

  if (editing) {
    return <HazardsEditor inspection={inspection} onDone={() => setEditing(false)} />;
  }
  return (
    <HazardsCard
      inspection={inspection}
      action={
        canEdit && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            data-testid="hazards-edit"
            onClick={() => setEditing(true)}
          >
            Edit hydraulic hazards
          </Button>
        )
      }
    />
  );
};

export default HazardsTab;
