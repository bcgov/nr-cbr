import { InlineNotification } from '@carbon/react';

import type { FC } from 'react';

type Props = {
  /** Every outstanding item on the structure. */
  total: number;
};

/**
 * Legacy's red "Warning: Structure data is incomplete.", in nr-frep's form: a low-contrast notice
 * above the tabs that counts what is missing and points at the tab that lists it — nr-frep's
 * `OutstandingSummary` on its checklist pages.
 *
 * <p>Informational, not an error: an incomplete structure is ordinary unfinished data, not a fault
 * in the page. Nothing renders when the structure is complete.
 */
const OutstandingSummary: FC<Props> = ({ total }) => {
  if (total === 0) return null;
  const items = total === 1 ? 'required item' : 'required items';
  return (
    <InlineNotification
      className="structure-detail__outstanding-summary"
      kind="info"
      lowContrast
      hideCloseButton
      title={`Structure data is incomplete: ${total} ${items} outstanding`}
      subtitle="The Details tab lists what is missing."
      data-testid="structure-outstanding-summary"
    />
  );
};

export default OutstandingSummary;
