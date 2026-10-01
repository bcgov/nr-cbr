import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import AddStructurePage from './index';

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/inventory/site/BOWRON-001/add-structure']}>
      <Routes>
        <Route path="/inventory/site/:siteId/add-structure" element={<AddStructurePage />} />
      </Routes>
    </MemoryRouter>,
  );

describe('AddStructurePage — placeholder', () => {
  it('is badged as under construction and says the page is not built', () => {
    renderPage();

    expect(screen.getByText('Under construction')).toBeInTheDocument();
    expect(screen.getByTestId('add-structure-placeholder')).toHaveTextContent(
      'This page has not been built yet',
    );
  });

  it('names the site the structure is for, and leads back to it', () => {
    renderPage();

    expect(screen.getByText('For site BOWRON-001.')).toBeInTheDocument();
    expect(screen.getByText('Site BOWRON-001')).toBeInTheDocument();
  });
});
