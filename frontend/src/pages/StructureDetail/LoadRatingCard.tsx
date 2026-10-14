import { Add, Edit, Scales, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useState } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';
import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import Card from './Card';
import CodeWithOther from './CodeWithOther';
import { describe, number, yesNo } from './format';
import LoadRatingDialog from './LoadRatingDialog';

import type {
  LoadRatingDetails,
  LoadRatingDetailsRequest,
  LoadRatingEntry,
} from './structureResponse';
import type { ChangeEvent, FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useLoadRatingCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useDeleteLoadRating, useUpdateLoadRatingDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';
import { byteLength, overLimitError } from '@/utils/textLimits';

/** The vehicle that needs words. */
const OTHER_VEHICLE = 'OTHER';
/** `DESIGN_LOAD_RATING NUMBER(6)`. */
const RATING_MAX = 999_999;
/** `DESIGN_VEHICLE_CMT VARCHAR2(2000)`. */
const TEXT_MAX = 2000;

/**
 * Whether a load rating predates the superstructure — legacy hides those unless asked
 * (`StructureAction.java:1999-2020`): a rating from before 1 January of the year it was installed.
 */
const beforeInstall = (entry: LoadRatingEntry, yearBuilt: number | null): boolean =>
  yearBuilt !== null && entry.date !== null && entry.date < `${yearBuilt}-01-01`;

/**
 * Legacy's advice on Load Rating Review Required (`StructureAction.java:716-738`), which never
 * stopped its save: an error when there is no rating and no review asked for, a warning when there
 * is a rating and a review is still asked for.
 */
const reviewAdvice = (reviewRequired: boolean, hasRatings: boolean) =>
  !reviewRequired && !hasRatings
    ? {
        invalid: true,
        invalidText: 'Required field: with no load rating, a review should be required.',
      }
    : reviewRequired && hasRatings
      ? { warn: true, warnText: 'Load rating review is required although a rating is recorded.' }
      : {};

type Values = {
  loadPostingSignsCode: string;
  reviewRequired: boolean;
  designVehicleCode: string;
  designVehicleComment: string;
  designLoadRating: string;
};
type Field = keyof Values;

/**
 * The section's four fields as a form, in place of the view, every field where the view has it.
 *
 * <p>As legacy, by role: Level 1 changes Load Rating Review Required alone; Level 2 the posting
 * signs, design vehicle and a typed design rating too; the Other vehicle's words a P.Eng only — a
 * Level 2 user who picks Other cannot save it, as in legacy. A vehicle that fixes a design load
 * rating sets it; otherwise it is typed.
 */
const DetailsForm: FC<{
  structureId: string;
  loadRating: LoadRatingDetails;
  onDone: () => void;
}> = ({ structureId, loadRating, onDone }) => {
  const { canDelete: level2, isPeng } = useAuthorization();
  const codes = useLoadRatingCodes();
  const update = useUpdateLoadRatingDetails(structureId);
  const { display } = useNotification();

  const [values, setValues] = useState<Values>({
    loadPostingSignsCode: loadRating.loadPostingSigns.code ?? '',
    reviewRequired: loadRating.reviewRequired,
    designVehicleCode: loadRating.designVehicle.code ?? '',
    designVehicleComment: loadRating.designVehicleComment ?? '',
    designLoadRating: number(loadRating.designLoadRating),
  });
  const vehicles = codes.data?.designVehicles ?? [];
  const chosen = vehicles.find((vehicle) => vehicle.code === values.designVehicleCode);
  // Choosing a vehicle that fixes the design rating sets it; one left as it was keeps the rating
  // stored, as legacy's page. A vehicle that fixes none leaves it to be typed.
  const vehicleChanged = values.designVehicleCode !== (loadRating.designVehicle.code ?? '');
  const fixed =
    chosen?.designLoadRating === null || chosen?.designLoadRating === undefined
      ? null
      : vehicleChanged
        ? chosen.designLoadRating
        : loadRating.designLoadRating;
  const typedRating = chosen === undefined || chosen.designLoadRating === null;
  const other = values.designVehicleCode === OTHER_VEHICLE;

  const errors: Partial<Record<Field, string>> = {};
  if (!values.loadPostingSignsCode) {
    errors.loadPostingSignsCode = 'Are Load Posting Signs Warranted? is required.';
  }
  if (other) {
    if (!values.designVehicleComment.trim()) {
      errors.designVehicleComment = 'Design Vehicle Configuration Details is required.';
    } else if (overLimitError(values.designVehicleComment, TEXT_MAX)) {
      errors.designVehicleComment = overLimitError(values.designVehicleComment, TEXT_MAX);
    }
  }
  if (typedRating && values.designLoadRating.trim()) {
    const typed = values.designLoadRating.trim();
    if (!/^\d+$/.test(typed) || Number(typed) > RATING_MAX) {
      errors.designLoadRating = `Original Design Load Rating must be a whole number up to ${RATING_MAX}.`;
    } else if (Number(typed) < 1) {
      errors.designLoadRating =
        'Original Design Load Rating GVW (tonnes) must be greater than or equal to 1.';
    }
  }
  // Checked as the values change; each shown once its field is left, or at Save.
  const form = useFieldErrors<Field>(errors, update.fieldErrors);
  const shown = form.shown;

  const change = <K extends Field>(field: K, value: Values[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    const typed = values.designLoadRating.trim();
    const request: LoadRatingDetailsRequest = {
      ...values,
      designVehicleComment: other ? values.designVehicleComment : '',
      designLoadRating: typedRating ? (typed ? Number(typed) : null) : fixed,
    };
    update.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Load rating details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The load rating details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  const hasRatings = loadRating.history.some((entry) => entry.rating !== null);

  return (
    <div className="structure-detail__edit" data-testid="load-rating-details-form">
      <div className="structure-detail__fields">
        <ReadOnlyField
          label="Current Load Rating GVW (tonnes)"
          value={number(loadRating.currentRating)}
        />
        {/* Set from the vehicle when it fixes one; typed, at Level 2, when it does not. */}
        {!typedRating || !level2 ? (
          <ReadOnlyField
            label="Original Design Load Rating GVW (tonnes)"
            value={typedRating ? values.designLoadRating : number(fixed)}
          />
        ) : (
          <TextInput
            id="load-rating-design"
            data-testid="load-rating-design"
            labelText="Original Design Load Rating GVW (tonnes)"
            inputMode="numeric"
            maxLength={6}
            value={values.designLoadRating}
            invalid={Boolean(shown.designLoadRating)}
            invalidText={shown.designLoadRating}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              change('designLoadRating', event.target.value)
            }
            onBlur={() => form.leave('designLoadRating')}
          />
        )}
        {level2 ? (
          <Select
            id="load-rating-posting"
            data-testid="load-rating-posting"
            labelText={requiredLabel('Are Load Posting Signs Warranted?', true)}
            value={values.loadPostingSignsCode}
            disabled={codes.isLoading}
            invalid={Boolean(shown.loadPostingSignsCode)}
            invalidText={shown.loadPostingSignsCode}
            onChange={(event) => change('loadPostingSignsCode', event.target.value)}
            onBlur={() => form.leave('loadPostingSignsCode')}
          >
            <SelectItem value="" text="Choose an answer" />
            {(codes.data?.loadPostingSigns ?? []).map((option) => (
              <SelectItem key={option.code} value={option.code} text={option.description} />
            ))}
          </Select>
        ) : (
          <ReadOnlyField
            label="Are Load Posting Signs Warranted?"
            value={describe(loadRating.loadPostingSigns)}
          />
        )}
        <RadioButtonGroup
          name="load-rating-review"
          legendText={requiredLabel('Load Rating Review Required', true)}
          valueSelected={values.reviewRequired ? 'Y' : 'N'}
          onChange={(selected) => change('reviewRequired', selected === 'Y')}
          {...reviewAdvice(values.reviewRequired, hasRatings)}
        >
          <RadioButton id="load-rating-review-yes" labelText="Yes" value="Y" />
          <RadioButton id="load-rating-review-no" labelText="No" value="N" />
        </RadioButtonGroup>
        {level2 ? (
          <Select
            id="load-rating-vehicle"
            data-testid="load-rating-vehicle"
            labelText="Design Vehicle Configuration"
            value={values.designVehicleCode}
            disabled={codes.isLoading}
            invalid={Boolean(shown.designVehicleCode)}
            invalidText={shown.designVehicleCode}
            onChange={(event) => change('designVehicleCode', event.target.value)}
            onBlur={() => form.leave('designVehicleCode')}
          >
            <SelectItem value="" text="None" />
            {vehicles.map((option) => (
              <SelectItem key={option.code} value={option.code} text={option.description} />
            ))}
          </Select>
        ) : (
          <ReadOnlyField
            label="Design Vehicle Configuration"
            value={describe(loadRating.designVehicle)}
          />
        )}
        {other &&
          (isPeng ? (
            <FieldWithCounter used={byteLength(values.designVehicleComment)} limit={TEXT_MAX}>
              <TextArea
                id="load-rating-vehicle-comment"
                data-testid="load-rating-vehicle-comment"
                labelText={requiredLabel('If Other, please specify', true)}
                rows={2}
                value={values.designVehicleComment}
                invalid={Boolean(shown.designVehicleComment)}
                invalidText={shown.designVehicleComment}
                onChange={(event) => change('designVehicleComment', event.target.value)}
                onBlur={() => form.leave('designVehicleComment')}
              />
            </FieldWithCounter>
          ) : (
            // A P.Eng's words, as legacy: shown, and their absence refused on Save.
            <div>
              <ReadOnlyField label="If Other, please specify" value={values.designVehicleComment} />
              {shown.designVehicleComment && (
                <p className="structure-detail__upload-error" role="alert">
                  {shown.designVehicleComment} Only a P.Eng can enter them.
                </p>
              )}
            </div>
          ))}
      </div>

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
          data-testid="load-rating-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

type Props = {
  structureId: string;
  loadRating: LoadRatingDetails;
  /** Year Superstructure Installed — ratings from before it are hidden unless asked for. */
  yearBuilt: number | null;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/**
 * The Details tab's Load Rating Details — legacy's fieldset: the four fields and the rating
 * history, newest first, the current rating marked.
 *
 * <p>"Edit load rating details" (Level 1 and up) edits the fields in place. A P.Eng adds a manual
 * rating with "Add load rating", and edits or deletes one from its row; a rating an inspection
 * brought is changed through its inspection. Each is written at once — legacy held them until its
 * page Save.
 */
const LoadRatingCard: FC<Props> = ({
  structureId,
  loadRating,
  yearBuilt,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canEdit, isPeng } = useAuthorization();
  const { display } = useNotification();
  const [showEarlyRatings, setShowEarlyRatings] = useState(false);
  /** The open rating dialog, if any: a manual rating to edit, or `'new'` to add one. */
  const [dialog, setDialog] = useState<LoadRatingEntry | 'new' | null>(null);
  const [pendingDelete, setPendingDelete] = useState<LoadRatingEntry | null>(null);
  const deleteRating = useDeleteLoadRating(structureId);

  const hiddenRatings = loadRating.history.filter((entry) =>
    beforeInstall(entry, yearBuilt),
  ).length;
  const ratings = showEarlyRatings
    ? loadRating.history
    : loadRating.history.filter((entry) => !beforeInstall(entry, yearBuilt));
  const current = loadRating.history.find((entry) => entry.current) ?? null;

  return (
    <Card
      title="Load Rating Details"
      icon={Scales}
      testId="structure-section-load-rating"
      action={
        !editing && (
          <div className="structure-detail__card-actions">
            {isPeng && (
              <Button
                kind="tertiary"
                size="sm"
                renderIcon={Add}
                disabled={otherEditing}
                data-testid="load-rating-add"
                onClick={() => setDialog('new')}
              >
                Add load rating
              </Button>
            )}
            {canEdit && (
              <Button
                kind="tertiary"
                size="sm"
                renderIcon={Edit}
                disabled={otherEditing}
                data-testid="load-rating-details-edit"
                onClick={onEdit}
              >
                Edit load rating details
              </Button>
            )}
          </div>
        )
      }
    >
      {editing ? (
        <DetailsForm structureId={structureId} loadRating={loadRating} onDone={onDone} />
      ) : (
        <div className="structure-detail__fields">
          <ReadOnlyField
            label="Current Load Rating GVW (tonnes)"
            value={number(loadRating.currentRating)}
          />
          <ReadOnlyField
            label="Original Design Load Rating GVW (tonnes)"
            value={number(loadRating.designLoadRating)}
          />
          <ReadOnlyField
            label="Are Load Posting Signs Warranted?"
            value={describe(loadRating.loadPostingSigns)}
          />
          <ReadOnlyField
            label="Load Rating Review Required"
            value={yesNo(loadRating.reviewRequired)}
          />
          <CodeWithOther
            label="Design Vehicle Configuration"
            value={loadRating.designVehicle}
            comment={loadRating.designVehicleComment}
          />
        </div>
      )}

      {loadRating.currentRating !== null &&
        loadRating.designLoadRating !== null &&
        loadRating.designLoadRating > loadRating.currentRating && (
          <p className="structure-detail__note" data-testid="structure-downrated">
            The current load rating is less than the design load rating.
          </p>
        )}

      {loadRating.history.length === 0 ? (
        <p className="structure-detail__empty">No load ratings.</p>
      ) : (
        <div className="structure-detail__history" data-testid="load-rating-history">
          {hiddenRatings > 0 && (
            <div className="structure-detail__history-toggle">
              <Checkbox
                id="structure-show-early-ratings"
                labelText={`Show ratings from before the superstructure was installed (${hiddenRatings})`}
                checked={showEarlyRatings}
                onChange={(_, { checked }) => setShowEarlyRatings(checked)}
              />
            </div>
          )}
          <Table size="md" useZebraStyles aria-label="Load rating history">
            <TableHead>
              <TableRow>
                <TableHeader className="structure-detail__nowrap">
                  Load Rating GVW (tonnes)
                </TableHeader>
                <TableHeader>Reason</TableHeader>
                <TableHeader className="structure-detail__nowrap">Date</TableHeader>
                <TableHeader className="structure-detail__nowrap">IDIR ID</TableHeader>
                <TableHeader className="structure-detail__nowrap">Reviewed Date</TableHeader>
                <TableHeader className="structure-detail__nowrap">Status</TableHeader>
                {isPeng && <TableHeader>Actions</TableHeader>}
              </TableRow>
            </TableHead>
            <TableBody>
              {ratings.map((entry) => (
                <TableRow
                  key={entry.id}
                  className={entry.current ? 'structure-detail__current-rating' : undefined}
                  data-testid={`load-rating-${entry.id}`}
                >
                  <TableCell className="structure-detail__nowrap">
                    {entry.rating === null ? 'Not recorded' : entry.rating}
                    {entry.current && (
                      <Tag type="blue" size="sm" className="structure-detail__current-tag">
                        Current
                      </Tag>
                    )}
                  </TableCell>
                  <TableCell>
                    {[entry.reason.code, entry.reason.description].filter(Boolean).join(' - ')}
                  </TableCell>
                  <TableCell className="structure-detail__nowrap">
                    {formatShortDate(entry.date)}
                  </TableCell>
                  <TableCell className="structure-detail__nowrap">{entry.userId}</TableCell>
                  <TableCell className="structure-detail__nowrap">
                    {formatShortDate(entry.reviewedDate)}
                  </TableCell>
                  <TableCell className="structure-detail__nowrap">
                    {entry.status === 'MANUAL' ? 'Manual' : 'Reviewed'}
                  </TableCell>
                  {isPeng && (
                    <TableCell className="structure-detail__nowrap">
                      {/* A manual rating only: one an inspection brought is changed there. */}
                      {entry.status === 'MANUAL' && (
                        <>
                          <Button
                            kind="ghost"
                            size="sm"
                            renderIcon={Edit}
                            data-testid={`load-rating-edit-${entry.id}`}
                            onClick={() => setDialog(entry)}
                          >
                            Edit <span className="cds--visually-hidden">load rating</span>
                          </Button>
                          <Button
                            kind="danger--ghost"
                            size="sm"
                            renderIcon={TrashCan}
                            data-testid={`load-rating-delete-${entry.id}`}
                            onClick={() => setPendingDelete(entry)}
                          >
                            Delete <span className="cds--visually-hidden">load rating</span>
                          </Button>
                        </>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {dialog !== null && (
        <LoadRatingDialog
          key={dialog === 'new' ? 'new' : dialog.id}
          structureId={structureId}
          entry={dialog === 'new' ? null : dialog}
          current={current}
          onClose={() => setDialog(null)}
        />
      )}

      {/* Asked first, as the app's other deletes. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete load rating"
        message={`Are you sure you would like to delete the ${number(
          pendingDelete?.rating,
        )}-tonne load rating? This cannot be undone.`}
        confirmButtonText="Delete"
        loading={deleteRating.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const target = pendingDelete;
          if (target === null) return;
          deleteRating.mutate(target.id, {
            onSuccess: () => {
              setPendingDelete(null);
              display({ kind: 'success', title: 'Load rating deleted', timeout: 4000 });
            },
            onError: (error) => {
              setPendingDelete(null);
              display({
                kind: 'error',
                title: 'The load rating was not deleted',
                subtitle: apiErrorMessage(
                  error,
                  'Try again, or contact support if this continues.',
                ),
                timeout: 0,
              });
            },
          });
        }}
      />
    </Card>
  );
};

export default LoadRatingCard;
