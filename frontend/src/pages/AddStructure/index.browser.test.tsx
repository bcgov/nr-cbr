import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AddStructurePage from './index';

import { WORK_SAVED } from '@/context/unsavedChanges/workSaved';

const authorization = vi.hoisted(() => ({
  canEdit: true,
  canDelete: true,
  isPeng: false,
}));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => authorization }));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));
vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));
const setDirty = vi.hoisted(() => vi.fn());
vi.mock('@/context/unsavedChanges/useUnsavedChanges', () => ({
  useUnsavedChanges: () => ({ isDirty: false, setDirty }),
}));
const navigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

const api = vi.hoisted(() => ({ createStructure: vi.fn(), previewSite: vi.fn() }));
const configurationApi = vi.hoisted(() => ({
  getStructureTypeClassCodes: vi.fn(),
  getBuiltByCodes: vi.fn(),
  getStructureSourceCodes: vi.fn(),
  getBridgeCodes: vi.fn(),
  getCulvertCodes: vi.fn(),
  getLoadRatingCodes: vi.fn(),
  getPierTypeCodes: vi.fn(),
  getSpecialEquipmentCodes: vi.fn(),
}));
vi.mock('@/services/APIs', () => ({
  default: { structureSearch: api, configuration: configurationApi },
}));

const site = {
  siteId: 'BOWRON-001',
  siteTypeCode: 'CRS',
  crossingName: 'Deadman Creek',
  districtName: 'Prince George',
  managementAreaName: null,
  forestServiceRoad: 'Bowron FSR',
  projectName: null,
  forestFileId: 'R00123',
  roadSectionId: '01',
  kilometres: '12.5',
  maintainerLabel: null,
  siteStatus: { code: 'ACT', description: 'Active' },
};

beforeEach(() => {
  authorization.isPeng = false;
  navigate.mockReset();
  api.createStructure.mockReset();
  api.createStructure.mockResolvedValue({ id: '901' });
  api.previewSite.mockReset();
  api.previewSite.mockResolvedValue({ site, problem: null });
  configurationApi.getStructureTypeClassCodes.mockResolvedValue([
    { code: 'PB', description: 'Durable Bridge' },
    { code: 'TB', description: 'Non-Durable Bridge' },
    { code: 'CUL', description: 'Culvert' },
    { code: 'WLC', description: 'Wood Log Culvert' },
  ]);
  configurationApi.getBuiltByCodes.mockResolvedValue([{ code: 'MOF', description: 'MOF' }]);
  configurationApi.getStructureSourceCodes.mockResolvedValue([
    { code: 'MOF', description: 'Ministry of Forests' },
  ]);
  configurationApi.getBridgeCodes.mockResolvedValue({
    superstructureTypes: [{ code: 'STL', description: 'Steel Girder' }],
    deckTypes: [{ code: 'TIM', description: 'Timber' }],
    runningSurfaces: [{ code: 'GRV', description: 'Gravel' }],
    curbTypes: [{ code: 'TMB', description: 'Timber' }],
    abutments: [{ code: 'CON', description: 'Concrete' }],
  });
  configurationApi.getCulvertCodes.mockResolvedValue({
    culvertTypes: [{ code: 'RND', description: 'Round' }],
    materials: [{ code: 'CSP', description: 'Corrugated Steel Pipe' }],
    headwallLocations: [],
    openBottomSubstructures: [],
  });
  configurationApi.getLoadRatingCodes.mockResolvedValue({
    loadPostingSigns: [
      { code: 'UNK', description: 'Unknown' },
      { code: 'N', description: 'No' },
    ],
    designVehicles: [],
    reasons: [],
  });
  configurationApi.getPierTypeCodes.mockResolvedValue([{ code: 'CRIB', description: 'Crib' }]);
  configurationApi.getSpecialEquipmentCodes.mockResolvedValue([]);
});

const renderPage = async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/inventory/site/BOWRON-001/add-structure']}>
        <Routes>
          <Route path="/inventory/site/:siteId/add-structure" element={<AddStructurePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await waitFor(() => expect(screen.getByTestId('add-structure-type')).not.toBeDisabled());
};

const set = (testId: string, value: string) =>
  fireEvent.change(screen.getByTestId(testId), { target: { value } });

const fillBridge = async () => {
  set('add-structure-name', 'b600');
  set('add-structure-type', 'PB');
  await waitFor(() => expect(screen.getByTestId('structure-sourceCode')).not.toBeDisabled());
  set('structure-sourceCode', 'MOF');
  await waitFor(() =>
    expect(screen.getByTestId('bridge-superstructureTypeCode')).not.toBeDisabled(),
  );
  set('bridge-totalBridgeLength', '24');
  set('bridge-deckWidth', '4.3');
  set('bridge-superstructureTypeCode', 'STL');
  set('bridge-deckTypeCode', 'TIM');
  set('bridge-runningSurfaceCode', 'GRV');
  set('bridge-curbTypeCode', 'TMB');
  set('bridge-leftAbutmentCode', 'CON');
  set('bridge-rightAbutmentCode', 'CON');
};

