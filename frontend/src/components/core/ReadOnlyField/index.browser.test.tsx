import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ReadOnlyField from './index';

import '@/styles/index.scss';

describe('ReadOnlyField', () => {
  it('shows the value', () => {
    render(<ReadOnlyField label="Site #" value="63-007" />);

    expect(screen.getByText('63-007')).toBeInTheDocument();
  });

  it('shows an em dash when there is no value', () => {
    render(<ReadOnlyField label="Crossing Name" value={null} />);

    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('sets the value as input text is set — body-compact-02: 16px, 22px lines, no tracking', () => {
    // The team's text size: one step above Carbon's body-compact-01 (styles/_overrides.scss).
    render(<ReadOnlyField label="Site #" value="63-007" />);
    const style = getComputedStyle(screen.getByText('63-007'));

    expect(style.fontSize).toBe('16px');
    expect(parseFloat(style.lineHeight)).toBeCloseTo(22, 0);
    expect(['0px', 'normal']).toContain(style.letterSpacing);
  });
});
