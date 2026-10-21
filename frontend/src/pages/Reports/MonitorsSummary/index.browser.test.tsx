import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import MonitorsSummaryPage, { MONITORS_SUMMARY_STATE_KEY } from './index';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getPortableSuperstructureStatusCodes: vi.fn(),
  getMonitoringStatusCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));
const reportApi = vi.hoisted(() => ({ monitorsSummary: vi.fn() }));

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
      <MemoryRouter initialEntries={['/reports/monitors-summary']}>
        <MonitorsSummaryPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (field: string) => screen.getByTestId(`monitors-summary-${field}`);
const type = (field: string, value: string) => fireEvent.change(byId(field), { target: { value } });

beforeEach(() => {
  sessionStorage.clear();
  display.mockReset();
  saveFile.mockReset();
  reportApi.monitorsSummary.mockReset();
  reportApi.monitorsSummary.mockResolvedValue(new Blob(['%PDF']));
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  api.getMonitoringStatusCodes.mockResolvedValue([{ code: 'ACT', description: 'Active' }]);
  api.getForestDistricts.mockResolvedValue([
    { orgUnitNo: '1', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
  ]);
});

describe('MonitorsSummaryPage', () => {
  it("offers legacy's criteria, less Area and Road Responsibility, and no Management Area", async () => {
    renderPage();

    await waitFor(() => expect(byId('monitoringStatusCode')).not.toBeDisabled());
    expect(screen.getByRole('option', { name: 'ACT - Active' })).toBeInTheDocument();
    for (const label of [
      'Structure #',
      'Site Status',
      'Site Type',
      'Monitoring Status',
      'Forest District',
      'BCTS Business Area',
      'Forest Service Road',
      'Project File ID#',
      'Br.',
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('Designated Maintainer')).toBeInTheDocument();
    expect(screen.getByText('Most Recent Inspections Only?')).toBeInTheDocument();
    for (const absent of [/Management Area/, /^Area/, /Road Responsibility/, /Site #/]) {
      expect(screen.queryByText(absent)).not.toBeInTheDocument();
    }
    expect(api.getManagementAreas).not.toHaveBeenCalled();
  });

  it("sorts by legacy's default, offers its other order, and runs as PDF or CSV", () => {
    renderPage();

    expect(byId('sortBy-PROJECT_BRANCH_KM')).toBeChecked();
    expect(screen.getByLabelText('Road Name, Structure Id')).not.toBeChecked();
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
      expect(reportApi.monitorsSummary).toHaveBeenCalledWith(
        expect.objectContaining({ structureName: 'B10' }),
        format,
      );
      expect(saveFile.mock.calls[0][1]).toMatch(
        new RegExp(`^MonitorsSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.${format.toLowerCase()}$`),
      );
    },
  );

  it('says so when the PDF could not be produced, and saves nothing', async () => {
    reportApi.monitorsSummary.mockRejectedValue(new Error('down'));
    renderPage();

    fireEvent.click(byId('run-PDF'));

    await waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The PDF could not be produced' }),
      ),
    );
    expect(saveFile).not.toHaveBeenCalled();
  });

  it('keeps the criteria for the tab, and Clear forgets them', async () => {
    const { unmount } = renderPage();
    await waitFor(() => expect(byId('orgUnit')).not.toBeDisabled());
    type('structureName', 'B10');
    type('orgUnit', '1');
    fireEvent.click(byId('sortBy-ROAD_NAME_STRUCTURE'));
    unmount();

    renderPage();

    expect(byId('structureName')).toHaveValue('B10');
    await waitFor(() => expect(byId('orgUnit')).toHaveValue('1'));
    expect(byId('sortBy-ROAD_NAME_STRUCTURE')).toBeChecked();
    const saved = JSON.parse(sessionStorage.getItem(MONITORS_SUMMARY_STATE_KEY) ?? '{}');
    expect(saved).toMatchObject({ structureName: 'B10', orgUnit: '1' });
    expect(saved).not.toHaveProperty('managementOrgUnit');

    fireEvent.click(byId('reset'));

    expect(byId('structureName')).toHaveValue('');
    expect(byId('sortBy-PROJECT_BRANCH_KM')).toBeChecked();
  });
});
