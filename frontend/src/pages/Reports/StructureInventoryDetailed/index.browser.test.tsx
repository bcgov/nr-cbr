import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import StructureInventoryDetailedPage, { STRUCTURE_INVENTORY_DETAILED_STATE_KEY } from './index';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getPortableSuperstructureStatusCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));
const reportApi = vi.hoisted(() => ({ structureInventoryDetailed: vi.fn() }));

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
      <MemoryRouter initialEntries={['/reports/structure-inventory-detailed']}>
        <StructureInventoryDetailedPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (field: string) => screen.getByTestId(`structure-inventory-detailed-${field}`);
const type = (field: string, value: string) => fireEvent.change(byId(field), { target: { value } });
const run = (format: 'PDF' | 'CSV' | 'KMZ' = 'PDF') => fireEvent.click(byId(`run-${format}`));

beforeEach(() => {
  sessionStorage.clear();
  display.mockReset();
  saveFile.mockReset();
  reportApi.structureInventoryDetailed.mockReset();
  reportApi.structureInventoryDetailed.mockResolvedValue(new Blob(['%PDF']));
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
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

describe('StructureInventoryDetailedPage', () => {
  it("offers legacy's Detailed criteria — the Summary's without its construction filters", async () => {
    renderPage();

    await waitFor(() => expect(byId('portableSuperstructureStatusCode')).not.toBeDisabled());
    expect(screen.getByRole('option', { name: 'S - In service' })).toBeInTheDocument();
    for (const legend of [
      'Year Built',
      'Kilometres',
      'Estimated Load Restriction',
      'Estimated Replacement',
    ]) {
      expect(screen.getByText(legend)).toBeInTheDocument();
    }
    expect(screen.getByLabelText('BCTS Business Area')).toBeInTheDocument();
    expect(screen.getByText('Capital Road')).toBeInTheDocument();
    for (const summaryOnly of ['Type/Class', 'Deck Type', 'Culvert Material']) {
      expect(screen.queryByLabelText(summaryOnly)).not.toBeInTheDocument();
    }
    expect(screen.queryByText('Opening Width (mm)')).not.toBeInTheDocument();
  });

  it('sorts by project, branch and user km until told otherwise, and runs as CSV, KMZ or PDF', () => {
    renderPage();

    expect(byId('sortBy-PROJECT_BRANCH_USER_KM')).toBeChecked();
    expect(
      screen.getAllByRole('button', { name: /^Download / }).map((button) => button.textContent),
    ).toEqual(['Download CSV', 'Download KMZ', 'Download PDF']);
  });

  it('shows a bad box once left, and a Download shows every error and runs nothing', async () => {
    renderPage();

    type('yearBuiltStart', '90');
    expect(screen.queryByText('Enter a year as yyyy, e.g. 2030')).not.toBeInTheDocument();
    fireEvent.blur(byId('yearBuiltStart'));
    expect(screen.getByText('Enter a year as yyyy, e.g. 2030')).toBeInTheDocument();

    type('kiloEnd', 'ten');
    run('KMZ');

    expect(await screen.findByText('Kilometres must be a number, e.g. 12.5')).toBeInTheDocument();
    expect(display).not.toHaveBeenCalled();
  });

  it.each(['PDF', 'CSV'] as const)(
    "fetches the %s for the criteria and saves it under legacy's name, dated today",
    async (format) => {
      renderPage();

      type('kiloStart', '1.5');
      run(format);

      await waitFor(() => expect(saveFile).toHaveBeenCalled());
      expect(reportApi.structureInventoryDetailed).toHaveBeenCalledWith(
        expect.objectContaining({ kiloStart: '1.5' }),
        format,
      );
      expect(saveFile.mock.calls[0][1]).toMatch(
        new RegExp(
          `^StructureInventoryDetailReport_\\d{4}-\\d{2}-\\d{2}\\.${format.toLowerCase()}$`,
        ),
      );
    },
  );

  it('waits while the PDF is produced, and says so', async () => {
    let finish: (file: Blob) => void = () => {};
    reportApi.structureInventoryDetailed.mockReturnValue(
      new Promise<Blob>((resolve) => {
        finish = resolve;
      }),
    );
    renderPage();

    run('PDF');

    expect(await screen.findByRole('button', { name: 'Producing PDF…' })).toBeDisabled();
    expect(byId('run-CSV')).toBeDisabled();
    finish(new Blob(['%PDF']));
    expect(await screen.findByRole('button', { name: 'Download PDF' })).toBeEnabled();
  });

  it('says so when the PDF could not be produced, and saves nothing', async () => {
    reportApi.structureInventoryDetailed.mockRejectedValue(new Error('down'));
    renderPage();

    run('PDF');

    await waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The PDF could not be produced' }),
      ),
    );
    expect(saveFile).not.toHaveBeenCalled();
  });

  it.each(['KMZ'] as const)(
    'says the %s is not available yet when the criteria are valid',
    (format) => {
      renderPage();

      type('kiloStart', '1.5');
      run(format);

      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'info',
          title: 'This report is not available yet',
          subtitle: `The criteria are valid. The ${format} is still being built.`,
        }),
      );
      expect(reportApi.structureInventoryDetailed).not.toHaveBeenCalled();
    },
  );

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

  it('keeps the criteria for the tab, apart from the Summary, and Clear forgets them', () => {
    const { unmount } = renderPage();
    type('forestFileId', 'R00123');
    fireEvent.click(byId('sortBy-SITE_ID'));
    unmount();

    renderPage();
    expect(byId('forestFileId')).toHaveValue('R00123');
    expect(byId('sortBy-SITE_ID')).toBeChecked();
    expect(sessionStorage.getItem('cbr.report.structureInventorySummary')).toBeNull();

    fireEvent.click(byId('reset'));
    expect(byId('forestFileId')).toHaveValue('');
    expect(
      JSON.parse(sessionStorage.getItem(STRUCTURE_INVENTORY_DETAILED_STATE_KEY) ?? '{}'),
    ).toMatchObject({ forestFileId: '', sortBy: 'PROJECT_BRANCH_USER_KM' });
  });

  it('reports a list that failed to load, and leaves the rest of the form working', async () => {
    api.getSiteTypeCodes.mockRejectedValue(new Error('down'));
    renderPage();

    expect(
      await screen.findByTestId('structure-inventory-detailed-codes-error'),
    ).toBeInTheDocument();
    run();
    await waitFor(() => expect(reportApi.structureInventoryDetailed).toHaveBeenCalled());
  });
});
