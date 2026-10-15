import {
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import { describe, number } from './format';
import {
  TEXT_MAX,
  reviewAdvice,
  type DesignRating,
  type LoadRatingValues,
} from './loadRatingRules';

import type { FieldsProps } from './formFields';
import type { CodeValue } from './structureResponse';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';
import { useLoadRatingCodes } from '@/hooks/useConfiguration';
import { byteLength } from '@/utils/textLimits';

type Props = FieldsProps<LoadRatingValues> & {
  design: DesignRating;
  /** Whether a rating is recorded — what legacy's Load Rating Review Required advice turns on. */
  hasRatings: boolean;
  /** What a user below Level 2 sees in place of the posting signs and vehicle lists. */
  stored?: { loadPostingSigns?: CodeValue; designVehicle?: CodeValue };
  /** Fields shown first in the grid — the card's Current Load Rating. */
  leading?: ReactNode;
};

/**
 * Load Rating Details' four fields, for the card's edit and for Add Structure.
 *
 * <p>As legacy, by role: Level 1 changes Load Rating Review Required alone; Level 2 the posting
 * signs, design vehicle and a typed design rating too; the Other vehicle's words a P.Eng only — a
 * Level 2 user who picks Other cannot save it, as in legacy. A vehicle that fixes a design load
 * rating sets it; otherwise it is typed.
 */
const LoadRatingFields: FC<Props> = ({
  values,
  shown,
  change,
  leave,
  design,
  hasRatings,
  stored = {},
  leading,
}) => {
  const { canDelete: level2, isPeng } = useAuthorization();
  const codes = useLoadRatingCodes();
  const vehicles = codes.data?.designVehicles ?? [];

  return (
    <div className="structure-detail__fields">
      {leading}
      {/* Set from the vehicle when it fixes one; typed, at Level 2, when it does not. */}
      {!design.typed || !level2 ? (
        <ReadOnlyField
          label="Original Design Load Rating GVW (tonnes)"
          value={design.typed ? values.designLoadRating : number(design.fixed)}
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
          onBlur={() => leave('designLoadRating')}
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
          onBlur={() => leave('loadPostingSignsCode')}
        >
          <SelectItem value="" text="Choose an answer" />
          {(codes.data?.loadPostingSigns ?? []).map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>
      ) : (
        <ReadOnlyField
          label="Are Load Posting Signs Warranted?"
          value={stored.loadPostingSigns ? describe(stored.loadPostingSigns) : null}
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
          onBlur={() => leave('designVehicleCode')}
        >
          <SelectItem value="" text="None" />
          {vehicles.map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>
      ) : (
        <ReadOnlyField
          label="Design Vehicle Configuration"
          value={stored.designVehicle ? describe(stored.designVehicle) : null}
        />
      )}
      {design.other &&
        (isPeng ? (
          <FieldWithCounter used={byteLength(values.designVehicleComment)} limit={TEXT_MAX}>
            <TextArea
              id="load-rating-vehicle-comment"
              data-testid="load-rating-vehicle-comment"
              labelText={requiredLabel('If Other, please specify', true)}
              rows={4}
              value={values.designVehicleComment}
              invalid={Boolean(shown.designVehicleComment)}
              invalidText={shown.designVehicleComment}
              onChange={(event) => change('designVehicleComment', event.target.value)}
              onBlur={() => leave('designVehicleComment')}
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
  );
};

export default LoadRatingFields;
