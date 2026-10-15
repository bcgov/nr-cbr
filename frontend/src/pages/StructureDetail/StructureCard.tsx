import { Building, Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';
import Card from './Card';
import { describe, money, number, yesNo } from './format';
import { blankToEmpty } from './formFields';
import StructureFields from './StructureFields';
import {
  structureErrorsOf,
  structureWarningsOf,
  toStructureRequest,
  type StructureValues,
} from './structureRules';

import type { StructureCommonDetails } from './structureResponse';
import type { FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateStructureDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

type Props = {
  structureId: string;
  details: StructureCommonDetails;
  /** Shown here only when the structure has no bridge or culvert card to carry them. */
  extra?: ReactNode;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/** What the card shows when it is not being edited. */
const StructureView: FC<{ details: StructureCommonDetails; extra?: ReactNode }> = ({
  details,
  extra,
}) => (
  <div className="structure-detail__fields">
    {extra}
    <ReadOnlyField label="Built For" value={describe(details.builtBy)} />
    <ReadOnlyField label="Year Superstructure Fabricated" value={number(details.yearFabricated)} />
    <ReadOnlyField label="Year Superstructure Installed" value={number(details.yearBuilt)} />
    <ReadOnlyField label="Year Added to Inventory" value={number(details.inventoryAddedYear)} />
    <ReadOnlyField label="Source" value={describe(details.source)} />
    <ReadOnlyField label="End of Design Life (year)" value={number(details.endOfDesignLifeYear)} />
    <ReadOnlyField label="As Built Information on File?" value={yesNo(details.asBuiltInfoOnFile)} />
    <ReadOnlyField label="Installation Cost ($)" value={money(details.installationCost)} />
    <ReadOnlyField label="Material Cost ($)" value={money(details.materialCost)} />
  </div>
);

/**
 * The card's values as a form, in place of the view, every field where the view has it.
 *
 * <p>Legacy's rules beside their fields — each once its field is left, then as it is corrected, and
 * all of them on Save: years of up to four digits, the fabrication year
 * not after this year or the year installed, Source once the structure has one, Year Added to
 * Inventory for the sources IR, INSR, ITM, IFS and IIO, costs in whole dollars the column holds.
 * Legacy's range warnings show as the fields are typed and do not stop the save.
 */
const StructureForm: FC<{
  structureId: string;
  details: StructureCommonDetails;
  extra?: ReactNode;
  onDone: () => void;
}> = ({ structureId, details, extra, onDone }) => {
  const update = useUpdateStructureDetails(structureId);
  const { display } = useNotification();
  // As legacy: required once the structure has one.
  const sourceRequired = Boolean(details.source.code);

  const [values, setValues] = useState<StructureValues>({
    builtByCode: details.builtBy.code ?? '',
    yearFabricated: blankToEmpty(details.yearFabricated),
    yearBuilt: blankToEmpty(details.yearBuilt),
    inventoryAddedYear: blankToEmpty(details.inventoryAddedYear),
    sourceCode: details.source.code ?? '',
    endOfDesignLifeYear: blankToEmpty(details.endOfDesignLifeYear),
    asBuiltInfoOnFile: details.asBuiltInfoOnFile,
    installationCost: blankToEmpty(details.installationCost),
    materialCost: blankToEmpty(details.materialCost),
  });
  // Checked as the values change; each shown once its field is left, or at Save.
  const form = useFieldErrors<keyof StructureValues>(
    structureErrorsOf(values, sourceRequired),
    update.fieldErrors,
  );

  const change = <K extends keyof StructureValues>(field: K, value: StructureValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    update.mutate(toStructureRequest(values), {
      onSuccess: () => {
        display({ kind: 'success', title: 'Structure details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The structure details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  return (
    <div className="structure-detail__edit" data-testid="structure-details-form">
      <StructureFields
        values={values}
        shown={form.shown}
        warnings={structureWarningsOf(values)}
        change={change}
        leave={form.leave}
        sourceRequired={sourceRequired}
        storedCodes={{ builtBy: details.builtBy, source: details.source }}
        leading={extra}
      />

      {/* The form's actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button
          onMouseDown={holdFocus}
          kind="tertiary"
          size="sm"
          onClick={onDone}
          disabled={update.isPending}
        >
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          size="sm"
          onClick={save}
          disabled={update.isPending}
          data-testid="structure-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

/**
 * The Details tab's Structure card — legacy's fields above the bridge and culvert ones — with its
 * own "Edit structure" for Level 2 and up, legacy's `/level2Access` on every one of them. The edit
 * replaces the values in place, as the Inspection Schedule card does.
 */
const StructureCard: FC<Props> = ({
  structureId,
  details,
  extra,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canDelete: canChange } = useAuthorization();
  return (
    <Card
      title="Structure"
      icon={Building}
      testId="structure-section-common"
      action={
        canChange &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="structure-details-edit"
            onClick={onEdit}
          >
            Edit structure
          </Button>
        )
      }
    >
      {editing ? (
        <StructureForm structureId={structureId} details={details} extra={extra} onDone={onDone} />
      ) : (
        <StructureView details={details} extra={extra} />
      )}
    </Card>
  );
};

export default StructureCard;
