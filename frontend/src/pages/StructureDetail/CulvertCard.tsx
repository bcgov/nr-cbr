import { Edit, Pipelines } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import CodeWithOther from './CodeWithOther';
import CulvertFields from './CulvertFields';
import {
  culvertErrorsOf,
  culvertWarningsOf,
  toCulvertRequest,
  type CulvertStored,
  type CulvertValues,
} from './culvertRules';
import { blankToEmpty } from './formFields';
import { describe, number } from './format';

import type { CulvertDetails } from './structureResponse';
import type { FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateCulvertDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

/** The culvert's own fields, as the card shows them. Wood log culverts have no type or open bottom. */
const CulvertView: FC<{ culvert: CulvertDetails; woodLog: boolean; identity: ReactNode }> = ({
  culvert,
  woodLog,
  identity,
}) => (
  <div className="structure-detail__fields">
    {identity}
    <ReadOnlyField
      label="Culvert Number"
      value={
        culvert.culvertNumber === null
          ? null
          : `${culvert.culvertNumber} of ${culvert.culvertsOnSite}`
      }
    />
    <ReadOnlyField label="Culvert Length (metres)" value={number(culvert.lengthMetres)} />
    <ReadOnlyField label="Gradient" value={number(culvert.gradient)} />
    {!woodLog && <ReadOnlyField label="Culvert Type" value={describe(culvert.culvertType)} />}
    <CodeWithOther
      label="Culvert Material"
      value={culvert.material}
      comment={culvert.materialComment}
    />
    <ReadOnlyField label="Inlet Cover Depth (mm)" value={number(culvert.inletCoverDepthMm)} />
    <ReadOnlyField label="Outlet Cover Depth (mm)" value={number(culvert.outletCoverDepthMm)} />
    <ReadOnlyField label="Opening Height (mm)" value={number(culvert.openingHeightMm)} />
    <ReadOnlyField label="Opening Width (mm)" value={number(culvert.openingWidthMm)} />
    <ReadOnlyField label="Headwall Location" value={describe(culvert.headwallLocation)} />
    {!woodLog && (
      <ReadOnlyField
        label="Open Bottom Substructure"
        value={describe(culvert.openBottomSubstructure)}
      />
    )}
  </div>
);

type FormProps = {
  structureId: string;
  culvert: CulvertDetails;
  portable: boolean;
  woodLog: boolean;
  /** Type/Class, read-only: a culvert's cannot change. */
  typeClass: ReactNode;
  onDone: () => void;
};

/**
 * The card's values as a form, in place of the view, every field where the view has it. A Wood
 * Log Culvert has no Culvert Type or Open Bottom Substructure, as legacy's page hides them; it is
 * saved as BOX. Each error shows once its field is left, then as it is corrected.
 */
const CulvertForm: FC<FormProps> = ({
  structureId,
  culvert,
  portable,
  woodLog,
  typeClass,
  onDone,
}) => {
  const update = useUpdateCulvertDetails(structureId);
  const { display } = useNotification();
  const [values, setValues] = useState<CulvertValues>({
    portable,
    culvertNumber: blankToEmpty(culvert.culvertNumber),
    lengthMetres: blankToEmpty(culvert.lengthMetres),
    gradient: blankToEmpty(culvert.gradient),
    culvertTypeCode: culvert.culvertType.code ?? '',
    materialCode: culvert.material.code ?? '',
    materialComment: culvert.materialComment ?? '',
    inletCoverDepthMm: blankToEmpty(culvert.inletCoverDepthMm),
    outletCoverDepthMm: blankToEmpty(culvert.outletCoverDepthMm),
    openingHeightMm: blankToEmpty(culvert.openingHeightMm),
    openingWidthMm: blankToEmpty(culvert.openingWidthMm),
    headwallLocationCode: culvert.headwallLocation.code ?? '',
    openBottomCode: culvert.openBottomSubstructure.code ?? '',
  });
  // Legacy's tier rule: required once the culvert has one.
  const stored: CulvertStored = {
    culvertNumber: culvert.culvertNumber !== null,
    lengthMetres: culvert.lengthMetres !== null,
    openingHeightMm: culvert.openingHeightMm !== null,
    openingWidthMm: culvert.openingWidthMm !== null,
    culvertTypeCode: Boolean(culvert.culvertType.code),
    materialCode: Boolean(culvert.material.code),
  };
  const form = useFieldErrors<keyof CulvertValues>(
    culvertErrorsOf(values, stored, woodLog),
    update.fieldErrors,
  );

  const change = <K extends keyof CulvertValues>(field: K, value: CulvertValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    update.mutate(toCulvertRequest(values), {
      onSuccess: () => {
        display({ kind: 'success', title: 'Culvert details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The culvert details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  return (
    <div className="structure-detail__edit" data-testid="culvert-details-form">
      <CulvertFields
        values={values}
        shown={form.shown}
        warnings={culvertWarningsOf(values)}
        change={change}
        leave={form.leave}
        stored={stored}
        woodLog={woodLog}
        storedCodes={{
          culvertType: culvert.culvertType,
          material: culvert.material,
          headwallLocation: culvert.headwallLocation,
          openBottomSubstructure: culvert.openBottomSubstructure,
        }}
        leading={typeClass}
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
          data-testid="culvert-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

type Props = {
  structureId: string;
  culvert: CulvertDetails;
  portable: boolean;
  woodLog: boolean;
  /** Type/Class and Portable as read-only fields, first in the view. */
  identity: ReactNode;
  /** Type/Class alone, read-only, first in the form. */
  typeClass: ReactNode;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/**
 * The Details tab's Culvert card — legacy's culvert rows with the structure's Portable Structure —
 * with its own "Edit culvert" for Level 2 and up, legacy's `/level2Access` on every one of them.
 * The edit replaces the values in place, as the Bridge card does.
 */
const CulvertCard: FC<Props> = ({
  structureId,
  culvert,
  portable,
  woodLog,
  identity,
  typeClass,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canDelete: canChange } = useAuthorization();
  return (
    <Card
      title="Culvert"
      icon={Pipelines}
      testId="structure-section-culvert"
      action={
        canChange &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="culvert-details-edit"
            onClick={onEdit}
          >
            Edit culvert
          </Button>
        )
      }
    >
      {editing ? (
        <CulvertForm
          structureId={structureId}
          culvert={culvert}
          portable={portable}
          woodLog={woodLog}
          typeClass={typeClass}
          onDone={onDone}
        />
      ) : (
        <CulvertView culvert={culvert} woodLog={woodLog} identity={identity} />
      )}
    </Card>
  );
};

export default CulvertCard;
