import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import InspectionDetailPage from './index';

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/inspection/4021']}>
      <Routes>
        <Route path="/inspection/:inspectionId" element={<InspectionDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );

describe('InspectionDetailPage — placeholder', () => {
  it('names the inspection, badged as under construction', () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Inspection 4021' })).toBeInTheDocument();
    expect(screen.getByText('Under construction')).toBeInTheDocument();
  });

  it('says the page is under construction, and leads back to Inspection Search', () => {
    renderPage();

    expect(screen.getByTestId('inspection-detail-placeholder')).toMatchTextContent(
      'This page is under construction',
    );
    expect(screen.getByText('Inspection Search')).toBeInTheDocument();
  });
});
