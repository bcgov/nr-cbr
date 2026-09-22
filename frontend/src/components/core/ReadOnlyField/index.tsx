import type { FC, ReactNode } from 'react';

import './index.scss';

type Props = {
  label: string;
  /** The value as the user should read it — a decoded description, not a stored code. */
  value: ReactNode;
  /** Shown when there is no value. An em dash, unless the field deserves an explanation. */
  emptyText?: string;
};

/**
 * One label-and-value pair, for a field the user may look at but not change.
 *
 * <p>The shape nr-frep uses for its tombstone values: a small secondary-coloured label above the
 * value in body text. Deliberately <b>not</b> a disabled input — a greyed-out box invites a click
 * that does nothing, takes a tab stop for no reason, and sizes itself to the field it would have
 * been rather than to the value it holds.
 *
 * <p>An empty value reads as an em dash rather than as nothing at all, so a field with no answer
 * is visibly a field with no answer instead of a rendering fault.
 */
const ReadOnlyField: FC<Props> = ({ label, value, emptyText = '—' }) => {
  const empty = value === null || value === undefined || value === '';
  return (
    <div className="read-only-field">
      <span className="read-only-field__label">{label}</span>
      <span className="read-only-field__value">{empty ? emptyText : value}</span>
    </div>
  );
};

export default ReadOnlyField;
