import { Add, Edit, Scales, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { useState } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';
import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import CodeWithOther from './CodeWithOther';
import { describe, number, yesNo } from './format';
import LoadRatingDialog from './LoadRatingDialog';
import LoadRatingFields from './LoadRatingFields';
import {
  designRatingOf,
  loadRatingErrorsOf,
  toLoadRatingRequest,
  type LoadRatingValues,
} from './loadRatingRules';

import type { LoadRatingDetails, LoadRatingEntry } from './structureResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useLoadRatingCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useDeleteLoadRating, useUpdateLoadRatingDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

/**
 * Whether a load rating predates the superstructure — legacy hides those unless asked
 * (`StructureAction.java:1999-2020`): a rating from before 1 January of the year it was installed.
 */
const beforeInstall = (entry: LoadRatingEntry, yearBuilt: number | null): boolean =>
  yearBuilt !== null && entry.date !== null && entry.date < `${yearBuilt}-01-01`;

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
  const codes = useLoadRatingCodes();
  const update = useUpdateLoadRatingDetails(structureId);
  const { display } = useNotification();

  const [values, setValues] = useState<LoadRatingValues>({
    loadPostingSignsCode: loadRating.loadPostingSigns.code ?? '',
    reviewRequired: loadRating.reviewRequired,
    designVehicleCode: loadRating.designVehicle.code ?? '',
    designVehicleComment: loadRating.designVehicleComment ?? '',
    designLoadRating: number(loadRating.designLoadRating),
  });
  const design = designRatingOf(
    values,
    codes.data?.designVehicles ?? [],
    loadRating.designVehicle.code ?? '',
    loadRating.designLoadRating,
  );

  // Checked as the values change; each shown once its field is left, or at Save.
  const form = useFieldErrors<keyof LoadRatingValues>(
    loadRatingErrorsOf(values, design),
    update.fieldErrors,
  );

  const change = <K extends keyof LoadRatingValues>(field: K, value: LoadRatingValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    update.mutate(toLoadRatingRequest(values, design), {
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

  return (
    <div className="structure-detail__edit" data-testid="load-rating-details-form">
      <LoadRatingFields
        values={values}
        shown={form.shown}
        change={change}
        leave={form.leave}
        design={design}
        hasRatings={loadRating.history.some((entry) => entry.rating !== null)}
        stored={{
          loadPostingSigns: loadRating.loadPostingSigns,
          designVehicle: loadRating.designVehicle,
        }}
        leading={
          <ReadOnlyField
            label="Current Load Rating GVW (tonnes)"
            value={number(loadRating.currentRating)}
          />
        }
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
                    {entry.rating ?? 'Not recorded'}
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
