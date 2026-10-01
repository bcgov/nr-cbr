import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import StructureDetailPage from './index';

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const renderAt = (entry: string | { pathname: string; state: unknown }) =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/inventory/structure/:structureId" element={<StructureDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );

describe('StructureDetailPage — placeholder', () => {
  it('is badged as under construction and says the page is not built', () => {
    renderAt('/inventory/structure/7');

    expect(screen.getByText('Under construction')).toBeInTheDocument();
    expect(screen.getByTestId('structure-detail-placeholder')).toHaveTextContent(
      'This page has not been built yet',
    );
  });

  it('names the structure the search link carried', () => {
    renderAt({ pathname: '/inventory/structure/7', state: { structureName: 'B100' } });

    expect(screen.getByRole('heading', { name: 'Structure B100' })).toBeInTheDocument();
  });

  it('falls back to the id when opened without the link, e.g. on a reload', () => {
    renderAt('/inventory/structure/7');

    expect(screen.getByRole('heading', { name: 'Structure 7' })).toBeInTheDocument();
  });

  it('leads back to Structure Search', () => {
    renderAt('/inventory/structure/7');

    expect(screen.getByText('Structure Search')).toBeInTheDocument();
  });
});
