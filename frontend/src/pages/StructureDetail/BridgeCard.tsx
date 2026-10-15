import { Edit, Road, WarningAltFilled } from '@carbon/icons-react';
import { Button, Select, SelectItem } from '@carbon/react';
import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import BridgeFields from './BridgeFields';
import {
  BRIDGE_TYPES,
  DURABLE,
  bridgeErrorsOf,
  bridgeWarningsOf,
  toBridgeRequest,
  type BridgeStored,
  type BridgeValues,
} from './bridgeRules';
import Card from './Card';
import CodeWithOther from './CodeWithOther';
import { blankToEmpty } from './formFields';
import { describe, isOther, number, yesNo } from './format';

import type { BridgeDetails, CodeValue } from './structureResponse';
import type { FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useStructureTypeClassCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateBridgeDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

/** Legacy warns when a bridge has more spans than this. */
const SPANS_ADVISED = 10;

/** Number of Spans, with legacy's warning past ten. Kept by the Spans & Piers tab. */
const SpanCount: FC<{ count: number }> = ({ count }) => (
  <ReadOnlyField
    label="Number of Spans"
    value={
      <>
        {count}
        {/* Legacy's warning under the count; it blocks nothing. */}
        {count > SPANS_ADVISED && (
          <span className="structure-detail__field-warning" data-testid="span-count-warning">
            <WarningAltFilled size={16} aria-hidden="true" />
            Should not be greater than {SPANS_ADVISED}.
          </span>
        )}
      </>
    }
  />
);

/** The bridge's own fields, as the card shows them. */
const BridgeView: FC<{ bridge: BridgeDetails; identity: ReactNode }> = ({ bridge, identity }) => (
  <div className="structure-detail__fields">
    {identity}
    <SpanCount count={bridge.spanCount} />
    <ReadOnlyField label="Needle Beams?" value={yesNo(bridge.needleBeams)} />
    <ReadOnlyField label="Bridge Length (metres)" value={number(bridge.lengthMetres)} />
    <CodeWithOther
      label="Superstructure"
      value={bridge.superstructure}
      comment={bridge.superstructureComment}
    />
    <CodeWithOther label="Deck Type" value={bridge.deckType} comment={bridge.deckTypeComment} />
    <ReadOnlyField label="Deck Width (metres)" value={number(bridge.deckWidthMetres)} />
    <ReadOnlyField label="Running Surface" value={describe(bridge.runningSurface)} />
    <CodeWithOther label="Curb Type" value={bridge.curbType} comment={bridge.curbTypeComment} />
    <ReadOnlyField
      label="Left Abutment (Looking Downstream)"
      value={describe(bridge.leftAbutment)}
    />
    <ReadOnlyField
      label="Right Abutment (Looking Downstream)"
      value={describe(bridge.rightAbutment)}
    />
    {(isOther(bridge.leftAbutment) || isOther(bridge.rightAbutment)) && (
      <ReadOnlyField
        label="If Other (Right or Left), please specify"
        value={bridge.abutmentComment}
      />
    )}
  </div>
);

type FormProps = {
  structureId: string;
  bridge: BridgeDetails;
  typeClass: CodeValue;
  portable: boolean;
  onDone: () => void;
};

/**
 * The card's values as a form, in place of the view, every field where the view has it.
 *
 * <p>Type/Class and Portable Structure are edited here with the fields their rules check: a
 * Durable Bridge needs a Curb Type and no temporary superstructure or abutment; a portable one a
 * portable superstructure. Each error shows once its field is left, then as it is corrected.
 */
const BridgeForm: FC<FormProps> = ({ structureId, bridge, typeClass, portable, onDone }) => {
  const typeClasses = useStructureTypeClassCodes();
  const update = useUpdateBridgeDetails(structureId);
  const { display } = useNotification();

  const [values, setValues] = useState<BridgeValues>({
    typeClassCode: typeClass.code ?? '',
    portable,
    needleBeams: bridge.needleBeams,
    totalBridgeLength: blankToEmpty(bridge.lengthMetres),
    superstructureTypeCode: bridge.superstructure.code ?? '',
    superstructureComment: bridge.superstructureComment ?? '',
    deckTypeCode: bridge.deckType.code ?? '',
    deckTypeComment: bridge.deckTypeComment ?? '',
    deckWidth: blankToEmpty(bridge.deckWidthMetres),
    runningSurfaceCode: bridge.runningSurface.code ?? '',
    curbTypeCode: bridge.curbType.code ?? '',
    curbTypeComment: bridge.curbTypeComment ?? '',
    leftAbutmentCode: bridge.leftAbutment.code ?? '',
    rightAbutmentCode: bridge.rightAbutment.code ?? '',
    abutmentComment: bridge.abutmentComment ?? '',
  });
  // Legacy's tier rule: required once the bridge has one.
  const stored: BridgeStored = {
    totalBridgeLength: bridge.lengthMetres !== null,
    deckWidth: bridge.deckWidthMetres !== null,
    superstructureTypeCode: Boolean(bridge.superstructure.code),
    deckTypeCode: Boolean(bridge.deckType.code),
    runningSurfaceCode: Boolean(bridge.runningSurface.code),
    leftAbutmentCode: Boolean(bridge.leftAbutment.code),
    rightAbutmentCode: Boolean(bridge.rightAbutment.code),
  };
  const form = useFieldErrors<keyof BridgeValues>(
    bridgeErrorsOf(values, stored),
    update.fieldErrors,
  );
  const shown = form.shown;

  const change = <K extends keyof BridgeValues>(field: K, value: BridgeValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    update.mutate(toBridgeRequest(values), {
      onSuccess: () => {
        display({ kind: 'success', title: 'Bridge details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The bridge details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  const typeOptions = BRIDGE_TYPES.map((code) => ({
    code,
    description:
      typeClasses.data?.find((option) => option.code === code)?.description ??
      (code === DURABLE ? 'Durable Bridge' : 'Non-Durable Bridge'),
  }));

  return (
    <div className="structure-detail__edit" data-testid="bridge-details-form">
      <BridgeFields
        values={values}
        shown={shown}
        warnings={bridgeWarningsOf(values)}
        change={change}
        leave={form.leave}
        stored={stored}
        storedCodes={{
          superstructure: bridge.superstructure,
          deckType: bridge.deckType,
          runningSurface: bridge.runningSurface,
          curbType: bridge.curbType,
          leftAbutment: bridge.leftAbutment,
          rightAbutment: bridge.rightAbutment,
        }}
        leading={
          <Select
            id="bridge-typeClassCode"
            data-testid="bridge-typeClassCode"
            labelText={requiredLabel('Type/Class', true)}
            value={values.typeClassCode}
            disabled={typeClasses.isLoading}
            invalid={Boolean(shown.typeClassCode)}
            invalidText={shown.typeClassCode}
            onChange={(event) => change('typeClassCode', event.target.value)}
            onBlur={() => form.leave('typeClassCode')}
          >
            <SelectItem value="" text="Choose a type" />
            {typeOptions.map((option) => (
              <SelectItem key={option.code} value={option.code} text={option.description} />
            ))}
          </Select>
        }
        afterPortable={<SpanCount count={bridge.spanCount} />}
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
          data-testid="bridge-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

type Props = {
  structureId: string;
  bridge: BridgeDetails;
  typeClass: CodeValue;
  portable: boolean;
  /** Type/Class and Portable as read-only fields, first in the view. */
  identity: ReactNode;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/**
 * The Details tab's Bridge card — legacy's bridge rows with the structure's Type/Class and Portable
 * Structure — with its own "Edit bridge" for Level 2 and up, legacy's `/level2Access` on every one
 * of them. The edit replaces the values in place, as the Structure card does.
 */
const BridgeCard: FC<Props> = ({
  structureId,
  bridge,
  typeClass,
  portable,
  identity,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canDelete: canChange } = useAuthorization();
  return (
    <Card
      title="Bridge"
      icon={Road}
      testId="structure-section-bridge"
      action={
        canChange &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="bridge-details-edit"
            onClick={onEdit}
          >
            Edit bridge
          </Button>
        )
      }
    >
      {editing ? (
        <BridgeForm
          structureId={structureId}
          bridge={bridge}
          typeClass={typeClass}
          portable={portable}
          onDone={onDone}
        />
      ) : (
        <BridgeView bridge={bridge} identity={identity} />
      )}
    </Card>
  );
};

export default BridgeCard;
