import { ComboBox } from '@carbon/react';
import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';
import SiteFields from '@/pages/StructureDetail/SiteFields';
import { requiredLabel } from '@/utils/requiredLabel';

import type { StructureNameSuggestion } from '@/pages/StructureDetail/structureResponse';
import type { FC } from 'react';

import {
  STRUCTURE_NAME_MIN,
  useStructure,
  useStructureNameSuggestions,
} from '@/hooks/useStructureSearch';
import { describe } from '@/pages/StructureDetail/format';

type Props = {
  /** The structure being inspected, once chosen. */
  structureId: string | null;
  /** True when the page was opened for a structure — from its Inspections tab — and it is fixed. */
  fixed: boolean;
  onPick: (structureId: string | null) => void;
  /** What is wrong with the choice — none made, or the server's refusal. */
  error?: string;
};

const suggestionLabel = (item: StructureNameSuggestion) =>
  item.archived ? `${item.structureName} (archived)` : item.structureName;

/**
 * The Inspection tab's first card: the structure being inspected and its site, read-only, as the
 * structure page's Information tab shows them. Opened from the side menu, the structure is found by a
 * type-ahead on Structure #; opened from a structure, it is that structure.
 *
 * <p>An incomplete structure cannot be inspected, as legacy: it says so beside the Structure #, as
 * soon as it is chosen.
 */
const InformationSection: FC<Props> = ({ structureId, fixed, onPick, error }) => {
  const [term, setTerm] = useState('');
  const { data: suggestions = [] } = useStructureNameSuggestions(term);
  const structure = useStructure(structureId ?? undefined);
  const loaded = structure.data;
  const incomplete = loaded !== undefined && loaded.outstanding.length > 0;
  // Beside the Structure #, as the app's other validation: the server's refusal, or incomplete.
  const message =
    error ??
    (incomplete
      ? `Structure ${loaded.structureName ?? ''} is incomplete. Complete it on its structure page before it can be inspected.`
      : undefined);

  return (
    <div className="structure-detail__fields">
      {fixed ? (
        <ReadOnlyField label="Structure #" value={loaded?.structureName} />
      ) : (
        <ComboBox<StructureNameSuggestion>
          id="inspection-structure"
          data-testid="inspection-structure"
          titleText={requiredLabel('Structure #', true)}
          placeholder="Search by Structure #"
          helperText={`Type at least ${STRUCTURE_NAME_MIN} characters of the Structure #.`}
          items={term.trim().length >= STRUCTURE_NAME_MIN ? suggestions : []}
          itemToString={(item) => item?.structureName ?? ''}
          itemToElement={suggestionLabel}
          invalid={Boolean(message)}
          invalidText={message}
          onInputChange={(text) => setTerm(text ?? '')}
          onChange={({ selectedItem }) => onPick(selectedItem?.id ?? null)}
        />
      )}
      <ReadOnlyField label="Type/Class" value={loaded ? describe(loaded.typeClass) : null} />
      <ReadOnlyField label="Site #" value={loaded?.site?.siteId} />
      <SiteFields site={loaded?.site ?? null} />
      {fixed && message && (
        <p
          className="structure-detail__upload-error"
          role="alert"
          data-testid="inspection-structure-error"
        >
          {message}
        </p>
      )}
    </div>
  );
};

export default InformationSection;
