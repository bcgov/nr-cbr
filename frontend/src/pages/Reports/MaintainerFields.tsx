import { Select, SelectItem } from '@carbon/react';

import ClientCombo from '@/components/core/ClientCombo';

import type { ClientSuggestion } from '@/types/client';
import type { FC } from 'react';

import { useClientLocations } from '@/hooks/useClientSearch';
import { clientLabel, locationLabel } from '@/utils/clientSearch';

/** The three criteria the maintainer boxes set. */
export type MaintainerCriteria = {
  clientNumber: string;
  clientLocationCode: string;
  /** The label of the maintainer picked. <b>Display state, not a criterion.</b> */
  maintainerLabel: string;
};

type Props = MaintainerCriteria & {
  /** The prefix of the boxes' ids — the report's. */
  id: string;
  onChange: <K extends keyof MaintainerCriteria>(field: K, value: MaintainerCriteria[K]) => void;
};

/**
 * Designated Maintainer and its location — a client picked from the lookup, as on Structure
 * Search, then one of its locations. Legacy's reports take a client number, not a name, so typed
 * text that is not a pick filters nothing and clears a pick it no longer matches.
 */
const MaintainerFields: FC<Props> = ({
  id,
  clientNumber,
  clientLocationCode,
  maintainerLabel,
  onChange,
}) => {
  const locations = useClientLocations(clientNumber);

  /** A maintainer was picked, or cleared: either way the old location no longer applies. */
  const select = (client: ClientSuggestion | null) => {
    onChange('clientNumber', client?.clientNumber ?? '');
    onChange('maintainerLabel', client ? clientLabel(client) : '');
    onChange('clientLocationCode', '');
  };

  const type = (term: string) => {
    if (clientNumber !== '' && term !== maintainerLabel) {
      select(null);
    }
  };

  return (
    <>
      <ClientCombo
        id={`${id}-maintainer`}
        titleText="Designated Maintainer"
        selectedLabel={maintainerLabel}
        onSelect={select}
        onTermChange={type}
      />
      <Select
        id={`${id}-clientLocationCode`}
        data-testid={`${id}-clientLocationCode`}
        labelText="Maintainer Location"
        disabled={clientNumber === '' || locations.isFetching}
        value={clientLocationCode}
        onChange={(event) => onChange('clientLocationCode', event.target.value)}
      >
        <SelectItem
          value=""
          text={clientNumber === '' ? 'Pick a maintainer first' : 'Any location'}
        />
        {(locations.data ?? []).map((location) => (
          <SelectItem
            key={location.clientLocnCode}
            value={location.clientLocnCode ?? ''}
            text={locationLabel(location)}
          />
        ))}
      </Select>
    </>
  );
};

export default MaintainerFields;
