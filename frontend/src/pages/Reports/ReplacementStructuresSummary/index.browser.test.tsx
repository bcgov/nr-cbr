import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ReplacementStructuresSummaryPage, {
  REPLACEMENT_STRUCTURES_SUMMARY_STATE_KEY,
} from './index';

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
const reportApi = vi.hoisted(() => ({ replacementStructuresSummary: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, client: clientApi, report: reportApi },
}));

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
      <MemoryRouter initialEntries={['/reports/replacement-structures-summary']}>
        <ReplacementStructuresSummaryPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (field: string) => screen.getByTestId(`replacement-structures-summary-${field}`);
const type = (field: string, value: string) => fireEvent.change(byId(field), { target: { value } });

const YEAR_MESSAGE = 'Enter a year as yyyy, e.g. 2030';

beforeEach(() => {
  sessionStorage.clear();
  display.mockReset();
  saveFile.mockReset();
  reportApi.replacementStructuresSummary.mockReset();
  reportApi.replacementStructuresSummary.mockResolvedValue(new Blob(['%PDF']));
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  api.getSiteStatusCodes.mockResolvedValue([{ code: 'ACT', description: 'Active' }]);
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

describe('ReplacementStructuresSummaryPage', () => {
  it("offers legacy's criteria, less Area, with no Sort By", async () => {
    renderPage();

    await waitFor(() => expect(byId('siteStatusCode')).not.toBeDisabled());
    expect(screen.getByRole('option', { name: 'ACT - Active' })).toBeInTheDocument();
    for (const label of [
      'Forest District',
      'Management Area',
      'Site Status',
      'Site Type',
      'BCTS Business Area',
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    for (const legend of ['Estimated Load Restriction', 'Estimated Replacement', 'Capital Road']) {
      expect(screen.getByText(legend)).toBeInTheDocument();
    }
    expect(screen.getByText('Designated Maintainer')).toBeInTheDocument();
    expect(screen.queryByText(/^Area/)).not.toBeInTheDocument();
    expect(screen.queryByText('Sort By')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeInTheDocument();
  });

  it('asks for each year as yyyy, beside its box once left, and runs nothing until fixed', async () => {
    renderPage();

    type('loadRestrictionYearStart', '30');
    expect(screen.queryByText(YEAR_MESSAGE)).not.toBeInTheDocument();
    fireEvent.blur(byId('loadRestrictionYearStart'));
    expect(screen.getByText(YEAR_MESSAGE)).toBeInTheDocument();

    type('replacementYearEnd', '20x5');
    fireEvent.click(byId('run-PDF'));

    expect(await screen.findAllByText(YEAR_MESSAGE)).toHaveLength(2);
    expect(reportApi.replacementStructuresSummary).not.toHaveBeenCalled();
  });

  it.each(['PDF', 'CSV'] as const)(
    'fetches the %s for the criteria and saves it under its name, dated today',
    async (format) => {
      renderPage();
      type('loadRestrictionYearStart', '2026');
      type('replacementYearEnd', '2030');

      fireEvent.click(byId(`run-${format}`));

      await waitFor(() => expect(saveFile).toHaveBeenCalled());
      expect(reportApi.replacementStructuresSummary).toHaveBeenCalledWith(
        expect.objectContaining({ loadRestrictionYearStart: '2026', replacementYearEnd: '2030' }),
        format,
      );
      expect(saveFile.mock.calls[0][1]).toMatch(
        new RegExp(
          `^ReplacementStructuresSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.${format.toLowerCase()}$`,
        ),
      );
    },
  );

  it('says so when the PDF could not be produced, and saves nothing', async () => {
    reportApi.replacementStructuresSummary.mockRejectedValue(new Error('down'));
    renderPage();

    fireEvent.click(byId('run-PDF'));

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
    expect(byId('managementOrgUnit')).toHaveValue('10');

    type('orgUnit', '2');

    await screen.findByRole('option', { name: 'MA - Area of 2' });
    expect(byId('managementOrgUnit')).toHaveValue('');
  });

  it('keeps the criteria for the tab, and Clear forgets them', async () => {
    const { unmount } = renderPage();
    await waitFor(() => expect(byId('orgUnit')).not.toBeDisabled());
    type('orgUnit', '1');
    type('replacementYearStart', '2027');
    fireEvent.click(byId('capitalRoad'));
    unmount();

    renderPage();

    expect(byId('replacementYearStart')).toHaveValue('2027');
    await waitFor(() => expect(byId('orgUnit')).toHaveValue('1'));
    expect(byId('capitalRoad')).toBeChecked();
    const saved = JSON.parse(
      sessionStorage.getItem(REPLACEMENT_STRUCTURES_SUMMARY_STATE_KEY) ?? '{}',
    );
    expect(saved).toMatchObject({ orgUnit: '1', replacementYearStart: '2027', capitalRoad: true });

    fireEvent.click(byId('reset'));

    expect(byId('replacementYearStart')).toHaveValue('');
    expect(byId('capitalRoad')).not.toBeChecked();
  });
});
