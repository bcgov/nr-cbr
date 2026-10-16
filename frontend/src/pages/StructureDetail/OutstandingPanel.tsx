import { ChevronDown, ChevronUp, ErrorFilled } from '@carbon/icons-react';
import { useId, useState } from 'react';

import type { OutstandingGroup } from './format';
import type { FC } from 'react';

type Props = {
  groups: OutstandingGroup[];
  /** The disclosure's wording; what the structure is missing by default. */
  heading?: string;
  testId?: string;
  /**
   * `error` once a save has been refused for these items: the line turns red with an error icon,
   * in step with the tab counts and the banner — nr-frep's pattern. The items keep their colour.
   */
  tone?: 'neutral' | 'error';
  /** What the red items are blocking, for screen readers: `save` by default. */
  blocking?: string;
};

/**
 * What the structure is missing in this tab, behind a disclosure at the top of it — nr-frep's
 * pattern.
 *
 * <p>Open by default, because it is the answer to the banner above the tabs. Collapsing it is a
 * per-visit choice. Grouped by the section (card) of the tab each field is in, so the list says
 * where to go: "Replacement Details — Estimated Load Restriction (year)".
 */
const OutstandingPanel: FC<Props> = ({
  groups,
  heading = 'Outstanding in this tab',
  testId = 'structure-outstanding-panel',
  tone = 'neutral',
  blocking = 'save',
}) => {
  const [open, setOpen] = useState(true);
  const contentId = useId();

  const total = groups.reduce((sum, group) => sum + group.items.length, 0);
  if (total === 0) return null;
  const noun = total === 1 ? 'item' : 'items';

  return (
    <div className="structure-detail__outstanding" data-testid={testId}>
      <button
        type="button"
        className={`structure-detail__outstanding-toggle${
          tone === 'error' ? ' structure-detail__outstanding-toggle--error' : ''
        }`}
        aria-expanded={open}
        aria-controls={contentId}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        {tone === 'error' && <ErrorFilled size={16} />}
        {heading}
        <span className="cds--visually-hidden">
          {` (${total} ${noun}${tone === 'error' ? `, blocking ${blocking}` : ''})`}
        </span>
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
