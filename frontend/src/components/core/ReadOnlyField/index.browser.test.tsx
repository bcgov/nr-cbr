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

  it("sets the value in nr-frep's record style — 14px, 18px lines, 0.16px tracking", () => {
    // Measured from nr-frep's record header: plain text in a Carbon Tile, which is body-compact-01.
    render(<ReadOnlyField label="Site #" value="63-007" />);
    const style = getComputedStyle(screen.getByText('63-007'));

    expect(style.fontSize).toBe('14px');
    expect(parseFloat(style.lineHeight)).toBeCloseTo(18, 0);
    expect(style.letterSpacing).toBe('0.16px');
  });
});