const addSpan = async (length: string) => {
  await userEvent.click(screen.getByTestId('add-structure-span-add'));
  const dialog = await screen.findByTestId('span-dialog');
  await userEvent.type(within(dialog).getByTestId('span-length'), length);
  await userEvent.click(within(dialog).getByTestId('span-save'));
  await waitFor(() => expect(screen.queryByTestId('span-dialog')).not.toBeInTheDocument());
};

describe('AddStructurePage', () => {
  it('starts on the site it came from, shown as Information shows a site', async () => {
    await renderPage();

    expect(screen.getByTestId('add-structure-site')).toHaveValue('BOWRON-001');
    await waitFor(() => expect(api.previewSite).toHaveBeenCalledWith('BOWRON-001'));
    expect(await screen.findByText('Deadman Creek')).toBeInTheDocument();
    // Legacy's starting values.
    expect(screen.getByTestId('load-rating-posting')).toHaveValue('UNK');
    expect(screen.getByTestId('schedule-frequency')).toHaveValue('3');
  });

  it('shows the bridge or culvert cards once Type/Class says which', async () => {
    await renderPage();
    expect(screen.queryByTestId('add-structure-bridge')).not.toBeInTheDocument();
    expect(screen.queryByTestId('add-structure-culvert')).not.toBeInTheDocument();

    set('add-structure-type', 'TB');
    expect(screen.getByTestId('add-structure-bridge')).toBeInTheDocument();
    expect(screen.getByTestId('add-structure-spans')).toBeInTheDocument();

    set('add-structure-type', 'WLC');
    expect(screen.queryByTestId('add-structure-bridge')).not.toBeInTheDocument();
    expect(screen.getByTestId('add-structure-culvert')).toBeInTheDocument();
    // A Wood Log Culvert has no Culvert Type, as legacy's page.
    expect(screen.queryByTestId('culvert-culvertTypeCode')).not.toBeInTheDocument();
  });

  it('requires what a complete structure needs, and sends nothing until it has it', async () => {
    await renderPage();
    set('add-structure-type', 'PB');

    await userEvent.click(screen.getByTestId('add-structure-save'));

    const form = screen.getByTestId('add-structure-form');
    expect(form).toMatchTextContent('Structure # is required.');
    expect(form).toMatchTextContent('Source is required.');
    expect(form).toMatchTextContent('Bridge Length is required.');
    expect(form).toMatchTextContent('Superstructure is required.');
    expect(screen.getByTestId('add-structure-spans-error')).toMatchTextContent(
      'At least one span is required.',
    );
    expect(api.createStructure).not.toHaveBeenCalled();
  });

  it('saves a bridge with its spans and opens it', async () => {
    await renderPage();
    await fillBridge();
    await addSpan('12');
    await addSpan('12');

    await userEvent.click(screen.getByTestId('add-structure-save'));

    await waitFor(() =>
      expect(api.createStructure).toHaveBeenCalledWith(
        expect.objectContaining({
          structureName: 'b600',
          siteId: 'BOWRON-001',
          typeClassCode: 'PB',
          culvert: null,
          replacement: null,
          spans: [
            { number: 1, lengthMetres: 12 },
            { number: 2, lengthMetres: 12 },
          ],
          piers: [],
          details: expect.objectContaining({ sourceCode: 'MOF' }),
          bridge: expect.objectContaining({ typeClassCode: 'PB', superstructureTypeCode: 'STL' }),
          loadRating: expect.objectContaining({
            loadPostingSignsCode: 'UNK',
            reviewRequired: true,
          }),
          schedule: expect.objectContaining({ routineFrequencyYears: 3 }),
        }),
      ),
    );
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/inventory/structure/901', { state: WORK_SAVED }),
    );
  });

  it('shows what the server refuses beside its field', async () => {
    api.createStructure.mockRejectedValue({
      body: {
        fieldErrors: {
          'structureName': 'Structure B600 already exists.',
          'bridge.deckWidth': 'Deck Width can be at most 99999.99 metres.',
        },
      },
    });
    await renderPage();
    await fillBridge();
    await addSpan('12');

    await userEvent.click(screen.getByTestId('add-structure-save'));

    const form = screen.getByTestId('add-structure-form');
    await waitFor(() => expect(form).toMatchTextContent('Structure B600 already exists.'));
    expect(form).toMatchTextContent('Deck Width can be at most 99999.99 metres.');
  });

  it('offers Replacement Details to a P.Eng only, Load Restriction optional', async () => {
    await renderPage();
    expect(screen.getByTestId('add-structure-replacement-peng')).toBeInTheDocument();
    expect(screen.queryByTestId('replacement-loadRestrictionYear')).not.toBeInTheDocument();
  });
});
