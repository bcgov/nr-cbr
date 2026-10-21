import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RepairsSummaryPage, { REPAIRS_SUMMARY_STATE_KEY } from './index';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getPortableSuperstructureStatusCodes: vi.fn(),
  getRepairStatusCodes: vi.fn(),
  getRepairPriorityCodes: vi.fn(),
  getRepairClassCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));
const reportApi = vi.hoisted(() => ({ repairsSummary: vi.fn() }));

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

const renderPage = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/reports/repairs-summary']}>
        <RepairsSummaryPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (field: string) => screen.getByTestId(`repairs-summary-${field}`);
const type = (field: string, value: string) => fireEvent.change(byId(field), { target: { value } });
const optionTexts = (field: string) =>
  Array.from((byId(field) as HTMLSelectElement).options).map((option) => option.textContent);

beforeEach(() => {
  sessionStorage.clear();
  display.mockReset();
  saveFile.mockReset();
  reportApi.repairsSummary.mockReset();
  reportApi.repairsSummary.mockResolvedValue(new Blob(['%PDF']));
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  api.getRepairStatusCodes.mockResolvedValue([
    { code: 'COM', description: 'Completed' },
    { code: 'REQ', description: 'Required' },
  ]);
  api.getRepairPriorityCodes.mockResolvedValue([
    { code: 'H', description: 'High' },
    { code: 'U', description: 'Urgent' },
  ]);
  api.getRepairClassCodes.mockResolvedValue([
    { code: 'N', description: 'Non-structural' },
    { code: 'S', description: 'Structural' },
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

describe('RepairsSummaryPage', () => {
  it("offers legacy's criteria, less Admin Area and Road Responsibility", async () => {
    renderPage();

    await waitFor(() => expect(byId('repairClassCode')).not.toBeDisabled());
    for (const label of [
      'Structure #',
      'Site #',
      'Site Status',
      'Site Type',
      'Repair Status',
      'Repair Priority',
      'Structural Safety',
      'Forest District',
      'Management Area',
      'BCTS Business Area',
      'Forest Service Road',
      'Project File ID#',
      'Br.',
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('Designated Maintainer')).toBeInTheDocument();
    expect(screen.getByText('Downrated Structure?')).toBeInTheDocument();
    expect(screen.getByText('Most Recent Inspections Only?')).toBeInTheDocument();
    expect(screen.queryByText(/Admin Area/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Road Responsibility/)).not.toBeInTheDocument();
  });

  it("lists legacy's groups of codes after the codes themselves", async () => {
    renderPage();

    await waitFor(() => expect(byId('repairClassCode')).not.toBeDisabled());
    expect(optionTexts('repairStatusCode')).toEqual([
      'Any status',
      'COM - Completed',
      'REQ - Required',
      'SOR - Suggested or Required',
      'OUT - Outstanding',
    ]);
    expect(optionTexts('repairPriorityCode')).toEqual([
      'Any priority',
      'H - High',
      'U - Urgent',
      'U&H&M - Urgent, High and Medium',
      'H&M - High and Medium',
      'M&L - Medium and Low',
    ]);
    expect(optionTexts('repairClassCode')).toEqual([
      'Any structural safety',
      'N - Non-structural',
      'S - Structural',
      'N&S - Non-structural and Structural',
    ]);
  });

  it("starts at legacy's defaults: Outstanding repairs, sorted by project and branch", async () => {
    renderPage();

    await waitFor(() => expect(byId('repairStatusCode')).toHaveValue('OUT'));
    expect(byId('sortBy-PROJECT_BRANCH_KM')).toBeChecked();
    expect(screen.getByLabelText('Road Name, KM User')).not.toBeChecked();
    expect(screen.getByLabelText('Site Id')).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeInTheDocument();
  });

  it.each(['PDF', 'CSV'] as const)(
    'fetches the %s for the criteria and saves it under its name, dated today',
    async (format) => {
      renderPage();
      type('structureName', 'B10');

      fireEvent.click(byId(`run-${format}`));

      await waitFor(() => expect(saveFile).toHaveBeenCalled());
      expect(reportApi.repairsSummary).toHaveBeenCalledWith(
        expect.objectContaining({ structureName: 'B10', repairStatusCode: 'OUT' }),
        format,
      );
      expect(saveFile.mock.calls[0][1]).toMatch(
        new RegExp(`^RepairsSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.${format.toLowerCase()}$`),
      );
    },
  );

  it('says so when the PDF could not be produced, and saves nothing', async () => {
    reportApi.repairsSummary.mockRejectedValue(new Error('down'));
    renderPage();

    fireEvent.click(byId('run-PDF'));

    await waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The PDF could not be produced' }),
      ),
    );
    expect(saveFile).not.toHaveBeenCalled();
  });

  it('keeps the criteria for the tab, and Clear goes back to the defaults', async () => {
    const { unmount } = renderPage();
    await waitFor(() => expect(byId('repairPriorityCode')).not.toBeDisabled());
    type('structureName', 'B10');
    type('repairPriorityCode', 'H&M');
    fireEvent.click(byId('sortBy-SITE_ID'));
    unmount();

    renderPage();

    expect(byId('structureName')).toHaveValue('B10');
    await waitFor(() => expect(byId('repairPriorityCode')).toHaveValue('H&M'));
    expect(byId('sortBy-SITE_ID')).toBeChecked();
    expect(JSON.parse(sessionStorage.getItem(REPAIRS_SUMMARY_STATE_KEY) ?? '{}')).toMatchObject({
      structureName: 'B10',
    });

    fireEvent.click(byId('reset'));

    expect(byId('structureName')).toHaveValue('');
    expect(byId('repairStatusCode')).toHaveValue('OUT');
    expect(byId('sortBy-PROJECT_BRANCH_KM')).toBeChecked();
  });

  it("loads the district's management areas, and drops the area when the district changes", async () => {
    renderPage();

    await waitFor(() => expect(byId('orgUnit')).not.toBeDisabled());
    type('orgUnit', '1');
    await screen.findByRole('option', { name: 'MA - Area of 1' });
    type('managementOrgUnit', '10');
    type('orgUnit', '2');

    await screen.findByRole('option', { name: 'MA - Area of 2' });
    expect(byId('managementOrgUnit')).toHaveValue('');
  });
});
