import { ChevronDown, ChevronUp } from '@carbon/icons-react';
import { useId, useState } from 'react';

import type { OutstandingGroup } from './format';
import type { FC } from 'react';

type Props = {
  groups: OutstandingGroup[];
};

/**
 * What the structure is missing in this tab, behind a disclosure at the top of it — nr-frep's
 * pattern.
 *
 * <p>Open by default, because it is the answer to the banner above the tabs. Collapsing it is a
 * per-visit choice. Grouped by the section (card) of the tab each field is in, so the list says
 * where to go: "Replacement Details — Estimated Load Restriction (year)".
 */
const OutstandingPanel: FC<Props> = ({ groups }) => {
  const [open, setOpen] = useState(true);
  const contentId = useId();

  const total = groups.reduce((sum, group) => sum + group.items.length, 0);
  if (total === 0) return null;
  const noun = total === 1 ? 'item' : 'items';

  return (
    <div className="structure-detail__outstanding" data-testid="structure-outstanding-panel">
      <button
        type="button"
        className="structure-detail__outstanding-toggle"
        aria-expanded={open}
        aria-controls={contentId}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        Outstanding in this tab
        <span className="cds--visually-hidden">{` (${total} ${noun})`}</span>
        {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>
      {open && (
        <div id={contentId} className="structure-detail__outstanding-body">
          {groups.map((group) => (
            <div key={group.title} className="structure-detail__outstanding-group">
              <p className="structure-detail__outstanding-title">{group.title}</p>
              <ul className="structure-detail__outstanding-list">
                {group.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default OutstandingPanel;
