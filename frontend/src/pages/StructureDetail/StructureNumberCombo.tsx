import { ComboBox } from '@carbon/react';
import { useState } from 'react';

import type { StructureNameSuggestion } from './structureResponse';
import type { FC, ReactNode } from 'react';

import { useStructureNameSuggestions } from '@/hooks/useStructureSearch';

type Props = {
  id: string;
  labelText: ReactNode;
  /** The Structure # in the box, picked or typed. */
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  invalid?: boolean;
  invalidText?: string;
  disabled?: boolean;
};

/**
 * A Structure # box that suggests structures as it is typed in — those whose Structure # starts
 * with the text, archived ones marked. A Structure # typed in full and not picked is kept too;
 * whether it exists is the server's to say.
 */
const StructureNumberCombo: FC<Props> = ({
  id,
  labelText,
  value,
  onChange,
  onBlur,
  invalid,
  invalidText,
  disabled,
}) => {
  // The text first shown, as the item Carbon renders it from; the box owns its text after that.
  const [initial] = useState<StructureNameSuggestion | undefined>(() =>
    value ? { id: '', structureName: value, archived: false } : undefined,
  );
  const { data: suggestions = [] } = useStructureNameSuggestions(value);

  return (
    // Blur reaches the wrapper from the box inside; the combo box takes no `onBlur` of its own.
    <div onBlur={onBlur}>
      <ComboBox<StructureNameSuggestion>
        id={id}
        data-testid={id}
        titleText={labelText}
        autoComplete="off"
        // Without this Carbon empties a box whose text matches no suggestion when it loses focus.
        allowCustomValue
        placeholder="Search by Structure #"
        disabled={disabled}
        invalid={invalid}
        invalidText={invalidText}
        items={value.trim() ? suggestions : []}
        initialSelectedItem={initial}
        itemToString={(item) => item?.structureName ?? ''}
        itemToElement={(item) => (
          <span>
            {item.structureName}
            {item.archived && ' (archived)'}
          </span>
        )}
        onInputChange={(text) => onChange(text ?? '')}
        onChange={({ selectedItem }) => {
          // With custom values allowed, Carbon hands back typed text as a string, not an item.
          const picked = selectedItem as StructureNameSuggestion | string | null | undefined;
          if (!picked) return;
          onChange(typeof picked === 'string' ? picked : picked.structureName);
        }}
      />
    </div>
  );
};

export default StructureNumberCombo;
