import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import StructureDetailReportPage from './index';

const api = vi.hoisted(() => ({
  getSiteTypeCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));
const structureSearchApi = vi.hoisted(() => ({ searchStructures: vi.fn() }));
const reportApi = vi.hoisted(() => ({ structureDetail: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: {
    configuration: api,
    client: clientApi,
    structureSearch: structureSearchApi,
    report: reportApi,
  },
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
      <MemoryRouter initialEntries={['/reports/structure-detail']}>
        <StructureDetailReportPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (field: string) => screen.getByTestId(`structure-detail-report-${field}`);
const type = (field: string, value: string) => fireEvent.change(byId(field), { target: { value } });
const search = () => fireEvent.click(byId('search'));

const structure = (id: string) => ({
  id,
  structureName: `B${id}`,
  siteId: '62-001',
  orgUnitCode: 'DPG',
  orgUnitName: 'Prince George',
  forestServiceRoad: 'Bowron FSR',
  kilometres: '12.50',
  crossingName: 'Deadman Creek',
  structureTypeClass: 'PB - Permanent Bridge',
  forestFileId: 'R00123',
  roadSectionId: '01',
  clientNumber: null,
  clientLocationCode: null,
  clientName: null,
});

const page = (content: unknown[], totalElements = content.length) => ({
  content,
  totalElements,
  totalPages: 1,
  pageNumber: 0,
  pageSize: 20,
});

beforeEach(() => {
  sessionStorage.clear();
  display.mockReset();
  saveFile.mockReset();
  reportApi.structureDetail.mockReset();
  reportApi.structureDetail.mockResolvedValue(new Blob(['%PDF']));
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  api.getSiteTypeCodes.mockResolvedValue([{ code: 'CRS', description: 'Stream crossing' }]);
  api.getForestDistricts.mockResolvedValue([
    { orgUnitNo: '1', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
    { orgUnitNo: '2', orgUnitCode: 'DMH', orgUnitName: '100 Mile House' },
  ]);
  api.getManagementAreas.mockImplementation((district: string) =>
    Promise.resolve([
      { orgUnitNo: `${district}0`, orgUnitCode: 'MA', orgUnitName: `Area of ${district}` },
    ]),
  );
  structureSearchApi.searchStructures.mockReset();
  structureSearchApi.searchStructures.mockResolvedValue(page([structure('7'), structure('8')]));
});

describe('StructureDetailReportPage', () => {
  it("offers legacy's search criteria and no results until Search", async () => {
    renderPage();

    await waitFor(() => expect(byId('siteTypeCode')).not.toBeDisabled());
    expect(screen.getByRole('option', { name: 'CRS - Stream crossing' })).toBeInTheDocument();
    for (const label of ['Structure #', 'Project File ID#', 'Br.', 'Forest District']) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('Designated Maintainer')).toBeInTheDocument();
    expect(screen.queryByText('Sort By')).not.toBeInTheDocument();
    expect(structureSearchApi.searchStructures).not.toHaveBeenCalled();
  });

  it('searches structures as Structure Search does, archived ones left out', async () => {
    renderPage();

    type('structureName', 'B10');
    type('forestFileId', 'R00123');
    search();

    expect(await byIdAsync('results')).toBeInTheDocument();
    const [criteria, pageNumber, pageSize] = structureSearchApi.searchStructures.mock.calls[0];
    expect(criteria).toMatchObject({
      structureName: 'B10',
      forestFileId: 'R00123',
      includeArchived: false,
    });
    expect([pageNumber, pageSize]).toEqual([0, 20]);
  });

  it("lists what it found in legacy's columns, a Download menu of CSV and PDF on each", async () => {
    renderPage();

    search();

    const row = (await byIdAsync('row-7')).textContent;
    for (const value of ['B7', '62-001', 'DPG — Prince George', 'Bowron FSR', 'R00123-01']) {
      expect(row).toContain(value);
    }
    expect(byId('download-7')).toHaveAccessibleName('Download B7');

    fireEvent.click(byId('download-7'));

    expect(await screen.findByRole('menuitem', { name: 'CSV' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'PDF' })).toBeInTheDocument();
    expect(screen.getByText('Structures — 2 matches')).toBeInTheDocument();
  });

  it('says No structures found when the search finds none', async () => {
    structureSearchApi.searchStructures.mockResolvedValue(page([]));
    renderPage();

    search();

    expect(await screen.findByText('No structures found.')).toBeInTheDocument();
  });

  it.each(['PDF', 'CSV'] as const)(
    "fetches a structure's %s and saves it under its Structure #, dated today",
    async (format) => {
      renderPage();
      search();

      await choose(format, '7');

      await waitFor(() => expect(saveFile).toHaveBeenCalled());
      expect(reportApi.structureDetail).toHaveBeenCalledWith('7', format);
      expect(saveFile.mock.calls[0][1]).toMatch(
        new RegExp(`^StructureDetailReport_B7_\\d{4}-\\d{2}-\\d{2}\\.${format.toLowerCase()}$`),
      );
    },
  );

  it("waits while a structure's PDF is produced, and says so on that row", async () => {
    let finish: (file: Blob) => void = () => {};
    reportApi.structureDetail.mockReturnValue(
      new Promise<Blob>((resolve) => {
        finish = resolve;
      }),
    );
    renderPage();
    search();

    await choose('PDF', '7');

    expect((await byIdAsync('producing-7')).textContent).toContain('Producing PDF…');
    expect(byId('download-8')).toBeDisabled();
    finish(new Blob(['%PDF']));
    await waitFor(() => expect(byId('download-8')).toBeEnabled());
    expect(byId('download-7')).toBeEnabled();
  });

  it('says so when the PDF could not be produced, and saves nothing', async () => {
    reportApi.structureDetail.mockRejectedValue(new Error('down'));
    renderPage();
    search();

    await choose('PDF', '7');

    await waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The PDF could not be produced' }),
      ),
    );
    expect(saveFile).not.toHaveBeenCalled();
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

  it('keeps the criteria and the results for the tab, and Clear forgets both', async () => {
    const { unmount } = renderPage();
    type('structureName', 'B10');
    search();
    await byIdAsync('results');
    unmount();

    renderPage();
    expect(byId('structureName')).toHaveValue('B10');
    expect(await byIdAsync('results')).toBeInTheDocument();

    fireEvent.click(byId('reset'));
    expect(byId('structureName')).toHaveValue('');
    expect(screen.queryByTestId('structure-detail-report-results')).not.toBeInTheDocument();
  });

  it('says so when the search fails', async () => {
    structureSearchApi.searchStructures.mockRejectedValue(new Error('down'));
    renderPage();

    search();

    expect(await byIdAsync('error')).toBeInTheDocument();
  });
});

const byIdAsync = (field: string) => screen.findByTestId(`structure-detail-report-${field}`);

/** Opens a row's Download menu and chooses a format. */
const choose = async (format: 'CSV' | 'PDF', structureId: string) => {
  fireEvent.click(await byIdAsync(`download-${structureId}`));
  fireEvent.click(await screen.findByRole('menuitem', { name: format }));
};
