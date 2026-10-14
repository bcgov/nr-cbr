import { Button, Modal, Select, SelectItem, Stack, TextArea, TextInput } from '@carbon/react';
import { useMemo, useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import type { LoadRatingEntry, LoadRatingRequest } from './structureResponse';
import type { ChangeEvent, FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useLoadRatingCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useCreateLoadRating, useUpdateLoadRating } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /** The manual rating being edited, or null to add one. Mounted only while open. */
  entry: LoadRatingEntry | null;
  /** The structure's current rating, which a new one is an increase, decrease or no change on. */
  current: LoadRatingEntry | null;
  onClose: () => void;
};

/** `LOAD_RATING NUMBER(6)`. */
const RATING_MAX = 999_999;
/** The reason that needs words. */
const OTHER = 'OTHR';
/** `STRCTRE_LOAD_RATING_RSN_CMNT VARCHAR2(4000)`. */
const TEXT_MAX = 4000;
/** Legacy's placeholder for a new structure, to be edited rather than added over. */
const NEW_STRUCTURE = 'NS';

type Values = { rating: string; reasonCode: string; reasonComment: string };
type Field = keyof Values;

/** A typed rating as a number, null when blank, or NaN when it is not a whole number in range. */
const toRating = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^\d+$/.test(trimmed) && Number(trimmed) <= RATING_MAX ? Number(trimmed) : Number.NaN;
};

/** Legacy's grouping of a new rating against the current one, which narrows the Add reasons. */
const deltaOf = (rating: number, current: number | null): string => {
  const change = rating - (current ?? 0);
  if (change > 0) return 'INC';
  if (change < 0) return 'DEC';
  return 'NOC';
};

/**
 * Adds a manual load rating, or changes one — legacy's Add Load Rating and Edit Manual Load Rating,
 * a P.Eng's.
 *
 * <p>As legacy, adding offers only the reasons that fit the change from the current rating —
 * increase, decrease or no change — and waits for the rating before it lists them; editing offers
 * every reason. Other needs its words. Adding is refused while the current rating is legacy's
 * 0-tonne New Structure placeholder, which is to be edited instead.
 */
