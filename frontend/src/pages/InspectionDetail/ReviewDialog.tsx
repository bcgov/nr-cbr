import {
  Button,
  Checkbox,
  Modal,
  Select,
  SelectItem,
  SkeletonText,
  Stack,
  Tag,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useMemo, useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import DateField from '@/pages/StructureDetail/DateField';
import { requiredLabel } from '@/utils/requiredLabel';

import {
  FREQUENCIES,
  OTHER_REASON,
  REASON_TEXT_MAX,
  reasonsFor,
  reviewErrorsOf,
  suggestedNextDate,
  toRating,
  yearStanding,
  type ReviewField,
  type ReviewValues,
} from './reviewRules';

import type { InspectionDetailResponse, InspectionReviewRequest } from './inspectionDetail';
import type { StructureDetailResponse } from '@/pages/StructureDetail/structureResponse';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useLoadRatingCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useStructureInspectionSchedule } from '@/hooks/useStructureSearch';
import { toIso } from '@/pages/StructureDetail/dateBox';
import { describe, number } from '@/pages/StructureDetail/format';
import { byteLength } from '@/utils/textLimits';

/** A routine inspection, whose review sets the structure's next planned one. */
const ROUTINE = 'ROUT';

type Props = {
  inspection: InspectionDetailResponse;
  structure: StructureDetailResponse | undefined;
  /** Saving the review: the Accept is pressed and its request on its way. */
  pending: boolean;
  /** What the server refused, beside each field. */
  serverErrors: Partial<Record<ReviewField, string>>;
  onAccept: (request: InspectionReviewRequest) => void;
  onCancel: () => void;
};

/** The Approaches tab's Posted Load Rating answer, as legacy's dialog shows it. */
const postedLoadRatingOf = (inspection: InspectionDetailResponse) =>
  inspection.items.find((item) => item.subsectionCode === 'APP' && item.questionCode === 'PLR')
    ?.answer ?? null;

/** A planned year, with legacy's colour for how it stands: red when overdue, yellow when due soon. */
const PlannedYear: FC<{ year: number | null; thisYear: number; testId: string }> = ({
  year,
  thisYear,
  testId,
}) => {
  if (year === null) return null;
  const standing = yearStanding(year, thisYear);
  return (
    <span className="inspection-detail__year" data-testid={testId}>
      {year}
      {standing?.kind === 'overdue' && (
        <Tag type="red" size="sm">
          {standing.years === 1 ? '1 year overdue' : `${standing.years} years overdue`}
        </Tag>
      )}
      {standing?.kind === 'soon' && (
        <Tag size="sm" className="inspection-detail__due-soon">
          Due within 5 years
        </Tag>
      )}
    </span>
  );
};

/** The dialog's form, once the structure, its schedule and the reasons are in. */
const ReviewForm: FC<
  Omit<Props, 'structure'> & {
    structure: StructureDetailResponse;
    reasons: { code: string; description: string; deltas: string[] }[];
    frequency: number | null;
  }
