import { FilterableMultiSelect, MultiSelect } from '@carbon/react';
import { useMemo, type FC, type ReactNode } from 'react';

import type { MultiCodeOption } from './options';

import './multiCodeSelect.scss';

type Props = {
  id: string;
  /** The field's label, above the box — with a required asterisk where the form asks for one. */
  titleText: ReactNode;
  /** What the box reads with nothing picked — "Any status". Nothing picked means no filter. */
  placeholder: string;
  options: readonly MultiCodeOption[];
  /** The values picked, in any order. */
  selected: readonly string[];
  /** The values picked, in the list's order. */
  onChange: (values: string[]) => void;
  disabled?: boolean;
  /**
   * A type-to-filter box above the list — for the long lists (Forest District, Management Area,
   * BCTS Business Area). The short code lists are a plain checkbox list.
   */
  filterable?: boolean;
  /** The form's message for the picks, shown beneath the box; none when they are fine. */
  invalidText?: string;
  /** Focus left the box — for a form that shows a field's error once it has been visited. */
  onBlur?: () => void;
};

const itemToString = (option: MultiCodeOption | null): string => option?.text ?? '';

/** The list's own order — the code table's — rather than Carbon's alphabetical, selected-first. */
const keepOrder = (items: MultiCodeOption[]): MultiCodeOption[] => items;

/**
 * A search or report filter that takes several values: a checkbox list, with nothing ticked meaning
 * "any". Requested in UAT (the "CBR Pick list", 2026-11) for the highlighted dropdowns.
 *
 * <p>Controlled, so Clear empties it with the rest of the criteria. Ticking does not move an item —
 * `selectionFeedback="fixed"` — so the list keeps the code table's order while the user works
 * through it.
 */
const MultiCodeSelect: FC<Props> = ({
  id,
  titleText,
  placeholder,
  options,
  selected,
  onChange,
  disabled = false,
  filterable = false,
  invalidText,
  onBlur,
}) => {
  const items = useMemo(() => [...options], [options]);
  const selectedItems = useMemo(
    () => items.filter((option) => selected.includes(option.value)),
    [items, selected],
  );
  /**
   * What the box reads: the placeholder ("Any district") only while nothing is ticked. Carbon
   * keeps showing it beside the count tag once something is, which read as "1 picked, yet any
   * district" — so the picks' names replace it, cut off with an ellipsis when they do not fit.
   */
  const shown =
    selectedItems.length === 0
      ? placeholder
      : selectedItems.map((option) => option.text).join(', ');
  const change = ({ selectedItems: picked }: { selectedItems: MultiCodeOption[] | null }) => {
    const values = new Set((picked ?? []).map((option) => option.value));
    onChange(items.filter((option) => values.has(option.value)).map((option) => option.value));
  };

  return (
    <div
      data-testid={id}
      // On the wrapper: focus moving within the box — the input, the list — bubbles here too, which
      // only marks the box visited a little early.
      onBlur={onBlur}
      className={
        selectedItems.length > 0
          ? 'multi-code-select multi-code-select--picked'
          : 'multi-code-select'
      }
    >
      {filterable ? (
        <FilterableMultiSelect<MultiCodeOption>
          id={id}
          titleText={titleText}
          placeholder={shown}
          items={items}
          itemToString={itemToString}
          selectedItems={selectedItems}
          onChange={change}
          selectionFeedback="fixed"
          sortItems={keepOrder}
          disabled={disabled}
          invalid={Boolean(invalidText)}
          invalidText={invalidText}
        />
      ) : (
        <MultiSelect<MultiCodeOption>
          id={id}
          titleText={titleText}
          label={shown}
          items={items}
          itemToString={itemToString}
          selectedItems={selectedItems}
          onChange={change}
          selectionFeedback="fixed"
          sortItems={keepOrder}
          disabled={disabled}
          invalid={Boolean(invalidText)}
          invalidText={invalidText}
        />
      )}
    </div>
  );
};

export default MultiCodeSelect;
