import type { FC, ReactNode } from 'react';

import './index.scss';

type Props = {
  /** Bytes used — see `byteLength`. The caller measures, so the unit is its decision. */
  used: number;
  limit: number;
  children: ReactNode;
};

/**
 * Wraps a length-limited field with a live "used / limit" count at the end of its label.
 *
 * <p><b>Purely presentational.</b> It never truncates and never sets `invalid`: the caller owns
 * both, so the count can never disagree with the error text beside it. Truncating is the thing
 * worth avoiding — `maxLength` silently drops the tail of pasted text, which is how someone loses
 * the end of a paragraph without noticing. Telling them it is too long, and refusing the save, is
 * the honest version of the same rule.
 *
 * <p>Ported from nr-frep's component of the same name.
 */
const FieldWithCounter: FC<Props> = ({ used, limit, children }) => (
  <div className="cbr-field">
    {children}
    <div className="cbr-field__footer">
      {/* Polite, so a screen reader hears the count settle rather than having every keystroke
          announced over the top of what the user is typing. */}
      <span
        className={
          used > limit ? 'cbr-field__counter cbr-field__counter--over' : 'cbr-field__counter'
        }
        aria-live="polite"
      >
        {used} / {limit}
      </span>
    </div>
  </div>
);

export default FieldWithCounter;