> = ({ inspection, structure, reasons, frequency, pending, serverErrors, onAccept, onCancel }) => {
  const current = structure.loadRating.currentRating;
  const routine = inspection.type.code === ROUTINE;
  const thisYear = new Date().getFullYear();
  const [values, setValues] = useState<ReviewValues>(() => {
    const years = frequency === null ? '' : String(frequency);
    const rating = inspection.review.loadRating ?? current;
    return {
      loadRating: rating === null ? '' : String(rating),
      reasonCode: inspection.review.loadRatingReason.code ?? '',
      reasonComment: inspection.review.loadRatingReasonComment ?? '',
      nextRoutineDate: suggestedNextDate(inspection.inspectionDate, years),
      routineFrequencyYears: years,
      loadRatingReviewRequired: structure.loadRating.reviewRequired,
    };
  });
  const rating = toRating(values.loadRating);
  const offered = useMemo(() => reasonsFor(reasons, rating, current), [reasons, rating, current]);
  const form = useFieldErrors<ReviewField>(reviewErrorsOf(values, offered, routine), serverErrors);
  const { shown } = form;

  const change = <K extends keyof ReviewValues>(field: K, value: ReviewValues[K]) => {
    setValues((previous) => ({ ...previous, [field]: value }));
    if (field !== 'loadRatingReviewRequired') form.change(field as ReviewField);
  };

  const accept = () => {
    if (!form.submit() || rating === null || Number.isNaN(rating)) return;
    onAccept({
      loadRating: rating,
      reasonCode: values.reasonCode,
      reasonComment: values.reasonCode === OTHER_REASON ? values.reasonComment : '',
      nextRoutineDate: routine ? toIso(values.nextRoutineDate) : null,
      routineFrequencyYears: Number(values.routineFrequencyYears),
      loadRatingReviewRequired: values.loadRatingReviewRequired,
    });
  };

  const { details, replacement } = structure;
  const section = (title: string, children: ReactNode) => (
    <section className="inspection-detail__dialog-section">
      <h3 className="inspection-detail__subheading">{title}</h3>
      {children}
    </section>
  );

  return (
    <>
      <Stack gap={6}>
        {section(
          'Load rating',
          <Stack gap={6}>
            <div className="structure-detail__fields">
              <ReadOnlyField
                label="Design Vehicle Configuration"
                value={describe(structure.loadRating.designVehicle)}
              />
              <ReadOnlyField
                label="Design Load Rating GVW (tonnes)"
                value={number(structure.loadRating.designLoadRating)}
              />
              <ReadOnlyField label="Structure's Current Load Rating" value={number(current)} />
              <ReadOnlyField label="Posted Load Rating" value={postedLoadRatingOf(inspection)} />
            </div>
            <div className="structure-detail__dialog-row">
              <TextInput
                id="review-loadRating"
                data-testid="review-loadRating"
                labelText={requiredLabel('Current Load Rating GVW (tonnes)', true)}
                inputMode="numeric"
                maxLength={6}
                value={values.loadRating}
                invalid={Boolean(shown.loadRating)}
                invalidText={shown.loadRating}
                onChange={(event: ChangeEvent<HTMLInputElement>) =>
                  change('loadRating', event.target.value)
                }
                onBlur={() => form.leave('loadRating')}
              />
              <Select
                id="review-reasonCode"
                data-testid="review-reasonCode"
                labelText={requiredLabel('Load Rating Reason', offered.length > 0)}
                helperText={
                  offered.length === 0 ? 'Enter the rating to see its reasons.' : undefined
                }
                value={values.reasonCode}
                disabled={offered.length === 0}
                invalid={Boolean(shown.reasonCode)}
                invalidText={shown.reasonCode}
                onChange={(event) => change('reasonCode', event.target.value)}
                onBlur={() => form.leave('reasonCode')}
              >
                <SelectItem value="" text="Choose a reason" />
                {offered.map((reason) => (
                  <SelectItem
                    key={reason.code}
                    value={reason.code}
                    text={`${reason.code} - ${reason.description}`}
                  />
                ))}
              </Select>
            </div>
            {values.reasonCode === OTHER_REASON && (
              <FieldWithCounter used={byteLength(values.reasonComment)} limit={REASON_TEXT_MAX}>
                <TextArea
                  id="review-reasonComment"
                  data-testid="review-reasonComment"
                  labelText={requiredLabel('Load Rating Reason Comment', true)}
                  rows={3}
                  value={values.reasonComment}
                  invalid={Boolean(shown.reasonComment)}
                  invalidText={shown.reasonComment}
                  onChange={(event) => change('reasonComment', event.target.value)}
                  onBlur={() => form.leave('reasonComment')}
                />
              </FieldWithCounter>
            )}
            <Checkbox
              id="review-loadRatingReviewRequired"
              data-testid="review-loadRatingReviewRequired"
              labelText="Load Rating Review Required"
              checked={values.loadRatingReviewRequired}
              onChange={(_, { checked }) => change('loadRatingReviewRequired', checked)}
            />
          </Stack>,
        )}
        {section(
          'Structure life',
          <div className="structure-detail__fields" data-testid="review-structure-life">
            <ReadOnlyField
              label="Year Installed"
              value={
                details.yearBuilt === null
                  ? null
                  : `${details.yearBuilt} · ${thisYear - details.yearBuilt} years old`
              }
            />
            <ReadOnlyField
              label="End of Design Life (Year)"
              value={
                <PlannedYear
                  year={details.endOfDesignLifeYear}
                  thisYear={thisYear}
                  testId="review-end-of-design-life"
                />
              }
            />
            <ReadOnlyField
              label="Estimated Load Restriction (Year)"
              value={
                <PlannedYear
                  year={replacement.estimatedLoadRestrictionYear}
                  thisYear={thisYear}
                  testId="review-load-restriction"
                />
              }
            />
            <ReadOnlyField
              label="Estimated Replacement (Year)"
              value={
                <PlannedYear
                  year={replacement.estimatedReplacementYear}
                  thisYear={thisYear}
                  testId="review-replacement"
                />
              }
            />
          </div>,
        )}
        {section(
          'Inspection schedule',
          <div className="structure-detail__dialog-row">
            <Select
              id="review-routineFrequencyYears"
              data-testid="review-routineFrequencyYears"
              labelText={requiredLabel('Routine Inspection Frequency (years)', true)}
              value={values.routineFrequencyYears}
              invalid={Boolean(shown.routineFrequencyYears)}
              invalidText={shown.routineFrequencyYears}
              onChange={(event) => {
                const years = event.target.value;
                change('routineFrequencyYears', years);
                // As legacy: a new frequency suggests the next routine inspection again.
                if (routine && years) {
                  change('nextRoutineDate', suggestedNextDate(inspection.inspectionDate, years));
                }
              }}
              onBlur={() => form.leave('routineFrequencyYears')}
            >
              <SelectItem value="" text="Choose a frequency" />
              {FREQUENCIES.map((years) => (
                <SelectItem key={years} value={String(years)} text={String(years)} />
              ))}
            </Select>
            {routine ? (
              <DateField
                id="review-nextRoutineDate"
                labelText={requiredLabel('Next Planned Inspection Date', true)}
                value={values.nextRoutineDate}
                invalidText={shown.nextRoutineDate}
                onChange={(value) => change('nextRoutineDate', value)}
                onBlur={() => form.leave('nextRoutineDate')}
              />
            ) : (
              // Legacy sets it only from a routine inspection's review.
              <ReadOnlyField
                label="Next Planned Inspection Date"
                value="Set by a routine inspection's review"
              />
            )}
          </div>,
        )}
      </Stack>

      <p className="inspection-detail__dialog-question">
        <strong>Do you accept these values and mark this inspection Reviewed?</strong>
      </p>
      <div className="structure-detail__dialog-actions">
        <Button
          onMouseDown={holdFocus}
          kind="tertiary"
          size="md"
          onClick={onCancel}
          disabled={pending}
          data-testid="review-cancel"
        >
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          size="md"
          onClick={accept}
          disabled={pending}
          data-testid="review-accept"
        >
          {pending ? 'Saving…' : 'Accept'}
        </Button>
      </div>
    </>
  );
};

/**
 * Legacy's Accept dialog, opened when a P.Eng saves the Inspection card as Reviewed: the structure's
 * load rating and the inspection's, the reason for any change, the structure's life, and its
 * inspection schedule — Accept marks the inspection Reviewed and writes them.
 */
const ReviewDialog: FC<Props> = ({ structure, ...rest }) => {
  const codes = useLoadRatingCodes();
  const schedule = useStructureInspectionSchedule(rest.inspection.structureId ?? undefined, true);
  const loaded = structure && codes.data && !schedule.isPending;
  return (
    <Modal
      open
      modalHeading="Review inspection"
      size="lg"
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={rest.onCancel}
      data-testid="review-dialog"
    >
      {loaded ? (
        <ReviewForm
          {...rest}
          structure={structure}
          reasons={codes.data.reasons}
          frequency={schedule.data?.routineFrequencyYears ?? null}
        />
      ) : (
        <SkeletonText paragraph lineCount={8} />
      )}
    </Modal>
  );
};

export default ReviewDialog;
