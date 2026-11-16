import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RepairsSummaryPage, { REPAIRS_SUMMARY_STATE_KEY } from './index';

import {
  multiSelectOptions,
  multiSelectSelected,
  toggleMultiSelect,
} from '@/config/tests/multiSelect';

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

    await waitFor(() => expect(byId('repairStatusCode')).not.toBeDisabled());
    for (const label of [
      'Structure #',
      'Site #',
      'Site Type',
      'Repair Status',
      'Forest Service Road',
      'Project File ID#',
      'Br.',
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    // Multi-selects: their labels name a list box rather than a form control.
    for (const label of [
      'Site Status',
      'Repair Priority',
      'Structural Safety',
      'Forest District',
      'Management Area',
      'BCTS Business Area',
    ]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('Designated Maintainer')).toBeInTheDocument();
    expect(screen.getByText('Downrated Structure?')).toBeInTheDocument();
    expect(screen.getByText('Most Recent Inspections Only?')).toBeInTheDocument();
    expect(screen.queryByText(/Admin Area/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Road Responsibility/)).not.toBeInTheDocument();
  });

  it("keeps legacy's groups of statuses, and lists priorities and safety as codes only", async () => {
    // Repair Status is still one choice, so its groups stay. Priority and Structural Safety are
    // multi-selects now: ticking several replaces legacy's ready-made groups ("U&H&M", "N&S").
    renderPage();

    await waitFor(() => expect(byId('repairStatusCode')).not.toBeDisabled());
    expect(optionTexts('repairStatusCode')).toEqual([
      'Any status',
      'COM - Completed',
      'REQ - Required',
      'SOR - Suggested or Required',
      'OUT - Outstanding',
    ]);
    await waitFor(async () =>
      expect(await multiSelectOptions('repairs-summary-repairPriorityCode')).toEqual([
        'H - High',
        'U - Urgent',
      ]),
    );
    expect(await multiSelectOptions('repairs-summary-repairClassCode')).toEqual([
      'N - Non-structural',
      'S - Structural',
    ]);
  });

  it('sends every priority and safety class ticked', async () => {
    renderPage();
    await waitFor(async () =>
      expect(await multiSelectOptions('repairs-summary-repairPriorityCode')).toHaveLength(2),
    );

    await toggleMultiSelect('repairs-summary-repairPriorityCode', 'H - High', 'U - Urgent');
    await toggleMultiSelect('repairs-summary-repairClassCode', 'S - Structural');
    fireEvent.click(byId('run-PDF'));

    await waitFor(() =>
      expect(reportApi.repairsSummary).toHaveBeenCalledWith(
        expect.objectContaining({ repairPriorityCode: ['H', 'U'], repairClassCode: ['S'] }),
        'PDF',
      ),
    );
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
    await waitFor(async () =>
      expect(await multiSelectOptions('repairs-summary-repairPriorityCode')).toHaveLength(2),
    );
    type('structureName', 'B10');
    await toggleMultiSelect('repairs-summary-repairPriorityCode', 'H - High');
    fireEvent.click(byId('sortBy-SITE_ID'));
    unmount();

    renderPage();

    expect(byId('structureName')).toHaveValue('B10');
    await waitFor(async () =>
      expect(await multiSelectSelected('repairs-summary-repairPriorityCode')).toEqual(['H - High']),
    );
    expect(byId('sortBy-SITE_ID')).toBeChecked();
    expect(JSON.parse(sessionStorage.getItem(REPAIRS_SUMMARY_STATE_KEY) ?? '{}')).toMatchObject({
      structureName: 'B10',
      repairPriorityCode: ['H'],
    });

    fireEvent.click(byId('reset'));

    expect(byId('structureName')).toHaveValue('');
    expect(byId('repairStatusCode')).toHaveValue('OUT');
    expect(byId('sortBy-PROJECT_BRANCH_KM')).toBeChecked();
  });

  it("loads the districts' management areas, and drops the areas when a district is removed", async () => {
    renderPage();
    await waitFor(async () =>
      expect(await multiSelectOptions('repairs-summary-orgUnit')).toHaveLength(2),
    );
    await toggleMultiSelect('repairs-summary-orgUnit', 'DPG - Prince George');
    await waitFor(async () =>
      expect(await multiSelectOptions('repairs-summary-managementOrgUnit')).toEqual([
        'MA - Area of 1',
      ]),
    );
    await toggleMultiSelect('repairs-summary-managementOrgUnit', 'MA - Area of 1');

    // Adding a district keeps the area: it still belongs to a district chosen.
    await toggleMultiSelect('repairs-summary-orgUnit', 'DMH - 100 Mile House');
    fireEvent.click(byId('run-PDF'));
    await waitFor(() =>
      expect(reportApi.repairsSummary).toHaveBeenLastCalledWith(
        expect.objectContaining({ orgUnit: ['1', '2'], managementOrgUnit: ['10'] }),
        'PDF',
      ),
    );

    // Removing one drops it: the area may be the removed district's, invisible in the list.
    await toggleMultiSelect('repairs-summary-orgUnit', 'DPG - Prince George');
    fireEvent.click(byId('run-PDF'));
    await waitFor(() =>
      expect(reportApi.repairsSummary).toHaveBeenLastCalledWith(
        expect.objectContaining({ orgUnit: ['2'], managementOrgUnit: [] }),
        'PDF',
      ),
    );
  });
});
