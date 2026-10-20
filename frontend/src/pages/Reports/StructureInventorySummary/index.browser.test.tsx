import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import StructureInventorySummaryPage, { STRUCTURE_INVENTORY_SUMMARY_STATE_KEY } from './index';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getStructureTypeClassCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getPortableSuperstructureStatusCodes: vi.fn(),
  getCulvertTypeCodes: vi.fn(),
  getSuperstructureTypeCodes: vi.fn(),
  getCulvertMaterialCodes: vi.fn(),
  getDeckTypeCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));
const reportApi = vi.hoisted(() => ({ structureInventorySummary: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, client: clientApi, report: reportApi },
}));

// The browser's save is asserted on what the page asked to save.
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
      <MemoryRouter initialEntries={['/reports/structure-inventory-summary']}>
        <StructureInventorySummaryPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (field: string) => screen.getByTestId(`structure-inventory-summary-${field}`);
const type = (field: string, value: string) => fireEvent.change(byId(field), { target: { value } });
const run = (format: 'PDF' | 'CSV' = 'PDF') => fireEvent.click(byId(`run-${format}`));

beforeEach(() => {
  sessionStorage.clear();
  display.mockReset();
  saveFile.mockReset();
  reportApi.structureInventorySummary.mockReset();
  reportApi.structureInventorySummary.mockResolvedValue(new Blob(['%PDF']));
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  api.getDeckTypeCodes.mockResolvedValue([{ code: 'TMB', description: 'Timber' }]);
  api.getCulvertMaterialCodes.mockResolvedValue([{ code: 'CSP', description: 'Corrugated steel' }]);
  api.getPortableSuperstructureStatusCodes.mockResolvedValue([
    { code: 'S', description: 'In service' },
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

describe('StructureInventorySummaryPage', () => {
  it("offers legacy's criteria, with the summary's own construction filters filled", async () => {
    renderPage();

    await waitFor(() => expect(byId('deckTypeCode')).not.toBeDisabled());
    expect(screen.getByRole('option', { name: 'TMB - Timber' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'CSP - Corrugated steel' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'S - In service' })).toBeInTheDocument();
    for (const legend of [
      'Opening Width (mm)',
      'Year Superstructure Installed',
      'Kilometres',
      'Estimated Load Restriction',
      'Estimated Replacement',
    ]) {
      expect(screen.getByText(legend)).toBeInTheDocument();
    }
    expect(screen.getByLabelText('BCTS Business Area')).toBeInTheDocument();
    expect(screen.getByText('Capital Road')).toBeInTheDocument();
  });

  it('sorts by project, branch and user km until told otherwise, and runs as PDF or CSV', () => {
    renderPage();

    expect(byId('sortBy-PROJECT_BRANCH_USER_KM')).toBeChecked();
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeInTheDocument();
    // The format is the button, not a choice beside one; legacy's Word and Excel are not offered.
    expect(screen.queryByRole('radio', { name: 'PDF' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Excel|Word/)).not.toBeInTheDocument();
  });

  it('shows a bad box once left, and Run shows every error and runs nothing', async () => {
    renderPage();

    type('yearBuiltStart', '90');
    expect(screen.queryByText('Enter a year as yyyy, e.g. 2030')).not.toBeInTheDocument();
    fireEvent.blur(byId('yearBuiltStart'));
    expect(screen.getByText('Enter a year as yyyy, e.g. 2030')).toBeInTheDocument();

    type('openingWidthMax', '1.5');
    run();

    expect(
      await screen.findByText('Opening width must be whole millimetres, e.g. 1200'),
    ).toBeInTheDocument();
    expect(reportApi.structureInventorySummary).not.toHaveBeenCalled();
  });

  it.each(['PDF', 'CSV'] as const)(
    'fetches the %s for the criteria and saves it, dated today',
    async (format) => {
      renderPage();

      type('kiloStart', '1.5');
      type('openingWidthMin', '600');
      run(format);

      await waitFor(() => expect(saveFile).toHaveBeenCalled());
      expect(reportApi.structureInventorySummary).toHaveBeenCalledWith(
        expect.objectContaining({ kiloStart: '1.5', openingWidthMin: '600' }),
        format,
      );
      expect(saveFile.mock.calls[0][1]).toMatch(
        new RegExp(
          `^StructureInventorySummaryReport_\\d{4}-\\d{2}-\\d{2}\\.${format.toLowerCase()}$`,
        ),
      );
    },
  );

  it('waits while a report is produced, and says which', async () => {
    let finish: (file: Blob) => void = () => {};
    reportApi.structureInventorySummary.mockReturnValue(
      new Promise<Blob>((resolve) => {
        finish = resolve;
      }),
    );
    renderPage();

    run('CSV');

    expect(await screen.findByRole('button', { name: 'Producing CSV…' })).toBeDisabled();
    expect(byId('run-PDF')).toBeDisabled();
    finish(new Blob(['a,b']));
    expect(await screen.findByRole('button', { name: 'Download CSV' })).toBeEnabled();
  });

  it('says so when the report could not be produced, and saves nothing', async () => {
    reportApi.structureInventorySummary.mockRejectedValue(new Error('down'));
    renderPage();

    run();

    await waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The PDF could not be produced' }),
      ),
    );
    expect(saveFile).not.toHaveBeenCalled();
  });

  it('checks the criteria for the CSV too', async () => {
    renderPage();

    type('kiloEnd', 'ten');
    run('CSV');

    expect(await screen.findByText('Kilometres must be a number, e.g. 12.5')).toBeInTheDocument();
    expect(reportApi.structureInventorySummary).not.toHaveBeenCalled();
  });

  it("loads the district's management areas, and drops the area when the district changes", async () => {
    renderPage();

    await waitFor(() => expect(byId('orgUnit')).not.toBeDisabled());
    type('orgUnit', '1');
    await screen.findByRole('option', { name: 'MA - Area of 1' });
    type('managementOrgUnit', '10');
    expect(byId('managementOrgUnit')).toHaveValue('10');

    type('orgUnit', '2');

    await screen.findByRole('option', { name: 'MA - Area of 2' });
    expect(byId('managementOrgUnit')).toHaveValue('');
  });

  it('keeps the criteria for the tab, and Clear forgets them', () => {
    const { unmount } = renderPage();
    type('forestFileId', 'R00123');
    fireEvent.click(byId('sortBy-SITE_ID'));
    unmount();

    renderPage();
    expect(byId('forestFileId')).toHaveValue('R00123');
    expect(byId('sortBy-SITE_ID')).toBeChecked();

    fireEvent.click(byId('reset'));
    expect(byId('forestFileId')).toHaveValue('');
    expect(byId('sortBy-PROJECT_BRANCH_USER_KM')).toBeChecked();
    expect(
      JSON.parse(sessionStorage.getItem(STRUCTURE_INVENTORY_SUMMARY_STATE_KEY) ?? '{}'),
    ).toMatchObject({ forestFileId: '', sortBy: 'PROJECT_BRANCH_USER_KM' });
  });

  it('reports a list that failed to load, and leaves the rest of the form working', async () => {
    api.getDeckTypeCodes.mockRejectedValue(new Error('down'));
    renderPage();

    expect(
      await screen.findByTestId('structure-inventory-summary-codes-error'),
    ).toBeInTheDocument();
    run();
    await waitFor(() => expect(reportApi.structureInventorySummary).toHaveBeenCalled());
  });
});
