import type { FC } from 'react';

type Props = {
  /** Outstanding items in the tab; nothing is drawn at zero. */
  count: number;
  /** The tab's title, so the badge's accessible name says which tab it counts. */
  section: string;
};

/**
 * The outstanding-items badge after a tab's label — nr-frep's `TabStatusIcon`. Only a count is
 * drawn: a tab with nothing outstanding carries no mark, so the badges stand out on their own.
 */
const TabCount: FC<Props> = ({ count, section }) => {
  if (count === 0) return null;
  const label = `${section}: ${count} ${count === 1 ? 'item' : 'items'} outstanding`;
  return (
    <svg
      className="structure-detail__tab-count"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      role="img"
      aria-label={label}
      focusable="false"
    >
      <title>{label}</title>
      <circle cx="8" cy="8" r="8" fill="var(--cds-background-inverse, #393939)" />
      <text
        x="8"
        y="8"
        textAnchor="middle"
        dominantBaseline="central"
        fontSize="10"
        fontWeight="600"
        fill="var(--cds-text-inverse, #ffffff)"
      >
        {count > 99 ? '99+' : count}
      </text>
    </svg>
  );
};

export default TabCount;
