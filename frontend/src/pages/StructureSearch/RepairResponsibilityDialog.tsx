import { Button, Select, SelectItem } from '@carbon/react';
import { useState } from 'react';

import ClientCombo from '@/components/core/ClientCombo';
import Modal from '@/components/Modal';

import { sites, structures } from './selection';

import type { SelectedStructure } from './types';
import type { ClientSuggestion } from '@/types/client';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useClientLocations } from '@/hooks/useClientSearch';
import { useUpdateRepairResponsibility } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { clientLabel, locationLabel } from '@/utils/clientSearch';

type Props = {
  open: boolean;
  /** Every ticked structure, on every page. */
  selected: ReadonlyMap<string, SelectedStructure>;
  onClose: () => void;
  /** The update went through; the page clears the ticks. */
  onUpdated: () => void;
};

type Maintainer = { clientNumber: string; label: string };

const NO_MAINTAINER: Maintainer = { clientNumber: '', label: '' };

/**
 * Update Repair Responsibility — a new designated maintainer for the ticked structures' sites.
 *
 * <p>Legacy puts a client number box, a location code box and the button under the results, with
 * no confirmation. Here the button opens this dialog, which picks the maintainer with the same
 * lookup Site Search uses and says first how many sites will change: the maintainer belongs to the
 * site, so every structure on it takes the new one, ticked or not.
 *
 * <p>The page mounts this only while it is open, so each opening starts with nothing picked.
 *
 * <p>The lookup offers clients already maintaining a site, and the locations they maintain from —
 * the business's choice; legacy's popup searched every client.
 */
const RepairResponsibilityDialog: FC<Props> = ({ open, selected, onClose, onUpdated }) => {
  const { display } = useNotification();
  const update = useUpdateRepairResponsibility();

  const [maintainer, setMaintainer] = useState<Maintainer>(NO_MAINTAINER);
  const [location, setLocation] = useState('');
  /** Set by the first Update, so the fields are not marked before the user has had a go. */
  const [attempted, setAttempted] = useState(false);

  const locations = useClientLocations(maintainer.clientNumber);

  const siteIds = new Set(
    [...selected.values()].map(({ siteId }) => siteId).filter((siteId) => siteId !== null),
  );
  const siteless = [...selected.values()].filter(({ siteId }) => siteId === null).length;

  const maintainerError =
    attempted && maintainer.clientNumber === '' ? 'Designated Maintainer is required.' : undefined;
  const locationError =
    attempted && maintainer.clientNumber !== '' && location === ''
      ? 'Maintainer Location is required.'
      : undefined;

  const submit = () => {
    setAttempted(true);
    if (maintainer.clientNumber === '' || location === '') {
      return;
    }
    const chosen = `${maintainer.clientNumber}-${location}`;
    update.mutate(
      {
        structureIds: [...selected.keys()],
        clientNumber: maintainer.clientNumber,
        clientLocationCode: location,
      },
      {
        onSuccess: ({ structureCount, siteCount }) => {
          onClose();
          onUpdated();
          // Legacy's "{0} structure(s) were successfully updated.", with the sites it changed.
          display({
            kind: 'success',
            title: `${structures(structureCount)} updated`,
            subtitle: `${sites(siteCount)} now maintained by ${chosen}.`,
            timeout: 4000,
          });
        },
        onError: (error) => {
          onClose();
          // The ticks stay, so the user can try again without finding them all again.
          display({
            kind: 'error',
            title: 'Repair responsibility was not updated',
            subtitle: apiErrorMessage(
              error,
              'Nothing was changed. Try again, or contact support if this continues.',
            ),
            timeout: 0,
          });
        },
      },
    );
  };

  const onSites = siteIds.size === 1 ? 'that site' : 'those sites';

  return (
    <Modal
      open={open}
      modalHeading="Update repair responsibility"
      // Passive, with the buttons drawn below: Carbon's footer stretches its two buttons across the
      // dialog's full width. These sit bottom-right, the way the app's other dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="repair-responsibility-dialog"
    >
      <p className="structure-search__dialog-line">
        This sets the maintainer of {sites(siteIds.size)}. Every structure on {onSites}, ticked or
        not, will have the new maintainer.
      </p>
      {siteless > 0 && (
        <p className="structure-search__dialog-line">
          {structures(siteless)} selected stand on no site and will be skipped.
        </p>
      )}

      <div className="structure-search__dialog-fields">
        <ClientCombo
          id="repair-responsibility-maintainer"
          titleText="Designated Maintainer"
          selectedLabel={maintainer.label}
          invalid={maintainerError !== undefined}
          invalidText={maintainerError}
          onSelect={(client: ClientSuggestion | null) => {
            setMaintainer(
              client
                ? { clientNumber: client.clientNumber ?? '', label: clientLabel(client) }
                : NO_MAINTAINER,
            );
            setLocation('');
          }}
          // Text that is not a pick names no maintainer, so it clears the one picked before.
          onTermChange={() => {
            setMaintainer(NO_MAINTAINER);
            setLocation('');
          }}
        />
        <Select
          id="repair-responsibility-location"
          data-testid="repair-responsibility-location"
          labelText="Maintainer Location"
          disabled={maintainer.clientNumber === '' || locations.isFetching}
          value={location}
          invalid={locationError !== undefined}
          invalidText={locationError}
          onChange={(event) => setLocation(event.target.value)}
        >
          <SelectItem
            value=""
            text={maintainer.clientNumber === '' ? 'Pick a maintainer first' : 'Select a location'}
          />
          {(locations.data ?? []).map((option) => (
            <SelectItem
              key={option.clientLocnCode}
              value={option.clientLocnCode ?? ''}
              text={locationLabel(option)}
            />
          ))}
        </Select>
      </div>

      <div className="structure-search__dialog-actions">
        <Button kind="tertiary" size="md" onClick={onClose} disabled={update.isPending}>
          Cancel
        </Button>
        <Button kind="primary" size="md" onClick={submit} disabled={update.isPending}>
          Update
        </Button>
      </div>
    </Modal>
  );
};

export default RepairResponsibilityDialog;
