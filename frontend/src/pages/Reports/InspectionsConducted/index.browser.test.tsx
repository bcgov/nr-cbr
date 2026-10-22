import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { INSPECTIONS_CONDUCTED_STATE_KEYS } from './types';

import InspectionsConductedPage from './index';

import type { InspectionsConductedVariant } from './types';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getPortableSuperstructureStatusCodes: vi.fn(),
  getInspectionReportStatusCodes: vi.fn(),
  getInspectionTypeCodes: vi.fn(),
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
  const SHARED_LABELS = [
    'Structure #',
    'Site Type',
    'Inspection Report Status',
    'Forest District',
    'Management Area',
    'BCTS Business Area',
    'Project File ID#',
    'Br.',
    'Inspector Name',
  ];

  it("offers the Summary legacy's criteria, without Inspection Type, Area or Road Responsibility", async () => {
    renderPage('summary');

    await waitFor(() => expect(byId('summary', 'inspectionReportStatusCode')).not.toBeDisabled());
    // As text boxes and selects: the Summary also has a "Project File ID#" sort.
    for (const label of SHARED_LABELS) {
      expect(
        screen.getByLabelText(label, { selector: 'input:not([type=radio]), select' }),
      ).toBeInTheDocument();
    }
    expect(screen.getByText('Inspection Date')).toBeInTheDocument();
    for (const toggle of [
      'Close Proximity Inspection Required?',
      'Most Recent Inspections Only?',
      'Professional Inspection Required?',
    ]) {
      expect(screen.getByText(toggle)).toBeInTheDocument();
    }
    expect(screen.queryByLabelText('Inspection Type')).not.toBeInTheDocument();
    expect(screen.queryByText(/Road Responsibility/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Area/)).not.toBeInTheDocument();
  });

  it("sorts the Summary by legacy's four orders, Forest Service Road first", () => {
    renderPage('summary');

    expect(byId('summary', 'sortBy-FOREST_SERVICE_ROAD')).toBeChecked();
    for (const label of ['Project File ID#', 'User Km (Asc)', 'Inspection Date (Desc)']) {
      expect(screen.getByRole('radio', { name: label })).not.toBeChecked();
    }
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
    expect(byId('detail', 'sortBy-PROJECT_BRANCH_USER_KM')).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Road Name, User KM (Asc)' })).not.toBeChecked();
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
      expect.objectContaining({ inspectorName: 'Smith', sortBy: 'FOREST_SERVICE_ROAD' }),
      'PDF',
    );
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsConductedSummaryReport_\d{4}-\d{2}-\d{2}\.pdf$/,
    );
  });

  it("fetches the Detail's PDF, not the Summary's, and saves it under its name", async () => {
    renderPage('detail');
    type('detail', 'structureName', 'B10');

    fireEvent.click(byId('detail', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsConductedDetail).toHaveBeenCalledWith(
      expect.objectContaining({ structureName: 'B10', sortBy: 'PROJECT_BRANCH_USER_KM' }),
      'PDF',
    );
    expect(reportApi.inspectionsConductedSummary).not.toHaveBeenCalled();
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsConductedDetailReport_\d{4}-\d{2}-\d{2}\.pdf$/,
    );
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
