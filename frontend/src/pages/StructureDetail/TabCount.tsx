import type { FC } from 'react';

type Props = {
  /** Outstanding items in the tab; nothing is drawn at zero. */
  count: number;
  /** The tab's title, so the badge's accessible name says which tab it counts. */
  section: string;
  /** What is counted: items outstanding (the structure page), or errors (a form's tabs). */
  kind?: 'outstanding' | 'errors';
  /** `error` once a save has been refused for these items — nr-frep's tone. */
  tone?: 'neutral' | 'error';
  /** What the red items are blocking, for screen readers: `save` by default. */
  blocking?: string;
};

/**
 * The outstanding-items badge after a tab's label. Only a count is
 * drawn: a tab with nothing outstanding carries no mark, so the badges stand out on their own.
 */
const TabCount: FC<Props> = ({
  count,
  section,
  kind = 'outstanding',
  tone = 'neutral',
  blocking = 'save',
}) => {
  if (count === 0) return null;
  const blockingNote = tone === 'error' ? `, blocking ${blocking}` : '';
  const items = count === 1 ? 'item' : 'items';
  const errors = count === 1 ? 'error' : 'errors';
  const label =
    kind === 'errors'
      ? `${section}: ${count} ${errors}`
      : `${section}: ${count} ${items} outstanding${blockingNote}`;
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
      <circle
        cx="8"
        cy="8"
        r="8"
        fill={
          tone === 'error'
            ? 'var(--cds-support-error, #da1e28)'
            : 'var(--cds-background-inverse, #393939)'
        }
      />
      <text
        x="8"
        y="8"
        textAnchor="middle"
        dominantBaseline="central"
        fontSize="10"
        fontWeight="600"
        fill={tone === 'error' ? '#ffffff' : 'var(--cds-text-inverse, #ffffff)'}
      >
        {count > 99 ? '99+' : count}
      </text>
    </svg>
  );
};

export default TabCount;
