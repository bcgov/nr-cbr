import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { INSPECTIONS_DUE_STATE_KEYS } from './types';

import InspectionsDuePage from './index';

import type { InspectionsDueVariant } from './types';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getSpecialEquipmentCodes: vi.fn(),
  getPortableSuperstructureStatusCodes: vi.fn(),
  getStructureTypeClassCodes: vi.fn(),
  getStructureInspectionStatusCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));
const reportApi = vi.hoisted(() => ({
  inspectionsDueSummary: vi.fn(),
  inspectionsDueDetailed: vi.fn(),
  inspectionsBlank: vi.fn(),
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

const ID: Record<InspectionsDueVariant, string> = {
  summary: 'inspections-due-summary',
  detailed: 'inspections-due-detailed',
  blank: 'inspections-blank',
};

const renderPage = (variant: InspectionsDueVariant) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/reports/${ID[variant]}`]}>
        <InspectionsDuePage variant={variant} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (variant: InspectionsDueVariant, field: string) =>
  screen.getByTestId(`${ID[variant]}-${field}`);
const type = (variant: InspectionsDueVariant, field: string, value: string) =>
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
  api.getStructureTypeClassCodes.mockResolvedValue([
    { code: 'PB', description: 'Permanent Bridge' },
  ]);
  api.getStructureInspectionStatusCodes.mockResolvedValue([
    { code: 'OVR', description: 'Overdue' },
  ]);
  api.getSpecialEquipmentCodes.mockResolvedValue([{ code: 'BOAT', description: 'Boat' }]);
  api.getForestDistricts.mockResolvedValue([
    { orgUnitNo: '1', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
  ]);
});

describe('InspectionsDuePage', () => {
  const SHARED_LABELS = [
    'Inspection Status',
    'Site Type',
    'Type/Class',
    'Site Status',
    'Close Proximity Inspection Required?',
    'Forest District',
    'Management Area',
    'BCTS Business Area',
    'Project File ID#',
    'Br.',
    'Special Access Requirements',
    'Special Equipment Requirements',
  ];

  it.each(['summary', 'detailed'] as const)(
    "offers the %s report legacy's criteria and its two orders, without Structure # or Area",
    async (variant) => {
      renderPage(variant);

      await waitFor(() => expect(byId(variant, 'structureTypeClassCode')).not.toBeDisabled());
      for (const label of SHARED_LABELS) {
        expect(
          screen.getByLabelText(label, { selector: 'input:not([type=radio]), select' }),
        ).toBeInTheDocument();
      }
      expect(screen.getByText('Next Planned Inspection Date')).toBeInTheDocument();
      expect(screen.getByText('No Next Planned Inspection Date')).toBeInTheDocument();
      expect(screen.queryByLabelText('Structure #')).not.toBeInTheDocument();
      expect(screen.queryByText(/^Area/)).not.toBeInTheDocument();
      expect(byId(variant, 'sortBy-FOREST_SERVICE_ROAD')).toBeChecked();
      expect(byId(variant, 'sortBy-FOREST_FILE_ID')).not.toBeChecked();
    },
  );

  it('asks the Blank for a Structure #, with no "no date" switch and no Sort By', async () => {
    renderPage('blank');

    await waitFor(() => expect(byId('blank', 'structureTypeClassCode')).not.toBeDisabled());
    expect(byId('blank', 'structureName')).toBeInTheDocument();
    for (const label of SHARED_LABELS) {
      expect(screen.getByLabelText(label, { selector: 'input, select' })).toBeInTheDocument();
    }
    expect(screen.queryByText('No Next Planned Inspection Date')).not.toBeInTheDocument();
    expect(screen.queryByText('Sort By')).not.toBeInTheDocument();
  });

  it("keeps legacy's Close Proximity either/Yes/No and Special Equipment's None", async () => {
    renderPage('summary');

    await waitFor(() => expect(byId('summary', 'specialEquipmentCode')).not.toBeDisabled());
    expect(
      within(byId('summary', 'closeProximity'))
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['Either', 'Yes', 'No']);
    expect(
      within(byId('summary', 'specialEquipmentCode'))
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['Any requirement', 'None', 'BOAT - Boat']);
  });

  it('rejects a date that is not yyyy/mm/dd, beside its box, and runs nothing', async () => {
    renderPage('detailed');

    type('detailed', 'nextPlannedInspectionDateStart', '2026/02/30');
    fireEvent.click(byId('detailed', 'run-PDF'));

    expect(
      await screen.findByText('Enter a date as yyyy/mm/dd, e.g. 2026/01/31'),
    ).toBeInTheDocument();
    expect(display).not.toHaveBeenCalled();
  });

  it('rejects a range that ends before it starts', () => {
    renderPage('blank');

    type('blank', 'nextPlannedInspectionDateStart', '2026/06/01');
    type('blank', 'nextPlannedInspectionDateEnd', '2026/01/31');
    fireEvent.click(byId('blank', 'run-CSV'));

    expect(screen.getByText('The end date cannot be before the start date')).toBeInTheDocument();
    expect(display).not.toHaveBeenCalled();
  });

  it('sets the dates aside — cleared and disabled — for No Next Planned Inspection Date', async () => {
    renderPage('summary');
    type('summary', 'nextPlannedInspectionDateStart', '2026/13/01');
    type('summary', 'nextPlannedInspectionDateEnd', '2026/06/30');

    fireEvent.click(byId('summary', 'noNextPlannedInspectionDate'));

    expect(byId('summary', 'nextPlannedInspectionDateStart')).toHaveValue('');
    expect(byId('summary', 'nextPlannedInspectionDateStart')).toBeDisabled();
    expect(byId('summary', 'nextPlannedInspectionDateEnd')).toHaveValue('');
    expect(byId('summary', 'nextPlannedInspectionDateEnd')).toBeDisabled();

    // The bad date went with them: nothing stops the run.
    fireEvent.click(byId('summary', 'run-CSV'));
    await waitFor(() =>
      expect(reportApi.inspectionsDueSummary).toHaveBeenCalledWith(
        expect.objectContaining({ noNextPlannedInspectionDate: true }),
        'CSV',
      ),
    );
  });

  it.each([
    ['summary', 'inspectionsDueSummary', 'InspectionsDueSummaryReport'],
    ['detailed', 'inspectionsDueDetailed', 'InspectionsDueDetailedReport'],
    ['blank', 'inspectionsBlank', 'InspectionsBlankReport'],
  ] as const)('fetches the %s CSV and saves it under its name', async (variant, method, name) => {
    renderPage(variant);

    fireEvent.click(byId(variant, 'run-CSV'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi[method]).toHaveBeenCalledWith(expect.any(Object), 'CSV');
    expect(saveFile.mock.calls[0][1]).toMatch(new RegExp(`^${name}_\\d{4}-\\d{2}-\\d{2}\\.csv$`));
  });

  it("fetches the Summary's PDF for the criteria and saves it under its name, dated today", async () => {
    renderPage('summary');
    type('summary', 'forestFileId', 'R00123');

    fireEvent.click(byId('summary', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsDueSummary).toHaveBeenCalledWith(
      expect.objectContaining({ forestFileId: 'R00123', sortBy: 'FOREST_SERVICE_ROAD' }),
      'PDF',
    );
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsDueSummaryReport_\d{4}-\d{2}-\d{2}\.pdf$/,
    );
  });

  it("fetches the Detailed's PDF, not the Summary's, and saves it under its name", async () => {
    renderPage('detailed');

    fireEvent.click(byId('detailed', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsDueDetailed).toHaveBeenCalledWith(expect.any(Object), 'PDF');
    expect(reportApi.inspectionsDueSummary).not.toHaveBeenCalled();
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsDueDetailedReport_\d{4}-\d{2}-\d{2}\.pdf$/,
    );
  });

  it.each([
    ['summary', ['Download CSV', 'Download KMZ', 'Download PDF']],
    ['detailed', ['Download CSV', 'Download KMZ', 'Download PDF']],
    ['blank', ['Download CSV', 'Download PDF']],
  ] as const)('offers the %s report a KMZ only when it is a Due report', (variant, buttons) => {
    renderPage(variant);

    expect(
      screen.getAllByRole('button', { name: /^Download / }).map((button) => button.textContent),
    ).toEqual(buttons);
  });

  it("fetches the Detailed's KMZ and saves it under the report's name", async () => {
    renderPage('detailed');

    fireEvent.click(byId('detailed', 'run-KMZ'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsDueDetailed).toHaveBeenCalledWith(expect.any(Object), 'KMZ');
    expect(saveFile.mock.calls[0][1]).toMatch(
      /^InspectionsDueDetailedReport_\d{4}-\d{2}-\d{2}\.kmz$/,
    );
  });

  it("fetches the Blank's PDF and saves it under its name", async () => {
    renderPage('blank');
    type('blank', 'structureName', 'B10');

    fireEvent.click(byId('blank', 'run-PDF'));

    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(reportApi.inspectionsBlank).toHaveBeenCalledWith(
      expect.objectContaining({ structureName: 'B10' }),
      'PDF',
    );
    expect(saveFile.mock.calls[0][1]).toMatch(/^InspectionsBlankReport_\d{4}-\d{2}-\d{2}\.pdf$/);
  });

  it('keeps each report its own criteria for the tab, and Clear goes back to the defaults', async () => {
    const { unmount } = renderPage('detailed');
    await waitFor(() => expect(byId('detailed', 'closeProximity')).not.toBeDisabled());
    type('detailed', 'forestFileId', 'R00123');
    type('detailed', 'closeProximity', 'N');
    unmount();

    renderPage('detailed');
    expect(byId('detailed', 'forestFileId')).toHaveValue('R00123');
    await waitFor(() => expect(byId('detailed', 'closeProximity')).toHaveValue('N'));
    expect(sessionStorage.getItem(INSPECTIONS_DUE_STATE_KEYS.summary)).toBeNull();

    fireEvent.click(byId('detailed', 'reset'));

    expect(byId('detailed', 'forestFileId')).toHaveValue('');
    expect(byId('detailed', 'closeProximity')).toHaveValue('');
  });
});
