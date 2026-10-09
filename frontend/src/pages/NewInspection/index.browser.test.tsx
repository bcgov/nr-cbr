import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { userEvent } from 'vitest/browser';

import NewInspectionPage from './index';

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const renderPage = (search: string) =>
  render(
    <MemoryRouter initialEntries={[`/inspection/new${search}`]}>
      <Routes>
        <Route path="/inspection/new" element={<NewInspectionPage />} />
        <Route path="/inventory/structure/:structureId" element={<p>Structure page</p>} />
      </Routes>
    </MemoryRouter>,
  );

describe('NewInspectionPage — placeholder', () => {
  it('names the kind of inspection, badged as under construction', () => {
    renderPage('?structureId=7&type=ROUT');

    expect(screen.getByRole('heading', { name: 'New routine inspection' })).toBeInTheDocument();
    expect(screen.getByText('Under construction')).toBeInTheDocument();
    expect(screen.getByTestId('new-inspection-placeholder')).toMatchTextContent(
      'This page is under construction',
    );
  });

  it('leads back to the structure it was started from', async () => {
    renderPage('?structureId=7&type=UNP');

    expect(screen.getByRole('heading', { name: 'New unplanned inspection' })).toBeInTheDocument();
    await userEvent.click(screen.getByText('Structure'));

    expect(await screen.findByText('Structure page')).toBeInTheDocument();
  });
});
