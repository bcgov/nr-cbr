import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { page, userEvent } from '@vitest/browser/context';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import StructureDetailPage from './index';

// The app's real stylesheet, Carbon included: these measure layout, which Carbon's sizes drive.
import '@/styles/index.scss';

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const code = (value: string) => ({ code: value, description: value });

const api = vi.hoisted(() => ({ getStructure: vi.fn() }));
vi.mock('@/services/APIs', () => ({ default: { structureSearch: api } }));

type Overrides = { details?: object; loadRating?: object; comments?: object[] };

const renderPage = async (overrides: Overrides = {}) => {
  api.getStructure.mockResolvedValue({
    id: '7',
    structureName: 'B100',
    active: true,
    typeClass: code('TB'),
    site: {
      siteId: '62-001',
      siteTypeCode: 'XNG',
      siteStatus: code('ACT'),
      inspectionStatus: code('OK'),
      districtName: 'Cariboo-Chilcotin',
      managementAreaName: 'Central',
      forestServiceRoad: 'CHILCOTIN SOUTH',
      projectName: null,
      crossingName: 'Riske Cr',
      forestFileId: '6970',
      roadSectionId: '01',
      kilometres: '0.24',
      maintainerLabel: 'CANFOR',
    },
    details: {
      builtBy: code('MOF'),
      yearFabricated: null,
      yearBuilt: 2000,
      inventoryAddedYear: null,
      source: code('MOF'),
      endOfDesignLifeYear: null,
      asBuiltInfoOnFile: false,
      installationCost: null,
      materialCost: null,
      portable: false,
      ...overrides.details,
    },
    bridge: null,
    culvert: null,
    comments: overrides.comments ?? [],
    loadRating: {
      currentRating: null,
      history: [],
      loadPostingSigns: code('N'),
      reviewRequired: false,
      designVehicle: code('L75'),
      designVehicleComment: null,
      designLoadRating: null,
      ...overrides.loadRating,
    },
    replaced: [],
    replacedBy: [],
    replacement: {
      estimatedClosureYear: null,
      estimatedReplacementYear: null,
      estimatedLoadRestrictionYear: null,
      estimatedReplacementCost: null,
      replacementCostComment: null,
    },
    outstanding: [{ section: 'DETAILS', label: 'Deck Width' }],
  });
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/inventory/structure/7']}>
        <Routes>
          <Route path="/inventory/structure/:structureId" element={<StructureDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByTestId('structure-header');
};

describe('StructureDetailPage — layout', () => {
  it('lays the header out four fields to a row on a wide screen', async () => {
    await page.viewport(1400, 900);
    await renderPage();

    const cells = Array.from(
      screen.getByTestId('structure-header').querySelectorAll('.read-only-field'),
    ).map((cell) => cell.getBoundingClientRect());

    expect(cells[1].top).toBeCloseTo(cells[0].top, 0);
    expect(cells[3].top).toBeCloseTo(cells[0].top, 0);
    expect(cells[4].top).toBeGreaterThan(cells[0].top);
  });

  it('keeps the tab badge on the tab label line', async () => {
    await page.viewport(1400, 900);
    await renderPage();

    const badge = screen
      .getByRole('img', { name: 'Details: 1 item outstanding' })
      .getBoundingClientRect();
    const label = screen.getByText('Details', { selector: '.structure-detail__tab-label' });
    const box = label.getBoundingClientRect();

    expect(badge.top + badge.height / 2).toBeCloseTo(box.top + box.height / 2, 0);
    expect(badge.left).toBeGreaterThan(box.left);
  });

  it('puts the status pill right beside the heading, on its centre line', async () => {
    await page.viewport(1400, 900);
    await renderPage();

    const heading = screen.getByRole('heading', { name: 'Structure B100' }).getBoundingClientRect();
    const pill = screen.getByTestId('structure-site-status').getBoundingClientRect();

    // Beside it, not pushed to the far edge of the page.
    expect(pill.left - heading.right).toBeGreaterThanOrEqual(0);
    expect(pill.left - heading.right).toBeLessThanOrEqual(16);
    // Centred on it, and its own small height rather than stretched to the heading's.
    expect(pill.top + pill.height / 2).toBeCloseTo(heading.top + heading.height / 2, 0);
    expect(pill.height).toBeLessThan(heading.height);
  });

  it('sets each value close under its label, as nr-frep does', async () => {
    await page.viewport(1400, 900);
    await renderPage();

    const cell = screen.getByTestId('structure-header').querySelector('.read-only-field')!;
    const label = cell.querySelector('.read-only-field__label')!.getBoundingClientRect();
    const value = cell.querySelector('.read-only-field__value')!.getBoundingClientRect();

    expect(value.top - label.bottom).toBeCloseTo(4, 0);
  });

  it('sets the load rating history apart from the fields above, and its checkbox from its table', async () => {
    await page.viewport(1400, 900);
    await renderPage({
      details: { yearBuilt: 2000 },
      loadRating: {
        history: [
          {
            id: 'r1',
            rating: 40,
            reason: code('DES'),
            reasonComment: null,
            date: '1990-01-01',
            userId: 'IDIR\\OLD',
            reviewedDate: null,
            status: 'MANUAL',
            inspectionId: null,
            current: false,
          },
          {
            id: 'r2',
            rating: 60,
            reason: code('INSP'),
            reasonComment: null,
            date: '2020-01-01',
            userId: 'IDIR\\NEW',
            reviewedDate: null,
            status: 'MANUAL',
            inspectionId: null,
            current: true,
          },
        ],
      },
    });

    const fields = screen
      .getByTestId('structure-section-load-rating')
      .querySelector('.structure-detail__fields')!
      .getBoundingClientRect();
    const checkbox = document.querySelector('.cds--checkbox-wrapper')!.getBoundingClientRect();
    const table = screen
      .getByRole('table', { name: 'Load rating history' })
      .getBoundingClientRect();

    expect(checkbox.top - fields.bottom).toBeGreaterThanOrEqual(24);
    expect(table.top - checkbox.bottom).toBeGreaterThanOrEqual(12);
  });

  it('keeps the comment date and IDIR ID on one line beside a long comment', async () => {
    await page.viewport(1400, 900);
    await renderPage({
      comments: [
        {
          id: '1',
          text: 'word '.repeat(400),
          userId: 'IDIR\\LHIGGS',
          timestamp: '2017-05-08T20:25:00',
        },
      ],
    });

    await userEvent.click(screen.getByRole('tab', { name: 'Comments' }));

    // The text's own line boxes, not the cell: the cell is as tall as the comment's row.
    const lines = (text: string) => {
      const range = document.createRange();
      range.selectNodeContents(screen.getByText(text));
      return new Set(Array.from(range.getClientRects()).map((rect) => Math.round(rect.top))).size;
    };
    expect(lines('May 8, 2017 8:25 PM')).toBe(1);
    expect(lines('IDIR\\LHIGGS')).toBe(1);
  });

  it('keeps the load rating columns on one line beside a long reason', async () => {
    await page.viewport(1400, 900);
    await renderPage({
      loadRating: {
        currentRating: 60,
        history: [
          {
            id: 'r1',
            rating: 60,
            reason: { code: 'OTH', description: 'Other reason '.repeat(60) },
            reasonComment: null,
            date: '2018-01-26',
            userId: 'IDIR\\LHIGGS',
            reviewedDate: '2025-05-14',
            status: 'REVIEWED',
            inspectionId: '9',
            current: true,
          },
        ],
      },
    });

    const lines = (text: string) => {
      const range = document.createRange();
      range.selectNodeContents(screen.getByText(text));
      return new Set(Array.from(range.getClientRects()).map((rect) => Math.round(rect.top))).size;
    };
    expect(lines('Jan 26, 2018')).toBe(1);
    expect(lines('May 14, 2025')).toBe(1);
    expect(lines('IDIR\\LHIGGS')).toBe(1);
    expect(lines('Reviewed')).toBe(1);
  });
});
