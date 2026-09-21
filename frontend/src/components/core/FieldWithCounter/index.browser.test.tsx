import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import FieldWithCounter from './index';

describe('FieldWithCounter', () => {
  it('reads "used / limit"', () => {
    render(
      <FieldWithCounter used={12} limit={255}>
        <textarea aria-label="Site Details" />
      </FieldWithCounter>,
    );

    expect(screen.getByText('12 / 255')).toBeInTheDocument();
  });

  it('keeps the field it wraps', () => {
    render(
      <FieldWithCounter used={0} limit={255}>
        <textarea aria-label="Site Details" />
      </FieldWithCounter>,
    );

    expect(screen.getByLabelText('Site Details')).toBeInTheDocument();
  });

  it('marks the count over the limit, so the change is visible and not only numeric', () => {
    render(
      <FieldWithCounter used={256} limit={255}>
        <textarea aria-label="Site Details" />
      </FieldWithCounter>,
    );

    expect(screen.getByText('256 / 255')).toHaveClass('cbr-field__counter--over');
  });

  it('announces politely, so typing is not talked over', () => {
    // assertive would interrupt the user on every keystroke, which is the opposite of helpful for
    // a number that only matters as it approaches the limit.
    render(
      <FieldWithCounter used={1} limit={255}>
        <textarea aria-label="Site Details" />
      </FieldWithCounter>,
    );

    expect(screen.getByText('1 / 255')).toHaveAttribute('aria-live', 'polite');
  });

  it('never truncates or invalidates on its own', () => {
    // Both belong to the caller, so the count can never disagree with the error text beside it.
    render(
      <FieldWithCounter used={999} limit={255}>
        <textarea aria-label="Site Details" />
      </FieldWithCounter>,
    );

    const field = screen.getByLabelText('Site Details');
    expect(field).not.toHaveAttribute('maxlength');
    expect(field).not.toHaveAttribute('aria-invalid', 'true');
  });
});
