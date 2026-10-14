import { Button, Modal } from '@carbon/react';
import { useState } from 'react';

import { requiredLabel } from '@/utils/requiredLabel';

import StructureNumberCombo from './StructureNumberCombo';

import type { StructureRef } from './structureResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useCreateReplacement } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

type Props = {
  structureId: string;
  /** This structure's Structure #, the replacing one, as legacy fixed it. */
  structureName: string;
  /** The structures this one already replaced, which are not recorded twice. */
  replaced: StructureRef[];
  /** The structures that replaced this one, which it cannot also have replaced. */
  replacedBy: StructureRef[];
  onClose: () => void;
};

const nameOf = (structure: StructureRef) => structure.structureName || structure.id;

/** The dialog's blocking rules — legacy's pop-up's, and the two it let through. */
const errorOf = (
  typed: string,
  structureName: string,
  replaced: StructureRef[],
  replacedBy: StructureRef[],
): string | undefined => {
  const name = typed.trim();
  if (!name) return 'Replaced Structure # is required.';
  if (name === structureName) return 'A structure cannot replace itself.';
  if (replaced.some((structure) => nameOf(structure) === name)) {
    return 'Replacement already exists.';
  }
  if (replacedBy.some((structure) => nameOf(structure) === name)) {
    return `Structure # ${name} is already recorded as replacing ${structureName}.`;
  }
  return undefined;
};

/**
 * Records that this structure replaced another — legacy's Replacement History pop-up, a P.Eng's:
 * "Structure # (this one, fixed) replaces Structure # (typed)". The box suggests structures as it
 * is typed in. Saved at once, as legacy's.
 */
const ReplacementDialog: FC<Props> = ({
  structureId,
  structureName,
  replaced,
  replacedBy,
  onClose,
}) => {
  const create = useCreateReplacement(structureId);
  const { display } = useNotification();
  const [replacedName, setReplacedName] = useState('');
  const form = useFieldErrors<'replacedName'>(
    { replacedName: errorOf(replacedName, structureName, replaced, replacedBy) },
    create.fieldErrors,
  );

  const save = () => {
    if (!form.submit()) return;
    create.mutate(
      { replacedName: replacedName.trim() },
      {
        onSuccess: () => {
          display({ kind: 'success', title: 'Replacement added', timeout: 4000 });
          onClose();
        },
        onError: (error) => {
          // Field refusals show beside the field; anything else is a toast.
          if (Object.keys(apiFieldErrors(error)).length > 0) return;
          display({
            kind: 'error',
            title: 'The replacement was not saved',
            subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
            timeout: 0,
          });
        },
      },
    );
  };

  return (
    <Modal
      open
      modalHeading="Add replacement"
      size="sm"
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      // Into the box to type in.
      selectorPrimaryFocus="#replacement-replaced"
      onRequestClose={onClose}
      className="structure-detail__replacement-dialog"
      data-testid="replacement-dialog"
    >
      {/* One line, as legacy's sentence: this structure replaces the one typed. */}
      <div className="structure-detail__replaces-row">
        {/* Shown, not edited — legacy's disabled box — so text sized to it, not a full box. */}
        <div>
          <span className="cds--label">Structure #</span>
          <p className="structure-detail__replaces-self" data-testid="replacement-replacing">
            {structureName}
          </p>
        </div>
        <strong className="structure-detail__replaces-word">replaces</strong>
        <StructureNumberCombo
          id="replacement-replaced"
          labelText={requiredLabel('Structure #', true)}
          value={replacedName}
          invalid={Boolean(form.shown.replacedName)}
          invalidText={form.shown.replacedName}
          disabled={create.isPending}
          onChange={(value) => {
            setReplacedName(value);
            form.change('replacedName');
          }}
          onBlur={() => form.leave('replacedName')}
        />
      </div>

      <div className="structure-detail__dialog-actions">
        <Button
          kind="tertiary"
          size="md"
          onMouseDown={holdFocus}
          onClick={onClose}
          disabled={create.isPending}
        >
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          onMouseDown={holdFocus}
          onClick={save}
          disabled={create.isPending}
          data-testid="replacement-save"
        >
          Add replacement
        </Button>
      </div>
    </Modal>
  );
};

export default ReplacementDialog;
