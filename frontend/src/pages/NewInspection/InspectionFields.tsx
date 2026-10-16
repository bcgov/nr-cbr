import { Forum, Inspection as InspectionIcon, WaveDirection } from '@carbon/icons-react';
import {
  Checkbox,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import Card from '@/pages/StructureDetail/Card';
import DateField from '@/pages/StructureDetail/DateField';
import { requiredLabel } from '@/utils/requiredLabel';

import {
  ADDABLE_STATUSES,
  COMMENT_MAX,
  IN_PROGRESS,
  TEXT_MAX,
  type HazardValues,
  type InspectionValues,
} from './inspectionRules';

import type { CodeOption } from '@/types/configuration';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';
import { byteLength } from '@/utils/textLimits';

type Messages = Record<string, string | undefined>;

type FieldProps<V> = {
  values: V;
  shown: Messages;
  warnings?: Messages;
  change: <K extends keyof V & string>(field: K, value: V[K]) => void;
  leave: (field: keyof V & string) => void;
};

/** Routine, which needs Level 1 — Unplanned needs Level 0. */
const ROUTINE = 'ROUT';

/**
 * The Inspection tab: the type, the status, the date, who inspected and at what cost, whether a
 * professional inspection is needed, and the inspector's comments. Added In Progress or Submitted;
 * Submitted needs the date, inspector and company. Reviewed and Rejected come later, from a P.Eng
 * on the inspection's own page.
 */
export const InspectionFields: FC<
  FieldProps<InspectionValues> & {
    types: CodeOption[];
    /** Every inspection report status; those an inspection may be added at are offered. */
    statuses: CodeOption[];
    /** Set by the structure's Add Routine / Add Unplanned button: shown, not changed. */
    typeFixed: boolean;
  }
> = ({ values, shown, change, leave, types, statuses, typeFixed }) => {
  const { canEdit, canDelete: level2 } = useAuthorization();
  // Routine is Level 1's: a Level 0 user is offered the types they may add.
  const offered = types.filter((type) => type.code !== ROUTINE || canEdit);
  const addable = statuses.filter((status) => ADDABLE_STATUSES.has(status.code));
  // Past In Progress, the date, inspector and company are required — legacy's rule.
  const submitting = values.statusCode !== IN_PROGRESS;
  const text = (
    field: 'inspectorName' | 'inspectorCompanyName' | 'cost',
    label: ReactNode,
    max: number,
  ) => (
    <TextInput
      id={`inspection-${field}`}
      data-testid={`inspection-${field}`}
      labelText={label}
      inputMode={field === 'cost' ? 'numeric' : undefined}
      maxLength={max}
      value={values[field]}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
      onBlur={() => leave(field)}
    />
  );
  return (
    <>
      <Card title="Inspection" icon={InspectionIcon} testId="inspection-section-inspection">
        <div className="structure-detail__edit">
          <div className="structure-detail__fields">
            {typeFixed ? (
              <ReadOnlyField
                label="Inspection Type"
                value={
                  types.find((type) => type.code === values.typeCode)?.description ??
                  values.typeCode
                }
              />
            ) : (
              <Select
                id="inspection-typeCode"
                data-testid="inspection-typeCode"
                labelText={requiredLabel('Inspection Type', true)}
                value={values.typeCode}
                invalid={Boolean(shown.typeCode)}
                invalidText={shown.typeCode}
                onChange={(event) => change('typeCode', event.target.value)}
                onBlur={() => leave('typeCode')}
              >
                <SelectItem value="" text="Choose a type" />
                {offered.map((type) => (
                  <SelectItem key={type.code} value={type.code} text={type.description} />
                ))}
              </Select>
            )}
            <Select
              id="inspection-statusCode"
              data-testid="inspection-statusCode"
              labelText={requiredLabel('Inspection Report Status', true)}
              value={values.statusCode}
              invalid={Boolean(shown.statusCode)}
              invalidText={shown.statusCode}
              onChange={(event) => change('statusCode', event.target.value)}
              onBlur={() => leave('statusCode')}
            >
              {addable.map((status) => (
                <SelectItem key={status.code} value={status.code} text={status.description} />
              ))}
            </Select>
            <DateField
              id="inspection-date"
              labelText={requiredLabel('Date of Inspection', submitting)}
              value={values.inspectionDate}
              invalidText={shown.inspectionDate}
              onChange={(value) => change('inspectionDate', value)}
              onBlur={() => leave('inspectionDate')}
            />
            {text('inspectorName', requiredLabel('Inspector Name', submitting), 255)}
            {text(
              'inspectorCompanyName',
              requiredLabel('Inspection Company Name', submitting),
              255,
            )}
            {text('cost', 'Cost of Inspection ($)', 6)}
            {level2 ? (
              <RadioButtonGroup
                name="inspection-professional"
                legendText="Professional Inspection Required?"
                valueSelected={values.professionalInspectionRequired ? 'Y' : 'N'}
                onChange={(selected) => change('professionalInspectionRequired', selected === 'Y')}
              >
                <RadioButton id="inspection-professional-yes" labelText="Yes" value="Y" />
                <RadioButton id="inspection-professional-no" labelText="No" value="N" />
              </RadioButtonGroup>
            ) : (
              // Legacy's /level2Access on the box: below Level 2 it is shown, not set.
              <ReadOnlyField label="Professional Inspection Required?" value="No" />
            )}
          </div>
        </div>
      </Card>
      <Card title="Inspection Comments" icon={Forum} testId="inspection-section-comments">
        <div className="structure-detail__edit">
          <FieldWithCounter used={byteLength(values.inspectionComment)} limit={COMMENT_MAX}>
            <TextArea
              id="inspection-comment"
              data-testid="inspection-comment"
              labelText="Comments"
              rows={4}
              value={values.inspectionComment}
              invalid={Boolean(shown.inspectionComment)}
              invalidText={shown.inspectionComment}
              onChange={(event) => change('inspectionComment', event.target.value)}
              onBlur={() => leave('inspectionComment')}
            />
          </FieldWithCounter>
        </div>
      </Card>
    </>
  );
};

/**
 * The Hydraulic Hazards tab: the channel's three measurements, legacy's five hazards and their
 * comments. Legacy's range warnings show as typed and never stop the save.
 */
export const HazardFields: FC<FieldProps<HazardValues>> = ({
  values,
  shown,
  warnings = {},
  change,
  leave,
}) => {
  const metres = (
    field: 'presentWaterDepth' | 'presentWaterWidth' | 'highWaterClearance',
    label: string,
  ) => (
    <TextInput
      id={`hazard-${field}`}
      data-testid={`hazard-${field}`}
      labelText={label}
      inputMode="decimal"
      maxLength={7}
      value={values[field]}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      warn={!shown[field] && Boolean(warnings[field])}
      warnText={warnings[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
      onBlur={() => leave(field)}
    />
  );
  const flags: ['highWater' | 'debris' | 'scour' | 'ice' | 'aggradation', string][] = [
    ['highWater', 'High Water?'],
    ['debris', 'Debris?'],
    ['scour', 'Scour?'],
    ['ice', 'Ice?'],
    ['aggradation', 'Aggradation?'],
  ];
  return (
    <Card title="Hydraulic Hazards" icon={WaveDirection} testId="inspection-section-hazards">
      <div className="structure-detail__edit">
        <div className="structure-detail__fields">
          {metres('presentWaterDepth', 'Estimated Present Water Level Depth (m)')}
          {metres('presentWaterWidth', 'Estimated Present Water Level Width (m)')}
          {metres('highWaterClearance', 'Estimated High Water Level Clearance (m)')}
        </div>
        <fieldset className="new-inspection__hazards">
          <legend className="cds--label">Hazards</legend>
          <div className="new-inspection__hazard-list">
            {flags.map(([field, label]) => (
              <Checkbox
                key={field}
                id={`hazard-${field}`}
                data-testid={`hazard-${field}`}
                labelText={label}
                checked={values[field]}
                onChange={(_, { checked }) => change(field, checked)}
              />
            ))}
          </div>
        </fieldset>
        <FieldWithCounter used={byteLength(values.hazardComment)} limit={TEXT_MAX}>
          <TextArea
            id="hazard-comment"
            data-testid="hazard-comment"
            labelText="Comments"
            rows={3}
            value={values.hazardComment}
            invalid={Boolean(shown.hazardComment)}
            invalidText={shown.hazardComment}
            onChange={(event) => change('hazardComment', event.target.value)}
            onBlur={() => leave('hazardComment')}
          />
        </FieldWithCounter>
      </div>
    </Card>
  );
};
