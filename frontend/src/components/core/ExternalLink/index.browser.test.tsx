import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import ExternalLink from './index';

describe('ExternalLink', () => {
  it('opens an outside address in a new tab, with no handle back on this window', () => {
    render(<ExternalLink href="https://example.gov.bc.ca">Example</ExternalLink>);
    const link = screen.getByRole('link', { name: 'Example (opens in a new tab)' });

    expect(link).toHaveAttribute('href', 'https://example.gov.bc.ca');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('opens a CBR route in a new tab', () => {
    render(
      <MemoryRouter>
        <ExternalLink to="/inventory/site/62-001">62-001</ExternalLink>
      </MemoryRouter>,
    );
    const link = screen.getByRole('link', { name: '62-001 (opens in a new tab)' });

    expect(link).toHaveAttribute('href', '/inventory/site/62-001');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('carries the launch icon, hidden from screen readers', () => {
    render(<ExternalLink href="https://example.gov.bc.ca">Example</ExternalLink>);

    expect(document.querySelector('.external-link__icon')).toHaveAttribute('aria-hidden', 'true');
  });

  it('keeps its label on one line in a column too narrow for it', () => {
    // A site id in a results column broke at its hyphen, "62-" over "001".
    render(
      <div style={{ width: '2rem' }}>
        <ExternalLink href="https://example.com">62-001</ExternalLink>
      </div>,
    );

    // The visible text alone: the link also holds the icon and the screen-reader text.
    const link = screen.getByRole('link');
    const label = Array.from(link.childNodes).find(
      (node) => node.nodeType === Node.TEXT_NODE && node.textContent === '62-001',
    );
    if (!label) throw new Error('label text node not found');
    const range = document.createRange();
    range.selectNodeContents(label);
    const lines = new Set(Array.from(range.getClientRects()).map((rect) => Math.round(rect.top)));
    expect(lines.size).toBe(1);
  });
});
