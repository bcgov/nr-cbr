import type { FC } from 'react';

import { formatShortDate } from '@/utils/date';

/** Who moved a repair or a monitor to a status, and when. */
export type UserAudit = { userId: string | null; date: string | null };

/** The three steps a repair or monitor is audited at. */
export type Audited = {
  suggested: UserAudit | null;
  required: UserAudit | null;
  completed: UserAudit | null;
};

/** Which items the Repairs and Monitoring tabs list — legacy's "Choose Viewing Option". */
export type ItemView = 'OUTSTANDING' | 'ALL';

const STEPS: { key: keyof Audited; label: string }[] = [
  { key: 'suggested', label: 'Suggested' },
  { key: 'required', label: 'Required' },
  { key: 'completed', label: 'Completed' },
];

const line = (label: string, audit: UserAudit) =>
  `${label}: ${[audit.userId, formatShortDate(audit.date)].filter(Boolean).join(', ')}`;

/**
 * Legacy's "User Audits", written out in the cell rather than in a tooltip: one line per step taken,
 * nothing for a step not taken.
 */
const UserAudits: FC<{ item: Audited }> = ({ item }) => (
  <ul className="structure-detail__audits">
    {STEPS.map(({ key, label }) => {
      const audit = item[key];
      return audit ? <li key={key}>{line(label, audit)}</li> : null;
    })}
  </ul>
);

export default UserAudits;
