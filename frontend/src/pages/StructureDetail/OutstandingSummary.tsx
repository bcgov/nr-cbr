import { InlineNotification } from '@carbon/react';

import type { FC } from 'react';

type Props = {
  /** Every outstanding item on the structure. */
  total: number;
  /** How many tabs carry at least one of them. */
  tabs: number;
  /** True once adding an inspection has been refused for these items. */
  refused?: boolean;
  /** What the items stand in the way of — the structure's: adding an inspection. */
  subtitle?: string;
  /** Once refused: what was refused, and why — the structure's: adding an inspection. */
  refusedTitle?: string;
  refusedSubtitle?: string;
  testId?: string;
};

/**
 * Legacy's red "Warning: Structure data is incomplete.", as nr-frep's page-level tally above the tab
 * strip: how much is outstanding, and across how many tabs.
 *
 * <p>Informational until something is refused for it: an incomplete structure is ordinary unfinished
 * data and saves as it is. Once Add Routine / Unplanned Inspection is pressed on it, the same items
 * have become the reason the inspection could not be added, so the banner turns red — and the tab
 * counts and each tab's list turn with it. Nothing renders once the structure is complete.
 */
const OutstandingSummary: FC<Props> = ({
  total,
  tabs,
  refused = false,
  subtitle = 'Inspections can be added once these are complete.',
  refusedTitle = 'Inspection cannot be added',
  refusedSubtitle = 'All outstanding items must be complete first. The tab counts show what is still outstanding.',
  testId = 'structure-outstanding-summary',
}) => {
  if (total === 0) return null;
  if (refused) {
    return (
      <InlineNotification
        className="structure-detail__outstanding-summary"
        kind="error"
        lowContrast
        hideCloseButton
        title={refusedTitle}
        subtitle={refusedSubtitle}
        data-testid={testId}
      />
    );
  }
  const items = total === 1 ? 'required item' : 'required items';
  const where = tabs === 1 ? '1 tab' : `${tabs} tabs`;
  return (
    <InlineNotification
      className="structure-detail__outstanding-summary"
      kind="info"
      lowContrast
      hideCloseButton
      title={`${total} ${items} outstanding across ${where}`}
      subtitle={subtitle}
      data-testid={testId}
    />
  );
};

export default OutstandingSummary;
