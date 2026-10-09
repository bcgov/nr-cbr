import { Calendar, Edit } from '@carbon/icons-react';
import { Button, RadioButton, RadioButtonGroup, Select, SelectItem } from '@carbon/react';
import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import { addYears, toBox, toIso } from './dateBox';
import DateField from './DateField';
import { describe, number, yesNo } from './format';

import type { InspectionScheduleRequest, InspectionScheduleResponse } from './inspectionsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useSpecialEquipmentCodes } from '@/hooks/useConfiguration';
import { useUpdateInspectionSchedule } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

/** Legacy's frequency list: every one to six years. */
const FREQUENCIES = ['1', '2', '3', '4', '5', '6'];

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

/** The form's values as typed: the dates are `yyyy/mm/dd` text and the frequency a select's. */
type Values = {
  closeProximityRequired: boolean;
  closeProximityEquipmentCode: string;
  nextCloseProximityDate: string;
  nextRoutineDate: string;
  routineFrequencyYears: string;
};

type Errors = Partial<Record<keyof Values, string>>;

const dateError = (label: string, typed: string): string | undefined =>
  typed.trim() && toIso(typed) === null ? `${label} must be a date, as yyyy/mm/dd.` : undefined;

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
 * The card's values as a form, in place of the view — nr-fspts' section edit.
 *
 * <p>Laid out as nr-fspts' Plan details edit: grey fields in sized cells on the white card, "All
 * fields are required unless marked optional." over them, and the yes/no question as a radio pair.
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
  const equipment = useSpecialEquipmentCodes();
  const update = useUpdateInspectionSchedule(structureId);
  const { display } = useNotification();

  const [values, setValues] = useState<Values>({
    closeProximityRequired: schedule.closeProximityRequired,
    closeProximityEquipmentCode: schedule.closeProximityEquipment.code ?? '',
    nextCloseProximityDate: toBox(schedule.nextCloseProximityDate),
    nextRoutineDate: toBox(schedule.nextRoutineDate),
    routineFrequencyYears: number(schedule.routineFrequencyYears),
  });
  /** The form's own errors, set on Save and cleared field by field as each is changed. */
  const [errors, setErrors] = useState<Errors>({});
  const shown: Errors = { ...update.fieldErrors, ...errors };
  // Legacy's rule: required once the structure has one.
  const routineRequired = schedule.nextRoutineDate !== null;

  const change = <K extends keyof Values>(field: K, value: Values[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const changeFrequency = (frequency: string) => {
    change('routineFrequencyYears', frequency);
    const latest = schedule.latestReviewedInspectionDate;
    if (latest && frequency) {
      change('nextRoutineDate', addYears(latest, Number(frequency)));
    }
  };

  const validate = (): Errors => {
    const found: Errors = {};
    if (!values.routineFrequencyYears) {
      found.routineFrequencyYears = 'Routine Inspection Frequency is required.';
    }
    if (!canSetAll) return found;
    found.nextRoutineDate = dateError('Next Planned Routine Inspection', values.nextRoutineDate);
    if (routineRequired && !values.nextRoutineDate.trim()) {
      found.nextRoutineDate = 'Next Planned Routine Inspection is required.';
    }
    if (values.closeProximityRequired) {
      found.nextCloseProximityDate = dateError(
        'Next Planned Close Proximity Inspection',
        values.nextCloseProximityDate,
      );
    }
    return found;
  };

  const save = () => {
    const found = validate();
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;
    const request: InspectionScheduleRequest = {
      closeProximityRequired: values.closeProximityRequired,
      closeProximityEquipmentCode: values.closeProximityEquipmentCode,
      nextCloseProximityDate: toIso(values.nextCloseProximityDate),
      nextRoutineDate: toIso(values.nextRoutineDate),
      routineFrequencyYears: Number(values.routineFrequencyYears),
    };
    update.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Inspection schedule saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
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

  const frequency = (
    <Select
      id="schedule-frequency"
      data-testid="schedule-frequency"
      labelText="Routine Inspection Frequency (years)"
      value={values.routineFrequencyYears}
      invalid={Boolean(shown.routineFrequencyYears)}
      invalidText={shown.routineFrequencyYears}
      onChange={(event) => changeFrequency(event.target.value)}
    >
      {!values.routineFrequencyYears && <SelectItem value="" text="Choose a frequency" />}
      {FREQUENCIES.map((years) => (
        <SelectItem key={years} value={years} text={years} />
      ))}
    </Select>
  );

  return (
    <div className="structure-detail__edit" data-testid="schedule-form">
      <p className="structure-detail__edit-intro">
        All fields are required unless marked optional.
      </p>
      {canSetAll ? (
        <>
          <RadioButtonGroup
            name="schedule-close-proximity"
            legendText="Is a close proximity inspection required?"
            valueSelected={values.closeProximityRequired ? 'Y' : 'N'}
            onChange={(selected) => change('closeProximityRequired', selected === 'Y')}
          >
            <RadioButton id="schedule-close-proximity-yes" labelText="Yes" value="Y" />
            <RadioButton id="schedule-close-proximity-no" labelText="No" value="N" />
          </RadioButtonGroup>
          {values.closeProximityRequired && (
            <div className="structure-detail__edit-row">
              <div className="structure-detail__edit-cell">
                <Select
                  id="schedule-equipment"
                  data-testid="schedule-equipment"
                  labelText="Close Proximity Special Equipment Requirements (optional)"
                  value={values.closeProximityEquipmentCode}
                  disabled={equipment.isLoading}
                  invalid={Boolean(shown.closeProximityEquipmentCode)}
                  invalidText={shown.closeProximityEquipmentCode}
                  onChange={(event) => change('closeProximityEquipmentCode', event.target.value)}
                >
                  <SelectItem value="" text="None" />
                  {(equipment.data ?? []).map((option) => (
                    <SelectItem key={option.code} value={option.code} text={option.description} />
                  ))}
                </Select>
              </div>
              <div className="structure-detail__edit-cell">
                <DateField
                  id="schedule-next-close-proximity"
                  labelText="Next Planned Close Proximity Inspection (optional)"
                  value={values.nextCloseProximityDate}
                  invalidText={shown.nextCloseProximityDate}
                  onChange={(value) => change('nextCloseProximityDate', value)}
                />
              </div>
            </div>
          )}
          <div className="structure-detail__edit-row">
            <div className="structure-detail__edit-cell">
              <DateField
                id="schedule-next-routine"
                labelText={
                  routineRequired
                    ? 'Next Planned Routine Inspection'
                    : 'Next Planned Routine Inspection (optional)'
                }
                value={values.nextRoutineDate}
                invalidText={shown.nextRoutineDate}
                onChange={(value) => change('nextRoutineDate', value)}
              />
            </div>
            <div className="structure-detail__edit-cell">{frequency}</div>
          </div>
        </>
      ) : (
        // Level 1: the frequency alone, as legacy's page left only it open to them; the date it
        // moves is shown as it will be saved.
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
            <div className="structure-detail__edit-cell">{frequency}</div>
          </div>
        </>
      )}

      {/* nr-fspts' form actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button kind="tertiary" size="sm" onClick={onDone} disabled={update.isPending}>
          Cancel
        </Button>
        <Button
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
 * with its own "Edit inspection schedule", as nr-fspts' sections, for Level 1 and up (legacy's page
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
