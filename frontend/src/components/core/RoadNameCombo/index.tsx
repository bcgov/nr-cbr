import { ComboBox, Loading } from '@carbon/react';
import { useState, type FC } from 'react';

import { MIN_ROAD_NAME_TERM_LENGTH, useRoadNames } from '@/hooks/useRoadNames';
import { ROAD_LOOKUP_UNAVAILABLE, isRoadLookupUnavailable } from '@/services/road.service';
import { roadNameCase } from '@/utils/roadName';

import '../ClientCombo/clientCombo.scss';

type Props = {
  id: string;
  /** The road name picked, or `''` for none. */
  value: string;
  /** A name was picked, or the field was cleared (`''`). */
  onChange: (roadName: string) => void;
  disabled?: boolean;
};

/**
 * Forest Service Road on the search screens: a pick list of LRMOPS's road names.
 *
 * <p>The search matches the name picked <b>exactly</b>, so the field takes a pick, not free text:
 * text that is not picked is dropped when the field loses focus. Matching exactly is what keeps the
 * filter cheap — a partial name could stand for thousands of LRMOPS road sections, an exact one
 * for a handful — and the list is what makes exact usable, as LRMOPS spells the same road several
 * ways.
 */
const RoadNameCombo: FC<Props> = ({ id, value, onChange, disabled }) => {
  const [term, setTerm] = useState(roadNameCase(value));

  // Picking a name puts it in the input, which would otherwise ask for it again.
  const searchTerm = term.trim() === roadNameCase(value).trim() ? '' : term;
  const { data: items = [], isFetching, error } = useRoadNames(searchTerm);
  const unavailable = error != null && isRoadLookupUnavailable(error);

  return (
    <ComboBox
      autoComplete="off"
      id={id}
      data-testid={id}
      className="client-combo"
      disabled={disabled}
      titleText={
        <span className="client-combo__label">
          Forest Service Road
          {isFetching && (
            <Loading
              small
              withOverlay={false}
              description="Searching road names"
              className="client-combo__spinner"
            />
          )}
        </span>
      }
      helperText={`Road name (min. ${MIN_ROAD_NAME_TERM_LENGTH} characters)`}
      placeholder="Search for a road"
      invalid={unavailable}
      invalidText={ROAD_LOOKUP_UNAVAILABLE}
      items={items}
      // Shown in mixed case; the name picked is passed on as LRMOPS stores it.
      itemToString={(item: string | null) => roadNameCase(item)}
      selectedItem={value || null}
      onInputChange={(text: string) => setTerm(text ?? '')}
      onChange={({ selectedItem }: { selectedItem?: string | null }) => {
        setTerm(roadNameCase(selectedItem));
        onChange(selectedItem ?? '');
      }}
    />
  );
};

export default RoadNameCombo;