const LoadRatingDialog: FC<Props> = ({ structureId, entry, current, onClose }) => {
  const codes = useLoadRatingCodes();
  const create = useCreateLoadRating(structureId);
  const update = useUpdateLoadRating(structureId);
  const adding = entry === null;
  const saving = create.isPending || update.isPending;
  const { display } = useNotification();

  const [values, setValues] = useState<Values>({
    rating: entry?.rating === null || entry === null ? '' : String(entry.rating),
    reasonCode: entry?.reason.code ?? '',
    reasonComment: entry?.reasonComment ?? '',
  });
  const rating = toRating(values.rating);

  const reasonOptions = useMemo(() => {
    const listed = codes.data?.reasons ?? [];
    if (adding) {
      if (rating === null || Number.isNaN(rating)) return [];
      const delta = deltaOf(rating, current?.rating ?? null);
      return listed.filter(
        (reason) => reason.deltas.includes(delta) || reason.deltas.includes('UNDEF'),
      );
    }
    // A stored reason since expired is still the rating's, so it stays in the list.
    const stored = entry.reason;
    return stored.code && !listed.some((reason) => reason.code === stored.code)
      ? [
          { code: stored.code, description: stored.description ?? stored.code, deltas: [] },
          ...listed,
        ]
      : listed;
  }, [codes.data, adding, rating, current, entry]);

  const placeholder =
    adding &&
    current?.status === 'MANUAL' &&
    current.rating === 0 &&
    current.reason.code === NEW_STRUCTURE;

  const errors: Partial<Record<Field, string>> = {};
  if (rating === null) errors.rating = 'Load Rating is required.';
  else if (Number.isNaN(rating)) {
    errors.rating = `Load Rating must be a whole number from 0 to ${RATING_MAX}.`;
  } else if (placeholder) {
    errors.rating = 'Edit the 0-tonne New Structure rating rather than adding another.';
  }
  if (!values.reasonCode) errors.reasonCode = 'Load Rating Reason is required.';
  else if (adding && !reasonOptions.some((reason) => reason.code === values.reasonCode)) {
    errors.reasonCode = 'Choose a reason that fits the change in load rating.';
  }
  if (values.reasonCode === OTHER) {
    if (!values.reasonComment.trim()) {
      errors.reasonComment = 'Load Rating Reason Comment is required for Other.';
    } else if (overLimitError(values.reasonComment, TEXT_MAX)) {
      errors.reasonComment = overLimitError(values.reasonComment, TEXT_MAX);
    }
  }
  // Checked as the values change; each shown once its field is left, or at Save.
  const form = useFieldErrors<Field>(errors, adding ? create.fieldErrors : update.fieldErrors);
  const shown = form.shown;

  const change = (field: Field, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit() || rating === null || Number.isNaN(rating)) return;
    const request: LoadRatingRequest = {
      rating,
      reasonCode: values.reasonCode,
      reasonComment: values.reasonCode === OTHER ? values.reasonComment : '',
    };
    const done = (title: string) => () => {
      display({ kind: 'success', title, timeout: 4000 });
      onClose();
    };
    const failed = (error: unknown) => {
      // Field refusals show beside their fields; anything else is a toast.
      if (Object.keys(apiFieldErrors(error)).length > 0) return;
      display({
        kind: 'error',
        title: adding ? 'The load rating was not added' : 'The load rating was not saved',
        subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
        timeout: 0,
      });
    };
    if (entry === null) {
      create.mutate(request, { onSuccess: done('Load rating added'), onError: failed });
    } else {
      update.mutate(
        { loadRatingId: entry.id, request },
        { onSuccess: done('Load rating saved'), onError: failed },
      );
    }
  };

  return (
    <Modal
      open
      modalHeading={adding ? 'Add load rating' : 'Edit manual load rating'}
      size="sm"
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="load-rating-dialog"
    >
      <Stack gap={6}>
        <div className="structure-detail__dialog-row">
          <TextInput
            id="load-rating-value"
            data-testid="load-rating-value"
            labelText={requiredLabel('Load Rating GVW (tonnes)', true)}
            inputMode="numeric"
            maxLength={6}
            value={values.rating}
            invalid={Boolean(shown.rating)}
            invalidText={shown.rating}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              change('rating', event.target.value)
            }
            onBlur={() => form.leave('rating')}
          />
          <Select
            id="load-rating-reason"
            data-testid="load-rating-reason"
            labelText={requiredLabel('Load Rating Reason', true)}
            helperText={
              adding && reasonOptions.length === 0
                ? 'Enter the rating to see its reasons.'
                : undefined
            }
            value={values.reasonCode}
            disabled={codes.isLoading || reasonOptions.length === 0}
            invalid={Boolean(shown.reasonCode)}
            invalidText={shown.reasonCode}
            onChange={(event) => change('reasonCode', event.target.value)}
            onBlur={() => form.leave('reasonCode')}
          >
            <SelectItem value="" text="Choose a reason" />
            {reasonOptions.map((reason) => (
              <SelectItem
                key={reason.code}
                value={reason.code}
                text={`${reason.code} - ${reason.description}`}
              />
            ))}
          </Select>
        </div>
        {values.reasonCode === OTHER && (
          <FieldWithCounter used={byteLength(values.reasonComment)} limit={TEXT_MAX}>
            <TextArea
              id="load-rating-comment"
              data-testid="load-rating-comment"
              labelText={requiredLabel('Load Rating Reason Comment', true)}
              rows={4}
              value={values.reasonComment}
              invalid={Boolean(shown.reasonComment)}
              invalidText={shown.reasonComment}
              onChange={(event) => change('reasonComment', event.target.value)}
              onBlur={() => form.leave('reasonComment')}
            />
          </FieldWithCounter>
        )}
      </Stack>

      <div className="structure-detail__dialog-actions">
        <Button
          kind="tertiary"
          size="md"
          onMouseDown={holdFocus}
          onClick={onClose}
          disabled={saving}
        >
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          onMouseDown={holdFocus}
          onClick={save}
          disabled={saving}
          data-testid="load-rating-save"
        >
          {adding ? 'Add load rating' : 'Save changes'}
        </Button>
      </div>
    </Modal>
  );
};

export default LoadRatingDialog;
