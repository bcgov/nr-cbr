import { Calendar, Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import { addYears, toBox, toIso } from './dateBox';
import { describe, number, yesNo } from './format';
import ScheduleFields, { FrequencySelect } from './ScheduleFields';
import { scheduleErrorsOf, toScheduleRequest, type ScheduleValues } from './scheduleRules';

import type { InspectionScheduleResponse } from './inspectionsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateInspectionSchedule } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

type Props = {
  structureId: string;
  schedule: InspectionScheduleResponse;
  /** True while this card is being edited. */
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/** What the card shows when it is not being edited. */
const ScheduleView: FC<{ schedule: InspectionScheduleResponse }> = ({ schedule }) => (
  <div className="structure-detail__fields">
    <ReadOnlyField
      label="Close Proximity Inspection Required?"
      value={yesNo(schedule.closeProximityRequired)}
    />
    {/* Legacy shows these two only when a close proximity inspection is required. */}
    {schedule.closeProximityRequired && (
      <>
        <ReadOnlyField
          label="Close Proximity Special Equipment Requirements"
          value={describe(schedule.closeProximityEquipment)}
        />
        <ReadOnlyField
          label="Next Planned Close Proximity Inspection"
          value={formatShortDate(schedule.nextCloseProximityDate)}
        />
      </>
    )}
    <ReadOnlyField
      label="Next Planned Routine Inspection"
      value={formatShortDate(schedule.nextRoutineDate)}
    />
    <ReadOnlyField
      label="Routine Inspection Frequency (years)"
      value={number(schedule.routineFrequencyYears)}
    />
  </div>
);

/**
 * The card's values as a form, in place of the view.
 *
 * <p>Grey fields in sized cells on the white card, required ones marked with a red asterisk, and
 * the yes/no question as a radio pair. Each error shows once its field is left, then as it is
 * corrected.
 *
 * <p>As legacy, by role: Level 2 and up change all of it; Level 1 the frequency alone, the rest
 * shown read-only. A new frequency moves the next planned routine inspection to the latest reviewed
 * or accepted inspection plus that many years, as legacy's page did; a Level 2 user may change the
 * answer, and with no such inspection the date stays. Unticking close proximity hides its equipment
 * and date and keeps them, as legacy's hidden fields did.
 */
const ScheduleForm: FC<{
  structureId: string;
  schedule: InspectionScheduleResponse;
  onDone: () => void;
}> = ({ structureId, schedule, onDone }) => {
  // Level 2 and up — legacy's /level2Access on these fields.
  const { canDelete: canSetAll } = useAuthorization();
  const update = useUpdateInspectionSchedule(structureId);
  const { display } = useNotification();

  const [values, setValues] = useState<ScheduleValues>({
    closeProximityRequired: schedule.closeProximityRequired,
    closeProximityEquipmentCode: schedule.closeProximityEquipment.code ?? '',
    nextCloseProximityDate: toBox(schedule.nextCloseProximityDate),
    nextRoutineDate: toBox(schedule.nextRoutineDate),
    routineFrequencyYears: number(schedule.routineFrequencyYears),
  });

  const routineRequired = schedule.nextRoutineDate !== null;

  const change = <K extends keyof ScheduleValues>(field: K, value: ScheduleValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const changeFrequency = (frequency: string) => {
    change('routineFrequencyYears', frequency);
    const latest = schedule.latestReviewedInspectionDate;
    if (latest && frequency) {
      change('nextRoutineDate', addYears(latest, Number(frequency)));
    }
  };

  const form = useFieldErrors<keyof ScheduleValues>(
    scheduleErrorsOf(values, canSetAll, routineRequired),
    update.fieldErrors,
  );
  const shown = form.shown;

  const save = () => {
    if (!form.submit()) return;
    update.mutate(toScheduleRequest(values), {
      onSuccess: () => {
        display({ kind: 'success', title: 'Inspection schedule saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The inspection schedule was not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  return (
    <div className="structure-detail__edit" data-testid="schedule-form">
      {canSetAll ? (
        <ScheduleFields
          values={values}
          shown={shown}
          change={change}
          leave={form.leave}
          routineRequired={routineRequired}
          onFrequency={changeFrequency}
        />
      ) : (
        <>
          <div className="structure-detail__fields">
            <ReadOnlyField
              label="Close Proximity Inspection Required?"
              value={yesNo(schedule.closeProximityRequired)}
            />
            {schedule.closeProximityRequired && (
              <>
                <ReadOnlyField
                  label="Close Proximity Special Equipment Requirements"
                  value={describe(schedule.closeProximityEquipment)}
                />
                <ReadOnlyField
                  label="Next Planned Close Proximity Inspection"
                  value={formatShortDate(schedule.nextCloseProximityDate)}
                />
              </>
            )}
            <ReadOnlyField
              label="Next Planned Routine Inspection"
              value={formatShortDate(toIso(values.nextRoutineDate))}
            />
          </div>
          <div className="structure-detail__edit-row">
            <div className="structure-detail__edit-cell">
              <FrequencySelect
                value={values.routineFrequencyYears}
                shown={shown}
                onChange={changeFrequency}
                onBlur={() => form.leave('routineFrequencyYears')}
              />
            </div>
          </div>
        </>
      )}

      {/* The form's actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button
          onMouseDown={holdFocus}
          kind="tertiary"
          size="sm"
          onClick={onDone}
          disabled={update.isPending}
        >
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          size="sm"
          onClick={save}
          disabled={update.isPending}
          data-testid="schedule-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

/**
 * When the structure is next to be inspected, and how often — legacy's fields above its table —
 * with its own "Edit inspection schedule", for Level 1 and up (legacy's page
 * Save). The edit replaces the values in place.
 */
const ScheduleCard: FC<Props> = ({
  structureId,
  schedule,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canEdit } = useAuthorization();
  return (
    <Card
      title="Inspection Schedule"
      icon={Calendar}
      testId="structure-section-schedule"
      action={
        canEdit &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="schedule-edit"
            onClick={onEdit}
          >
            Edit inspection schedule
          </Button>
        )
      }
    >
      {editing ? (
        <ScheduleForm structureId={structureId} schedule={schedule} onDone={onDone} />
      ) : (
        <ScheduleView schedule={schedule} />
      )}
    </Card>
  );
};

export default ScheduleCard;
