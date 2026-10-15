import { addYears, fromCalendar, toIso } from './dateBox';

import type { Messages } from './formFields';
import type { InspectionScheduleRequest } from './inspectionsResponse';

/** Legacy's frequency list: every one to six years. */
export const FREQUENCIES = ['1', '2', '3', '4', '5', '6'];

/** The schedule's values as typed: the dates are `yyyy/mm/dd` text and the frequency a select's. */
export type ScheduleValues = {
  closeProximityRequired: boolean;
  closeProximityEquipmentCode: string;
  nextCloseProximityDate: string;
  nextRoutineDate: string;
  routineFrequencyYears: string;
};

/**
 * A new structure's, as legacy's page started one: no close proximity inspection, the next routine
 * inspection a year from today, every three years.
 */
export const newScheduleValues = (): ScheduleValues => ({
  closeProximityRequired: false,
  closeProximityEquipmentCode: '',
  nextCloseProximityDate: '',
  nextRoutineDate: addYears(toIso(fromCalendar(new Date())) ?? '', 1),
  routineFrequencyYears: '3',
});

const dateError = (label: string, typed: string): string | undefined =>
  typed.trim() && toIso(typed) === null ? `${label} must be a date, as yyyy/mm/dd.` : undefined;

/**
 * The schedule's blocking rules. The frequency always; with the rest editable — Level 2 and up —
 * the dates as dates, and the next routine inspection once required.
 */
export const scheduleErrorsOf = (
  values: ScheduleValues,
  canSetAll: boolean,
  routineRequired: boolean,
): Messages<ScheduleValues> => {
  const found: Messages<ScheduleValues> = {};
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

/** The values as the server takes them. */
export const toScheduleRequest = (values: ScheduleValues): InspectionScheduleRequest => ({
  closeProximityRequired: values.closeProximityRequired,
  closeProximityEquipmentCode: values.closeProximityEquipmentCode,
  nextCloseProximityDate: toIso(values.nextCloseProximityDate),
  nextRoutineDate: toIso(values.nextRoutineDate),
  routineFrequencyYears: Number(values.routineFrequencyYears),
});
