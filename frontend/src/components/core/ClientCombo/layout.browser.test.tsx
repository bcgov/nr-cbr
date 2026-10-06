import { Select, SelectItem } from '@carbon/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { page } from '@vitest/browser/context';
import { describe, expect, it, vi } from 'vitest';

import ClientCombo from './index';

// The app's real stylesheet, Carbon included — the measurement depends on Carbon's label and field
// sizes, which the component's own SCSS does not supply.
import '@/styles/index.scss';

vi.mock('@/services/APIs', () => ({ default: { client: { searchClients: vi.fn() } } }));

describe('ClientCombo — beside another field', () => {
  it('puts its box on the same line as a plain field beside it', async () => {
    // The spinner sits inside the label so it can come and go without moving the box. An
    // inline-flex label made that row taller than a plain one, so the box sat a few pixels below a
    // select in the same row.
    await page.viewport(1400, 900);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', alignItems: 'start' }}>
          <ClientCombo
            id="combo"
            titleText="Designated Maintainer"
            selectedLabel=""
            onSelect={() => {}}
            onTermChange={() => {}}
          />
          <Select id="plain" data-testid="plain" labelText="Maintainer Location">
            <SelectItem value="" text="Any" />
          </Select>
        </div>
      </QueryClientProvider>,
    );

    const combo = screen
      .getByRole('combobox', { name: 'Designated Maintainer' })
      .getBoundingClientRect();
    const plain = screen.getByTestId('plain').getBoundingClientRect();

    expect(combo.top).toBeCloseTo(plain.top, 0);
  });
});
