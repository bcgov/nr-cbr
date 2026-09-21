import { ComboBox, Loading } from '@carbon/react';
import { useRef, useState, type FC } from 'react';

import type { ClientSuggestion } from '@/types/client';

import { useClientSearch } from '@/hooks/useClientSearch';
import { MIN_CLIENT_TERM_LENGTH, clientLabel } from '@/utils/clientSearch';

import './clientCombo.scss';

type Props = {
  id: string;
  titleText: string;
  /**
   * The label of the currently-picked suggestion, or `''` when nothing is picked.
   *
   * <p>Owned by the caller rather than held here: Carbon needs a selected *item* to render a
   * label, and the picked suggestion is rarely still in the list by the time it matters — the term
   * has usually moved on.
   */
  selectedLabel: string;
  /** A suggestion was picked, or the selection was cleared. */
  onSelect: (client: ClientSuggestion | null) => void;
  /**
   * The raw text in the field, on every keystroke.
   *
   * <p>Only a *pick* yields a client number, so text that matches no suggestion would otherwise be
   * dropped on the floor. The caller uses this to keep searching on the name instead.
   */
  onTermChange: (term: string) => void;
  disabled?: boolean;
};

/**
 * Type-ahead picker for a Forest Client, replacing the legacy search-form-in-a-popup.
 *
 * <p>The lookup it replaces opened a second window, asked the user to choose which of six fields to
 * search, submit, and then click a row — to fill in two text boxes. One term searches client name,
 * division name, city and client number together here.
 *
 * <p>Each suggestion is a client *location*, because that is what a site records; see
 * {@link clientLabel} for how the two are told apart on screen.
 */
const ClientCombo: FC<Props> = ({
  id,
  titleText,
  selectedLabel,
  onSelect,
  onTermChange,
  disabled,
}) => {
  const [term, setTerm] = useState(selectedLabel);

  /**
   * The label of the suggestion just picked, so the input change Carbon fires on a pick can be told
   * apart from the user typing that same text.
   *
   * <p>Carbon raises both `onChange` and `onInputChange` when an item is chosen, and does not
   * promise an order. Whichever lands first, the result is the same: if the pick is seen first this
   * ref suppresses the echo, and if the echo is seen first the pick that follows overwrites it.
   */
  const justPicked = useRef('');

  // Picking an item makes Carbon fire onInputChange with that item's own label, which would
  // otherwise send the full "CANFOR CORPORATION · Vancouver · 00001012-00" back as a search term —
  // matching nothing, and replacing a good list with an empty one the moment the user chose from it.
  const searchTerm = term.trim() === selectedLabel.trim() ? '' : term;
  const { data: items = [], isFetching } = useClientSearch(searchTerm);

  return (
    <ComboBox
      autoComplete="off"
      id={id}
      data-testid={id}
      className="client-combo"
      disabled={disabled}
      // Without this Carbon discards text matching no suggestion the moment the field blurs —
      // including the blur that pressing Search causes. The typed name would vanish just as the
      // search that uses it ran, and the user would watch the field empty itself for no reason.
      allowCustomValue
      titleText={
        <span className="client-combo__label">
          {titleText}
          {isFetching && (
            <Loading
              small
              withOverlay={false}
              description="Searching clients"
              className="client-combo__spinner"
            />
          )}
        </span>
      }
      helperText={`Name, city or client number (min. ${MIN_CLIENT_TERM_LENGTH} characters)`}
      placeholder="Search for a maintainer"
      items={items}
      itemToString={(item: ClientSuggestion | null) => (item ? clientLabel(item) : '')}
      // A stand-in carrying only what itemToString reads, because the picked suggestion is usually
      // no longer among `items` — the term has moved on since it was chosen.
      selectedItem={selectedLabel ? ({ clientName: selectedLabel } as ClientSuggestion) : null}
      onInputChange={(value: string) => {
        const next = value ?? '';
        setTerm(next);
        if (next === justPicked.current) {
          return;
        }
        // Editing the text after a pick is no longer that pick, so the guard lapses with it.
        justPicked.current = '';
        onTermChange(next);
      }}
      onChange={({ selectedItem }: { selectedItem?: ClientSuggestion | null }) => {
        justPicked.current = selectedItem ? clientLabel(selectedItem) : '';
        setTerm(justPicked.current);
        onSelect(selectedItem ?? null);
      }}
    />
  );
};

export default ClientCombo;
