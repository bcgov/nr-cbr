import { Theme } from '@carbon/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { page, userEvent } from 'vitest/browser';

import StructureDetailPage from './index';

// The app's real stylesheet, Carbon included: these measure layout, which Carbon's sizes drive.
import '@/styles/index.scss';

const authorization = vi.hoisted(() => ({ canEdit: false, canDelete: false }));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => authorization }));

// A delete's outcome is a toast; asserted on what the page asked to show.
const display = vi.hoisted(() => vi.fn());
vi.mock('@/context/notification/useNotification', () => ({ useNotification: () => ({ display }) }));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const code = (value: string) => ({ code: value, description: value });

const api = vi.hoisted(() => ({
  getStructure: vi.fn(),
  getStructureMonitors: vi.fn(),
  getStructureRepairs: vi.fn(),
}));
vi.mock('@/services/APIs', () => ({
  default: {
    structureSearch: api,
    configuration: {
      getMonitoringStatusCodes: () => Promise.resolve([{ code: 'SUG', description: 'Suggested' }]),
      getMonitorFrequencyCodes: () =>
        Promise.resolve([{ code: 'INS', description: 'Every Routine Inspection' }]),
    },
  },
}));

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
  // Inside the app's Theme, which defines the colour tokens the cards and pane are painted with.
  render(
    <Theme theme="white">
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/inventory/structure/7']}>
          {/* The app's content wrapper, whose grid padding the grey pane breaks out of. */}
          <div className="cds--content">
            <Routes>
              <Route path="/inventory/structure/:structureId" element={<StructureDetailPage />} />
            </Routes>
          </div>
        </MemoryRouter>
      </QueryClientProvider>
    </Theme>,
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

  it('sets each value 8px under its bold label, as nr-fspts does', async () => {
    await page.viewport(1400, 900);
    await renderPage();

    const cell = screen.getByTestId('structure-header').querySelector('.read-only-field')!;
    const label = cell.querySelector('.read-only-field__label')!.getBoundingClientRect();
    const value = cell.querySelector('.read-only-field__value')!.getBoundingClientRect();

    expect(value.top - label.bottom).toBeCloseTo(8, 0);
    expect(getComputedStyle(cell.querySelector('.read-only-field__label')!).fontWeight).toBe('600');
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

    await userEvent.click(screen.getByTestId('structure-tab-details'));

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

    await userEvent.click(screen.getByTestId('structure-tab-details'));

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

    await userEvent.click(screen.getByTestId('structure-tab-details'));

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

  it('sets each section as a white card on the grey tab pane, its title beside an icon', async () => {
    await page.viewport(1400, 900);
    await renderPage();

    const card = screen.getByTestId('structure-information-tab');
    const pane = card.closest('.cds--tab-content')!;
    expect(getComputedStyle(card).backgroundColor).toBe('rgb(255, 255, 255)');
    expect(getComputedStyle(pane).backgroundColor).not.toBe('rgb(255, 255, 255)');

    const title = within(card).getByRole('heading', { name: 'Structure and site' });
    const icon = title.querySelector('svg')!.getBoundingClientRect();
    const text = title.querySelector('span')!.getBoundingClientRect();
    expect(icon.right).toBeLessThanOrEqual(text.left);
    expect(icon.top + icon.height / 2).toBeCloseTo(text.top + text.height / 2, 0);
  });

  it.each([1400, 400])(
    'runs the grey pane the full width of the page at %ipx, cards in line with the tabs',
    async (width) => {
      // nr-fspts' FSP page: no white margin either side of the pane.
      await page.viewport(width, 900);
      await renderPage();

      const grid = document.querySelector('.structure-detail')!.getBoundingClientRect();
      const pane = screen
        .getByTestId('structure-information-tab')
        .closest('.cds--tab-content')!
        .getBoundingClientRect();
      const tabs = screen.getByRole('tablist').getBoundingClientRect();
      const card = screen.getByTestId('structure-information-tab').getBoundingClientRect();

      expect(pane.left).toBeCloseTo(grid.left, 0);
      expect(pane.right).toBeCloseTo(grid.right, 0);
      expect(pane.bottom).toBeCloseTo(grid.bottom, 0);
      expect(card.left).toBeCloseTo(tabs.left, 0);
      // Out to the page's edges and no further, so the pane adds no sideways scroll of its own.
      expect(pane.right).toBeLessThanOrEqual(document.documentElement.clientWidth);
    },
  );

  it('wraps the incomplete-data banner on a phone rather than running off the screen', async () => {
    await page.viewport(400, 900);
    await renderPage();

    const banner = screen.getByTestId('structure-outstanding-summary').getBoundingClientRect();
    const clientWidth = document.documentElement.clientWidth;
    expect(banner.right).toBeLessThanOrEqual(clientWidth);
    // The text's own line boxes, not the elements': text set `nowrap` spills out of its box.
    for (const part of ['__title', '__subtitle']) {
      const range = document.createRange();
      range.selectNodeContents(
        document.querySelector(
          `.structure-detail__outstanding-summary .cds--inline-notification${part}`,
        )!,
      );
      for (const line of Array.from(range.getClientRects())) {
        expect(line.right).toBeLessThanOrEqual(banner.right);
      }
    }
  });

  it("lines the add dialog's first row up: labels on one line, values level with the box", async () => {
    // The read-only Status sits beside the Frequency dropdown; read in the page's bold
    // reading style they sat higher than the box and labelled louder than it.
    authorization.canEdit = true;
    api.getStructureMonitors.mockResolvedValue({
      page: { content: [], totalElements: 0, totalPages: 1, pageNumber: 0, pageSize: 10 },
      beforeInstallCount: 0,
    });
    await page.viewport(1400, 900);
    await renderPage();
    await userEvent.click(screen.getByTestId('structure-tab-monitoring'));
    await userEvent.click(await screen.findByTestId('structure-monitor-add'));
    const dialog = await screen.findByTestId('monitor-dialog');

    const numberCell = within(dialog).getByText('Monitoring Status').closest('.read-only-field')!;
    const numberLabel = numberCell.querySelector('.read-only-field__label')!;
    const numberValue = numberCell
      .querySelector('.read-only-field__value')!
      .getBoundingClientRect();
    const frequencyLabel = within(dialog).getByText('Monitoring Frequency').getBoundingClientRect();
    const frequencyBox = within(dialog).getByTestId('monitor-frequency').getBoundingClientRect();

    expect(numberLabel.getBoundingClientRect().top).toBeCloseTo(frequencyLabel.top, 0);
    expect(numberValue.top + numberValue.height / 2).toBeCloseTo(
      frequencyBox.top + frequencyBox.height / 2,
      0,
    );
    expect(getComputedStyle(numberLabel).fontWeight).toBe('400');
    // No wide empty column between the status and the frequency: the frequency starts a gap's
    // width after the status's text.
    const statusText = within(dialog).getByText('Suggested').getBoundingClientRect();
    expect(frequencyBox.left - statusText.right).toBeLessThanOrEqual(48);
  });

  /** The repairs tab with one long repair, Edit and Delete both offered — a Level 2 user. */
  const showRepairs = async (width: number) => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    api.getStructureRepairs.mockResolvedValue({
      page: {
        content: [
          {
            id: '3',
            number: 3,
            status: { code: 'REQ', description: 'Required' },
            type: { code: 'DECK', description: 'Deck planks, timber, full width replacement' },
            suggested: { userId: 'IDIR\\ASMITHSON', date: '2023-06-02' },
            required: { userId: 'IDIR\\BJONESWORTH', date: '2023-06-05' },
            completed: null,
            inspectionId: '41',
            inspectionDate: '2023-06-01',
            priority: { code: 'P1', description: 'Urgent - within 30 days' },
            completedDate: null,
            estimate: 4000,
            actualCost: null,
            quantity: 12,
            unit: 'm2',
            description: 'Replace worn planks across the north span.',
          },
        ],
        totalElements: 1,
        totalPages: 1,
        pageNumber: 0,
        pageSize: 10,
      },
      beforeInstallCount: 0,
    });
    await page.viewport(width, 900);
    await renderPage();
    await userEvent.click(screen.getByTestId('structure-tab-repairs'));
    const edit = await screen.findByTestId('structure-repair-edit-3');
    const del = screen.getByTestId('structure-repair-delete-3');
    const scroller = del.closest('.structure-detail__table-scroll')!;
    // Nothing needs a sideways scroll to reach, Actions included.
    expect(scroller.scrollWidth).toBeLessThanOrEqual(scroller.clientWidth);
    expect(del.getBoundingClientRect().right).toBeLessThanOrEqual(
      scroller.getBoundingClientRect().right,
    );
    return { edit: edit.getBoundingClientRect(), del: del.getBoundingClientRect() };
  };

  it('fits the repairs table inside its card at 1400px, Edit stacked over Delete', async () => {
    // Eleven columns: User Audits, Repair Type and Priority wrap, and the actions stack.
    const { edit, del } = await showRepairs(1400);

    expect(del.top).toBeGreaterThan(edit.bottom - 1);
  });

  it('puts Edit and Delete side by side where the table has the room', async () => {
    const { edit, del } = await showRepairs(1800);

    expect(del.top).toBeCloseTo(edit.top, 0);
    expect(del.left).toBeGreaterThan(edit.right - 1);
  });
});
