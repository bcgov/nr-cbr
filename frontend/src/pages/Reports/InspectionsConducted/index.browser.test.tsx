import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { INSPECTIONS_CONDUCTED_STATE_KEYS } from './types';

import InspectionsConductedPage from './index';

import type { InspectionsConductedVariant } from './types';

import {
  multiSelectDisabled,
  multiSelectOptions,
  toggleMultiSelect,
} from '@/config/tests/multiSelect';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getPortableSuperstructureStatusCodes: vi.fn(),
  getInspectionReportStatusCodes: vi.fn(),
  getInspectionTypeCodes: vi.fn(),
  getAreas: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));
const reportApi = vi.hoisted(() => ({
  inspectionsConductedSummary: vi.fn(),
  inspectionsConductedDetail: vi.fn(),
}));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, client: clientApi, report: reportApi },
}));

// The browser's save is asserted on what the page asked to save.
const saveFile = vi.hoisted(() => vi.fn());
vi.mock('@/utils/saveFile', () => ({ saveFile }));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const display = vi.hoisted(() => vi.fn());
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const ID: Record<InspectionsConductedVariant, string> = {
  summary: 'inspections-conducted-summary',
  detail: 'inspections-conducted-detail',
};

const renderPage = (variant: InspectionsConductedVariant) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/reports/${ID[variant]}`]}>
        <InspectionsConductedPage variant={variant} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (variant: InspectionsConductedVariant, field: string) =>
  screen.getByTestId(`${ID[variant]}-${field}`);
const type = (variant: InspectionsConductedVariant, field: string, value: string) =>
  fireEvent.change(byId(variant, field), { target: { value } });

/** Ticks these Forest Districts on the Detail, once its list has loaded — the Detail needs one. */
const pickDistricts = async (...texts: string[]) => {
  const box = `${ID.detail}-orgUnit`;
  await waitFor(() => expect(multiSelectDisabled(box)).toBe(false));
  await toggleMultiSelect(box, ...texts);
};

/** Nine districts, one more than the Detail may ask for. */
const NINE_DISTRICTS = Array.from({ length: 9 }, (_, index) => ({
  orgUnitNo: String(index + 1),
  orgUnitCode: `D${index + 1}`,
  orgUnitName: `District ${index + 1}`,
}));

beforeEach(() => {
  sessionStorage.clear();
  display.mockReset();
  saveFile.mockReset();
  Object.values(reportApi).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue(new Blob(['%PDF']));
  });
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  api.getInspectionReportStatusCodes.mockResolvedValue([{ code: 'ACC', description: 'Accepted' }]);
  api.getInspectionTypeCodes.mockResolvedValue([
    { code: 'ROUT', description: 'Routine' },
    { code: 'SPEC', description: 'Special' },
  ]);
  api.getForestDistricts.mockResolvedValue([
    { orgUnitNo: '1', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
    { orgUnitNo: '2', orgUnitCode: 'DMH', orgUnitName: '100 Mile House' },
  ]);
  api.getManagementAreas.mockImplementation((district: string) =>
    Promise.resolve([
      { orgUnitNo: `${district}0`, orgUnitCode: 'MA', orgUnitName: `Area of ${district}` },
    ]),
  );
});

describe('InspectionsConductedPage', () => {
  const SHARED_LABELS = ['Structure #', 'Project File ID#', 'Br.', 'Inspector Name'];
  // Multi-selects: their labels name a list box rather than a form control.
  const MULTI_SELECT_LABELS = [
    'Site Type',
    'Inspection Report Status',
    'Forest District',
    'Management Area',
    'BCTS Business Area',
  ];

  it("offers the Summary legacy's criteria, Area among them, without Inspection Type or Road Responsibility", async () => {
    renderPage('summary');

    await waitFor(async () =>
      expect(
        await multiSelectOptions('inspections-conducted-summary-inspectionReportStatusCode'),
      ).toEqual(['ACC - Accepted']),
    );
    // As text boxes and selects: the Summary also has a "Project File ID#" sort.
    for (const label of SHARED_LABELS) {
      expect(
        screen.getByLabelText(label, { selector: 'input:not([type=radio]), select' }),
      ).toBeInTheDocument();
    }
    for (const label of MULTI_SELECT_LABELS) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('Inspection Date (yyyy/mm)')).toBeInTheDocument();
    for (const toggle of [
      'Close Proximity Inspection Required?',
      'Most Recent Inspections Only?',
      'Professional Inspection Required?',
    ]) {
      expect(screen.getByText(toggle)).toBeInTheDocument();
    }
    expect(screen.queryByLabelText('Inspection Type')).not.toBeInTheDocument();
    expect(screen.queryByText(/Road Responsibility/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('Area')).toBeInTheDocument();
  });

  it("sorts the Summary by legacy's orders less Forest Service Road, Project File ID# first", () => {
    renderPage('summary');

    expect(byId('summary', 'sortBy-FOREST_FILE_ID')).toBeChecked();
    for (const label of ['User Km (Asc)', 'Inspection Date (Desc)']) {
      expect(screen.getByRole('radio', { name: label })).not.toBeChecked();
    }
    // CBR no longer holds road names, so it cannot sort by them.
    expect(screen.queryByRole('radio', { name: 'Forest Service Road' })).not.toBeInTheDocument();
  });

  it('asks the Detail for the inspection type, Routine unless changed, and sorts by project first', async () => {
    renderPage('detail');

    await waitFor(() => expect(byId('detail', 'inspectionTypeCode')).toHaveValue('ROUT'));
    expect(screen.getByRole('option', { name: 'SPEC - Special' })).toBeInTheDocument();
    // As text boxes and selects: the Summary also has a "Project File ID#" sort.
    for (const label of SHARED_LABELS) {
      expect(
        screen.getByLabelText(label, { selector: 'input:not([type=radio]), select' }),
      ).toBeInTheDocument();
    }
    for (const label of MULTI_SELECT_LABELS) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(byId('detail', 'sortBy-PROJECT_BRANCH_USER_KM')).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Inspection Date (Desc)' })).not.toBeChecked();
    expect(
      screen.queryByRole('radio', { name: 'Road Name, User KM (Asc)' }),
    ).not.toBeInTheDocument();
  });

  it('rejects a month that is not yyyy/mm, beside its box, and runs nothing', async () => {
    renderPage('summary');

    type('summary', 'inspectionDateStart', '2026-01');
    fireEvent.click(byId('summary', 'run-PDF'));

    expect(await screen.findByText('Enter a month as yyyy/mm, e.g. 2026/01')).toBeInTheDocument();
    expect(display).not.toHaveBeenCalled();
  });

  it('rejects a range that ends before it starts', () => {
    renderPage('detail');

    type('detail', 'inspectionDateStart', '2026/06');
    type('detail', 'inspectionDateEnd', '2026/01');
    fireEvent.click(byId('detail', 'run-CSV'));

    expect(screen.getByText('The end month cannot be before the start month')).toBeInTheDocument();
    expect(display).not.toHaveBeenCalled();
  });

  it.each([
    ['summary', 'inspectionsConductedSummary', 'InspectionsConductedSummaryReport'],
    ['detail', 'inspectionsConductedDetail', 'InspectionsConductedDetailReport'],
  ] as const)('fetches the %s CSV and saves it under its name', async (variant, method, name) => {
    renderPage(variant);
    if (variant === 'detail') {
      await pickDistricts('DPG - Prince George');
    }

    fireEvent.click(byId(variant, 'run-CSV'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi[method]).toHaveBeenCalledWith(expect.any(Object), 'CSV');
    expect(saveFile.mock.calls[0][1]).toMatch(new RegExp(`^${name}_\\d{4}-\\d{2}-\\d{2}\\.csv$`));
  });

  it("fetches the Summary's PDF for the criteria and saves it under its name, dated today", async () => {
    renderPage('summary');
    type('summary', 'inspectorName', 'Smith');

    fireEvent.click(byId('summary', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsConductedSummary).toHaveBeenCalledWith(
      expect.objectContaining({ inspectorName: 'Smith', sortBy: 'FOREST_FILE_ID' }),
      'PDF',
    );
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsConductedSummaryReport_\d{4}-\d{2}-\d{2}\.pdf$/,
    );
  });

  it("fetches the Summary's KMZ and saves it under the report's name", async () => {
    renderPage('summary');

    expect(
      screen.getAllByRole('button', { name: /^Download / }).map((button) => button.textContent),
    ).toEqual(['Download CSV', 'Download KMZ', 'Download PDF']);
    fireEvent.click(byId('summary', 'run-KMZ'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsConductedSummary).toHaveBeenCalledWith(expect.any(Object), 'KMZ');
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsConductedSummaryReport_\d{4}-\d{2}-\d{2}\.kmz$/,
    );
  });

  it("fetches the Detail's PDF, not the Summary's, and saves it under its name", async () => {
    renderPage('detail');
    type('detail', 'structureName', 'B10');
    await pickDistricts('DPG - Prince George');

    fireEvent.click(byId('detail', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsConductedDetail).toHaveBeenCalledWith(
      expect.objectContaining({
        structureName: 'B10',
        orgUnit: ['1'],
        sortBy: 'PROJECT_BRANCH_USER_KM',
      }),
      'PDF',
    );
    expect(reportApi.inspectionsConductedSummary).not.toHaveBeenCalled();
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsConductedDetailReport_\d{4}-\d{2}-\d{2}\.pdf$/,
    );
  });

  it.each(['CSV', 'KMZ', 'PDF'] as const)(
    'refuses the Detail as a %s until an Area or a Forest District is picked',
    async (format) => {
      renderPage('detail');

      fireEvent.click(byId('detail', `run-${format}`));

      expect(
        await screen.findByText('Select an Area or at least one Forest District.'),
      ).toBeInTheDocument();
      expect(reportApi.inspectionsConductedDetail).not.toHaveBeenCalled();
    },
  );

  it('runs the Detail for an Area alone', async () => {
    api.getAreas.mockResolvedValue([
      { orgUnitNo: '1833', orgUnitCode: 'NOR', orgUnitName: 'Northern', districts: ['1'] },
    ]);
    renderPage('detail');
    await waitFor(() => expect(byId('detail', 'areaOrgUnit')).not.toBeDisabled());
    await screen.findByRole('option', { name: 'NOR - Northern' });
    type('detail', 'areaOrgUnit', '1833');

    fireEvent.click(byId('detail', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsConductedDetail).toHaveBeenCalledWith(
      expect.objectContaining({ areaOrgUnit: '1833', orgUnit: [] }),
      'PDF',
    );
  });

  it('refuses more than eight Forest Districts on the Detail, and runs with eight', async () => {
    api.getForestDistricts.mockResolvedValue(NINE_DISTRICTS);
    renderPage('detail');
    const all = NINE_DISTRICTS.map((unit) => `${unit.orgUnitCode} - ${unit.orgUnitName}`);
    await pickDistricts(...all);

    fireEvent.click(byId('detail', 'run-PDF'));

    expect(await screen.findByText('Select no more than 8 Forest Districts.')).toBeInTheDocument();
    expect(reportApi.inspectionsConductedDetail).not.toHaveBeenCalled();

    await toggleMultiSelect(`${ID.detail}-orgUnit`, all[8]);
    await waitFor(() =>
      expect(screen.queryByText('Select no more than 8 Forest Districts.')).toBeNull(),
    );
    fireEvent.click(byId('detail', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsConductedDetail).toHaveBeenCalledWith(
      expect.objectContaining({ orgUnit: ['1', '2', '3', '4', '5', '6', '7', '8'] }),
      'PDF',
    );
  });

  it('runs the Summary with neither an Area nor a Forest District', async () => {
    renderPage('summary');

    fireEvent.click(byId('summary', 'run-PDF'));

    await waitFor(() => expect(reportApi.inspectionsConductedSummary).toHaveBeenCalled());
    expect(screen.queryByText('Select an Area or at least one Forest District.')).toBeNull();
  });

  it('keeps each report its own criteria for the tab, and Clear goes back to its defaults', async () => {
    const { unmount } = renderPage('detail');
    await waitFor(() => expect(byId('detail', 'inspectionTypeCode')).not.toBeDisabled());
    type('detail', 'structureName', 'B10');
    type('detail', 'inspectionTypeCode', 'SPEC');
    unmount();

    renderPage('detail');
    expect(byId('detail', 'structureName')).toHaveValue('B10');
    await waitFor(() => expect(byId('detail', 'inspectionTypeCode')).toHaveValue('SPEC'));
    expect(sessionStorage.getItem(INSPECTIONS_CONDUCTED_STATE_KEYS.summary)).toBeNull();

    fireEvent.click(byId('detail', 'reset'));

    expect(byId('detail', 'structureName')).toHaveValue('');
    expect(byId('detail', 'inspectionTypeCode')).toHaveValue('ROUT');
  });
});
