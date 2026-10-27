import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { userEvent } from 'vitest/browser';

import StructureDetailPage from './index';

import type { StructureDetailResponse } from './structureResponse';

const authorization = vi.hoisted(() => ({
  canEdit: false,
  canDelete: false,
  isPeng: false,
  isInspectionReviewer: false,
  canWriteInspection: false,
}));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => authorization }));

// A delete's outcome is a toast; asserted on what the page asked to show.
const display = vi.hoisted(() => vi.fn());
vi.mock('@/context/notification/useNotification', () => ({ useNotification: () => ({ display }) }));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const api = vi.hoisted(() => ({
  getStructure: vi.fn(),
  getSpansAndPiers: vi.fn(),
  previewSite: vi.fn(),
  suggestStructureNames: vi.fn(),
  createReplacement: vi.fn(),
  deleteReplacement: vi.fn(),
  updateStructureInformation: vi.fn(),
  updateStructureDetails: vi.fn(),
  updateBridgeDetails: vi.fn(),
  updateCulvertDetails: vi.fn(),
  updateLoadRatingDetails: vi.fn(),
  updateReplacementDetails: vi.fn(),
  createLoadRating: vi.fn(),
  updateLoadRating: vi.fn(),
  deleteLoadRating: vi.fn(),
  createSpan: vi.fn(),
  updateSpan: vi.fn(),
  deleteSpan: vi.fn(),
  createPier: vi.fn(),
  updatePier: vi.fn(),
  deletePier: vi.fn(),
  getDocuments: vi.fn(),
  getDocumentFile: vi.fn(),
  addDocument: vi.fn(),
  updateDocument: vi.fn(),
  deleteDocument: vi.fn(),
  getInspectionSchedule: vi.fn(),
  updateInspectionSchedule: vi.fn(),
  addPlannedInspectionComment: vi.fn(),
  addStructureComment: vi.fn(),
  addCloseProximityInspection: vi.fn(),
  updateStructureComment: vi.fn(),
  getStructureInspections: vi.fn(),
  getStructureRepairs: vi.fn(),
  deleteStructureRepair: vi.fn(),
  getStructureRepairTypes: vi.fn(),
  createStructureRepair: vi.fn(),
  updateStructureRepair: vi.fn(),
  getStructureMonitors: vi.fn(),
  createStructureMonitor: vi.fn(),
  updateStructureMonitor: vi.fn(),
  deleteStructureMonitor: vi.fn(),
}));
const configurationApi = vi.hoisted(() => ({
  getMonitoringStatusCodes: vi.fn(),
  getMonitorFrequencyCodes: vi.fn(),
  getRepairStatusCodes: vi.fn(),
  getRepairPriorityCodes: vi.fn(),
  getRepairGroupCodes: vi.fn(),
  getSpecialEquipmentCodes: vi.fn(),
  getFileAttachmentTypeCodes: vi.fn(),
  getDocumentExtensions: vi.fn(),
  getPierTypeCodes: vi.fn(),
  getBuiltByCodes: vi.fn(),
  getStructureSourceCodes: vi.fn(),
  getBridgeCodes: vi.fn(),
  getStructureTypeClassCodes: vi.fn(),
  getLoadRatingCodes: vi.fn(),
  getCulvertCodes: vi.fn(),
}));
vi.mock('@/services/APIs', () => ({
  default: { structureSearch: api, configuration: configurationApi },
}));

const code = (value: string | null, description: string | null = value) => ({
  code: value,
  description,
});

const NONE = code(null, null);

/** A complete timber bridge on a crossing site. */
const bridge = (overrides: Partial<StructureDetailResponse> = {}): StructureDetailResponse => ({
  id: '7',
  structureName: 'B100',
  active: true,
  typeClass: code('TB', 'Timber Bridge'),
  site: {
    siteId: '62-001',
    siteTypeCode: 'XNG',
    siteStatus: code('ACT', 'Active'),
    inspectionStatus: code('OK', 'Inspected'),
    districtName: 'Cariboo-Chilcotin Natural Resource District',
    managementAreaName: 'Central Cariboo',
    forestServiceRoad: 'CHILCOTIN SOUTH',
    projectName: null,
    crossingName: 'Riske Cr',
    forestFileId: '6970',
    roadSectionId: '01',
    kilometres: '0.24',
    maintainerLabel: 'CANADIAN FOREST PRODUCTS LTD. · 00001271-01',
  },
  details: {
    builtBy: code('MOF', 'Ministry of Forests'),
    yearFabricated: 1998,
    yearBuilt: 2000,
    inventoryAddedYear: 2001,
    source: code('MOF', 'MoF Engineering'),
    endOfDesignLifeYear: 2045,
    asBuiltInfoOnFile: true,
    installationCost: 125000,
    materialCost: 0,
    portable: false,
  },
  bridge: {
    spanCount: 2,
    needleBeams: false,
    lengthMetres: 24.5,
    superstructure: code('STL', 'Steel Girder'),
    superstructureComment: null,
    deckType: code('TIM', 'Timber'),
    deckTypeComment: null,
    deckWidthMetres: 4.27,
    runningSurface: code('GRV', 'Gravel'),
    curbType: code('TMB', 'Timber'),
    curbTypeComment: null,
    leftAbutment: code('CON', 'Concrete'),
    rightAbutment: code('CON', 'Concrete'),
    abutmentComment: null,
  },
  culvert: null,
  comments: [
    { id: '2', text: 'Deck replaced.', userId: 'IDIR\\JSMITH', timestamp: '2024-06-03T14:05:00' },
    { id: '1', text: 'Installed.', userId: 'IDIR\\AJONES', timestamp: '2000-09-01T09:00:00' },
  ],
  loadRating: {
    currentRating: 60,
    history: [
      {
        id: 'r2',
        rating: 60,
        reason: code('INSP', 'Inspection'),
        reasonComment: null,
        date: '2024-05-01',
        userId: 'IDIR\\PENG',
        reviewedDate: '2024-05-20',
        status: 'REVIEWED',
        inspectionId: '900',
        current: true,
      },
      {
        id: 'r1',
        rating: 63,
        reason: code('DES', 'Design'),
        reasonComment: null,
        date: '2000-10-01',
        userId: 'IDIR\\AJONES',
        reviewedDate: null,
        status: 'MANUAL',
        inspectionId: null,
        current: false,
      },
      {
        id: 'r0',
        rating: 45,
        reason: code('INSP', 'Inspection'),
        reasonComment: null,
        date: '1995-04-01',
        userId: 'IDIR\\OLD',
        reviewedDate: '1995-04-10',
        status: 'REVIEWED',
        inspectionId: '100',
        current: false,
      },
    ],
    loadPostingSigns: code('N', 'No'),
    reviewRequired: false,
    designVehicle: code('L75', 'L-75'),
    designVehicleComment: null,
    designLoadRating: 63,
  },
  replaced: [{ id: '3', structureName: 'B050' }],
  replacedBy: [],
  replacement: {
    estimatedClosureYear: 2050,
    estimatedReplacementYear: 2048,
    estimatedLoadRestrictionYear: 2046,
    estimatedReplacementCost: 300000,
    replacementCostComment: 'Steel prices.',
  },
  outstanding: [],
  ...overrides,
});

/** A corrugated culvert, second of three on its site. */
const culvert = (overrides: Partial<StructureDetailResponse> = {}): StructureDetailResponse =>
  bridge({
    id: '8',
    structureName: 'C200',
    typeClass: code('CUL', 'Culvert'),
    bridge: null,
    culvert: {
      culvertNumber: 2,
      culvertsOnSite: 3,
      lengthMetres: 18.0,
      gradient: 2.5,
      culvertType: code('RND', 'Round'),
      material: code('CSP', 'Corrugated Steel Pipe'),
      materialComment: null,
      inletCoverDepthMm: 600,
      outletCoverDepthMm: 450,
      openingHeightMm: 1200,
      openingWidthMm: 1200,
      headwallLocation: code('IN', 'Inlet'),
      openBottomSubstructure: code('NA', 'Not applicable'),
    },
    ...overrides,
  });

/** Where a link or button took the page: the path and query, for the tests to read. */
const Landed = () => {
  const location = useLocation();
  return <p data-testid="landed">{`${location.pathname}${location.search}`}</p>;
};

const renderAt = (
  entry: string | { pathname: string; state: unknown } = '/inventory/structure/7',
) =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/inventory/structure/:structureId" element={<StructureDetailPage />} />
          <Route path="/inspection/new" element={<Landed />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

const showing = async (structure: StructureDetailResponse) => {
  api.getStructure.mockResolvedValue(structure);
  renderAt(`/inventory/structure/${structure.id}`);
  return screen.findByTestId('structure-header');
};

/** The top of the Inspections tab, empty unless a test fills it. */
const schedule = (overrides: object = {}) => ({
  plannedInspectionComments: [],
  closeProximityRequired: false,
  closeProximityEquipment: { code: null, description: null },
  nextCloseProximityDate: null,
  nextRoutineDate: null,
  routineFrequencyYears: null,
  completedCloseProximity: [],
  latestReviewedInspectionDate: null,
  ...overrides,
});

/** A page of the inspection table. */
const inspectionsPage = (content: object[], overrides: object = {}) => ({
  page: {
    content,
    totalElements: content.length,
    totalPages: 1,
    pageNumber: 0,
    pageSize: 10,
  },
  beforeInstallCount: 0,
  ...overrides,
});

/** A page of the repairs or monitoring table, and how many predate the superstructure. */
const repairsPage = (
  content: object[],
  totalElements = content.length,
  beforeInstallCount = 0,
) => ({
  page: {
    content,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / 10)),
    pageNumber: 0,
    pageSize: 10,
  },
  beforeInstallCount,
});

/** Opens the Details tab; Information is the one selected on arrival. */
const openDetails = () => userEvent.click(screen.getByTestId('structure-tab-details'));

/** A read-only field's value, found by its label within a container. */
const valueOf = (label: string, container: HTMLElement = document.body) => {
  const labelElement = within(container)
    .getAllByText(label, { selector: '.read-only-field__label' })
    .at(0) as HTMLElement;
  return labelElement.parentElement?.querySelector('.read-only-field__value')?.textContent;
};

beforeEach(() => {
  api.getStructure.mockReset();
  api.getSpansAndPiers.mockReset();
  api.getSpansAndPiers.mockResolvedValue({ spans: [], piers: [] });
  api.previewSite.mockReset();
  api.suggestStructureNames.mockReset();
  api.suggestStructureNames.mockResolvedValue([]);
  api.createReplacement.mockReset();
  api.deleteReplacement.mockReset();
  api.updateStructureInformation.mockReset();
  api.updateStructureInformation.mockResolvedValue(undefined);
  api.updateStructureDetails.mockReset();
  api.updateStructureDetails.mockResolvedValue(undefined);
  api.updateBridgeDetails.mockReset();
  api.updateBridgeDetails.mockResolvedValue(undefined);
  for (const write of [api.updateLoadRatingDetails, api.updateLoadRating, api.deleteLoadRating]) {
    write.mockReset();
    write.mockResolvedValue(undefined);
  }
  api.updateCulvertDetails.mockReset();
  api.updateCulvertDetails.mockResolvedValue(undefined);
  configurationApi.getCulvertCodes.mockResolvedValue({
    culvertTypes: [
      { code: 'RND', description: 'Round' },
      { code: 'OPN', description: 'Open Bottom' },
    ],
    materials: [
      { code: 'CSP', description: 'Corrugated Steel Pipe' },
      { code: 'OTH', description: 'Other' },
    ],
    headwallLocations: [{ code: 'IN', description: 'Inlet' }],
    openBottomSubstructures: [{ code: 'CON', description: 'Concrete' }],
  });
  api.updateReplacementDetails.mockReset();
  api.updateReplacementDetails.mockResolvedValue(undefined);
  api.createLoadRating.mockReset();
  api.createLoadRating.mockResolvedValue({ id: 'r9' });
  configurationApi.getLoadRatingCodes.mockResolvedValue({
    loadPostingSigns: [
      { code: 'N', description: 'No' },
      { code: 'Y', description: 'Yes' },
    ],
    designVehicles: [
      { code: 'L75', description: 'L-75', designLoadRating: 75 },
      { code: 'OTHER', description: 'Other', designLoadRating: null },
    ],
    reasons: [
      { code: 'INSP', description: 'Inspection', deltas: ['INC', 'DEC'] },
      { code: 'NOC', description: 'No change', deltas: ['NOC'] },
      { code: 'OTHR', description: 'Other', deltas: ['UNDEF'] },
    ],
  });
  configurationApi.getBridgeCodes.mockResolvedValue({
    superstructureTypes: [
      { code: 'STL', description: 'Steel Girder' },
      { code: 'SBB', description: 'Steel Box Beam' },
      { code: 'RAIL', description: 'Rail Car' },
      { code: 'OTH', description: 'Other' },
    ],
    deckTypes: [{ code: 'TIM', description: 'Timber' }],
    runningSurfaces: [{ code: 'GRV', description: 'Gravel' }],
    curbTypes: [{ code: 'TMB', description: 'Timber' }],
    abutments: [
      { code: 'CON', description: 'Concrete' },
      { code: 'CLT', description: 'Crib Log Timber' },
    ],
  });
  configurationApi.getStructureTypeClassCodes.mockResolvedValue([
    { code: 'PB', description: 'Durable Bridge' },
    { code: 'TB', description: 'Non-Durable Bridge' },
  ]);
  configurationApi.getBuiltByCodes.mockResolvedValue([
    { code: 'MOF', description: 'Ministry of Forests' },
    { code: 'LIC', description: 'Licensee' },
  ]);
  configurationApi.getStructureSourceCodes.mockResolvedValue([
    { code: 'MOF', description: 'MoF Engineering' },
    { code: 'IR', description: 'Inventory Reconnaissance' },
  ]);
  for (const write of [
    api.createSpan,
    api.updateSpan,
    api.deleteSpan,
    api.createPier,
    api.updatePier,
    api.deletePier,
  ]) {
    write.mockReset();
    write.mockResolvedValue(undefined);
  }
  api.createSpan.mockResolvedValue({ id: 's9' });
  api.createPier.mockResolvedValue({ id: 'p9' });
  configurationApi.getPierTypeCodes.mockResolvedValue([
    { code: 'CONC', description: 'Concrete' },
    { code: 'CRIB', description: 'Timber crib' },
  ]);
  api.getDocuments.mockReset();
  api.getDocuments.mockResolvedValue({ documents: [] });
  api.getDocumentFile.mockReset();
  api.addDocument.mockReset();
  api.addDocument.mockResolvedValue({ id: '91' });
  api.updateDocument.mockReset();
  api.updateDocument.mockResolvedValue(undefined);
  api.deleteDocument.mockReset();
  api.deleteDocument.mockResolvedValue(undefined);
  configurationApi.getFileAttachmentTypeCodes.mockResolvedValue([
    { code: 'OTH', description: 'Other' },
    { code: 'PHOTO', description: 'Photograph' },
  ]);
  configurationApi.getDocumentExtensions.mockResolvedValue(['JPG', 'PDF']);

  api.getInspectionSchedule.mockReset();
  api.getInspectionSchedule.mockResolvedValue(schedule());
  api.getStructureInspections.mockReset();
  api.getStructureInspections.mockResolvedValue(inspectionsPage([]));
  api.getStructureRepairs.mockReset();
  api.getStructureRepairs.mockResolvedValue(repairsPage([]));
  api.deleteStructureRepair.mockReset();
  api.deleteStructureRepair.mockResolvedValue(undefined);
  api.getStructureMonitors.mockReset();
  api.getStructureMonitors.mockResolvedValue(repairsPage([]));
  api.createStructureMonitor.mockReset();
  api.createStructureMonitor.mockResolvedValue({ id: '30', number: 3 });
  api.updateStructureMonitor.mockReset();
  api.updateStructureMonitor.mockResolvedValue(undefined);
  configurationApi.getMonitoringStatusCodes.mockResolvedValue([
    { code: 'COM', description: 'Completed' },
    { code: 'REQ', description: 'Required' },
    { code: 'SUG', description: 'Suggested' },
  ]);
  configurationApi.getMonitorFrequencyCodes.mockResolvedValue([
    { code: 'ANN', description: 'Annually' },
    { code: 'INS', description: 'Each inspection' },
    { code: 'OTH', description: 'Other' },
  ]);
  authorization.canEdit = false;
  api.deleteStructureMonitor.mockReset();
  api.deleteStructureMonitor.mockResolvedValue(undefined);
  authorization.canDelete = false;
  authorization.isPeng = false;
  authorization.isInspectionReviewer = false;
  authorization.canWriteInspection = false;
  api.getStructureRepairTypes.mockReset();
  api.getStructureRepairTypes.mockResolvedValue([
    { code: 'RAIL', description: 'Railing', unit: 'm', groupCode: 'APPR' },
    { code: 'DECK', description: 'Deck planks', unit: 'm²', groupCode: 'STRU' },
    { code: 'RAIL', description: 'Railing', unit: 'm', groupCode: 'STRU' },
    { code: '800A', description: 'Other', unit: null, groupCode: 'MISC' },
  ]);
  api.createStructureRepair.mockReset();
  api.createStructureRepair.mockResolvedValue({ id: '40', number: 4 });
  api.updateStructureRepair.mockReset();
  api.updateStructureRepair.mockResolvedValue(undefined);
  configurationApi.getRepairStatusCodes.mockResolvedValue([
    { code: 'CF', description: 'Carried Forward' },
    { code: 'COM', description: 'Completed' },
    { code: 'NRQ', description: 'Not Required' },
    { code: 'REQ', description: 'Required' },
    { code: 'SUG', description: 'Suggested' },
  ]);
  configurationApi.getRepairPriorityCodes.mockResolvedValue([
    { code: 'H', description: 'High' },
    { code: 'P1', description: 'Urgent' },
  ]);
  configurationApi.getRepairGroupCodes.mockResolvedValue([
    { code: 'APPR', description: 'Approach' },
    { code: 'MISC', description: 'Miscellaneous' },
    { code: 'STRU', description: 'Superstructure' },
  ]);
  api.addCloseProximityInspection.mockReset();
  api.addCloseProximityInspection.mockResolvedValue({ id: '12' });
  api.addPlannedInspectionComment.mockReset();
  api.addPlannedInspectionComment.mockResolvedValue({ id: '9' });
  api.addStructureComment.mockReset();
  api.addStructureComment.mockResolvedValue({ id: '9' });
  api.updateStructureComment.mockReset();
  api.updateStructureComment.mockResolvedValue(undefined);
  api.updateInspectionSchedule.mockReset();
  api.updateInspectionSchedule.mockResolvedValue(undefined);
  configurationApi.getSpecialEquipmentCodes.mockResolvedValue([
    { code: 'BOAT', description: 'Boat' },
    { code: 'UBIU', description: 'Under-bridge unit' },
  ]);
  display.mockClear();
});

describe('StructureDetailPage — loading', () => {
  it('asks for the structure in the URL, and shows a skeleton until it arrives', async () => {
    api.getStructure.mockReturnValue(new Promise(() => {}));
    renderAt('/inventory/structure/7');

    expect(await screen.findByTestId('structure-detail-loading')).toBeInTheDocument();
    expect(api.getStructure).toHaveBeenCalledWith('7');
  });

  it('names the structure from the search link while it loads', async () => {
    api.getStructure.mockReturnValue(new Promise(() => {}));
    renderAt({ pathname: '/inventory/structure/7', state: { structureName: 'B100' } });

    expect(await screen.findByRole('heading', { name: 'Structure B100' })).toBeInTheDocument();
  });

  it('says so when the structure does not exist', async () => {
    api.getStructure.mockRejectedValue(Object.assign(new Error('Not Found'), { status: 404 }));
    renderAt();

    expect(await screen.findByTestId('structure-detail-error')).toMatchTextContent(
      'Structure not found',
    );
  });

  it('says so when it could not be loaded', async () => {
    api.getStructure.mockRejectedValue(Object.assign(new Error('boom'), { status: 500 }));
    renderAt();

    expect(await screen.findByTestId('structure-detail-error')).toMatchTextContent(
      'This structure could not be loaded',
    );
  });

  it('is no longer marked under construction: every tab is built', async () => {
    await showing(bridge());

    expect(screen.queryByText('Under construction')).not.toBeInTheDocument();
  });
});

describe('StructureDetailPage — header', () => {
  it('titles the page with the structure number', async () => {
    await showing(bridge());

    expect(screen.getByRole('heading', { name: 'Structure B100' })).toBeInTheDocument();
  });

  it('shows the structure and its site', async () => {
    const header = await showing(bridge());

    expect(valueOf('Structure #', header)).toBe('B100');
    // Type/Class is on the Details tab, with the bridge.
    expect(within(header).queryByText('Type/Class')).not.toBeInTheDocument();
    // Code and description; legacy shows the raw code alone.
    expect(valueOf('Inspection Status', header)).toBe('OK - Inspected');
    expect(valueOf('Forest District', header)).toBe('Cariboo-Chilcotin Natural Resource District');
    expect(valueOf('Management Area', header)).toBe('Central Cariboo');
    expect(valueOf('Forest Service Road', header)).toBe('CHILCOTIN SOUTH');
    expect(valueOf('Project File ID#-Br.', header)).toBe('6970-01');
    expect(valueOf('Crossing Name', header)).toBe('Riske Cr');
    expect(valueOf('Kilometres', header)).toBe('0.24');
    expect(valueOf('Designated Maintainer', header)).toBe(
      'CANADIAN FOREST PRODUCTS LTD. · 00001271-01',
    );
  });

  it('links the site number to the site, in a new tab', async () => {
    const header = await showing(bridge());

    const link = within(header).getByRole('link', { name: '62-001 (opens in a new tab)' });
    expect(link).toHaveAttribute('href', '/inventory/site/62-001');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('shows a recreation site its district and project, and no management area', async () => {
    const structure = bridge();
    const header = await showing({
      ...structure,
      site: {
        ...structure.site!,
        siteTypeCode: 'REC',
        districtName: 'Rec District',
        projectName: 'Lakeside Trail',
        managementAreaName: null,
      },
    });

    expect(valueOf('Recreation District', header)).toBe('Rec District');
    expect(valueOf('Project Name', header)).toBe('Lakeside Trail');
    expect(within(header).queryByText('Management Area')).not.toBeInTheDocument();
    expect(within(header).queryByText('Forest Service Road')).not.toBeInTheDocument();
  });

  it('shows the site status as a pill on the Information tab, not beside the title', async () => {
    const header = await showing(bridge());

    expect(within(header).getByTestId('structure-site-status')).toMatchTextContent('Active');
    expect(valueOf('Site Status', header)).toBe('Active');
  });

  it('shows no status pill when the site has no status', async () => {
    const structure = bridge();
    await showing({ ...structure, site: { ...structure.site!, siteStatus: NONE } });

    expect(screen.queryByTestId('structure-site-status')).not.toBeInTheDocument();
  });

  it('tags an archived structure, which legacy never showed', async () => {
    await showing(bridge({ active: false }));

    expect(screen.getByTestId('structure-archived')).toMatchTextContent('Archived');
  });

  it('carries no tag on an active one', async () => {
    await showing(bridge());

    expect(screen.queryByTestId('structure-archived')).not.toBeInTheDocument();
  });
});

describe('StructureDetailPage — editing the Structure card', () => {
  const openEdit = async () => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    await showing(bridge());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    await userEvent.click(await screen.findByTestId('structure-details-edit'));
    const form = await screen.findByTestId('structure-details-form');
    // Wait for the dropdowns' lists, which the form fetches as it opens.
    await vi.waitFor(() =>
      expect(within(form).getByTestId('structure-builtByCode')).toHaveValue('MOF'),
    );
    return form;
  };

  const retype = async (form: HTMLElement, field: string, value: string) => {
    const box = within(form).getByTestId(`structure-${field}`);
    await userEvent.clear(box);
    if (value) await userEvent.type(box, value);
  };

  it('offers Edit structure at Level 2 only, as legacy', async () => {
    authorization.canEdit = true;
    await showing(bridge());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    await screen.findByTestId('structure-section-common');

    expect(screen.queryByTestId('structure-details-edit')).not.toBeInTheDocument();
  });

  it('shows Portable Structure with the bridge, not in the Structure card', async () => {
    await showing(bridge());
    await userEvent.click(screen.getByTestId('structure-tab-details'));

    expect(valueOf('Portable Structure?', screen.getByTestId('structure-section-bridge'))).toBe(
      'No',
    );
    expect(
      within(screen.getByTestId('structure-section-common')).queryByText('Portable Structure?'),
    ).not.toBeInTheDocument();
  });

  it('saves the card, and says so', async () => {
    const form = await openEdit();

    expect(within(form).getByTestId('structure-yearBuilt')).toHaveValue('2000');
    await retype(form, 'yearBuilt', '2002');
    await retype(form, 'installationCost', '130000');
    await userEvent.click(within(form).getByRole('radio', { name: 'No' }));
    await userEvent.click(within(form).getByTestId('structure-details-save'));

    await vi.waitFor(() =>
      expect(api.updateStructureDetails).toHaveBeenCalledWith('7', {
        builtByCode: 'MOF',
        yearFabricated: 1998,
        yearBuilt: 2002,
        inventoryAddedYear: 2001,
        sourceCode: 'MOF',
        endOfDesignLifeYear: 2045,
        asBuiltInfoOnFile: false,
        installationCost: 130000,
        materialCost: 0,
      }),
    );
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Structure details saved' }),
      ),
    );
    expect(screen.queryByTestId('structure-details-form')).not.toBeInTheDocument();
  });

  it('refuses beside it a fabrication year after the year installed', async () => {
    const form = await openEdit();

    await retype(form, 'yearFabricated', '2005');
    await userEvent.click(within(form).getByTestId('structure-details-save'));

    expect(form).toMatchTextContent(
      'Year Superstructure Fabricated must not be after Year Superstructure Installed.',
    );
    expect(api.updateStructureDetails).not.toHaveBeenCalled();
  });

  it('warns as legacy did about an unlikely year, and still saves', async () => {
    const form = await openEdit();

    await retype(form, 'yearBuilt', '1850');
    await retype(form, 'yearFabricated', '1849');

    expect(form).toMatchTextContent('Must be greater than or equal to 1900.');
    await userEvent.click(within(form).getByTestId('structure-details-save'));
    await vi.waitFor(() =>
      expect(api.updateStructureDetails).toHaveBeenCalledWith(
        '7',
        expect.objectContaining({ yearBuilt: 1850 }),
      ),
    );
  });

  it('refuses a year that is not four digits, where legacy saved 200', async () => {
    const form = await openEdit();

    await retype(form, 'inventoryAddedYear', '200');
    await userEvent.click(within(form).getByTestId('structure-details-save'));

    expect(form).toMatchTextContent('Year Added to Inventory must be a 4-digit year, e.g. 2004.');
    // An error, not a warning: red, and nothing is sent.
    expect(within(form).getByTestId('structure-inventoryAddedYear')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(api.updateStructureDetails).not.toHaveBeenCalled();
  });

  it("shows a field's error as soon as it is left, without Save, and clears it as it is fixed", async () => {
    const form = await openEdit();

    await retype(form, 'inventoryAddedYear', '200');
    expect(form).not.toMatchTextContent('must be a 4-digit year');
    await userEvent.tab();

    expect(form).toMatchTextContent('Year Added to Inventory must be a 4-digit year, e.g. 2004.');
    await userEvent.type(within(form).getByTestId('structure-inventoryAddedYear'), '4');
    expect(form).not.toMatchTextContent('must be a 4-digit year');
  });

  it('marks the required fields with a red asterisk', async () => {
    const form = await openEdit();

    // Source is required once the structure has one; As Built always.
    expect(form.querySelectorAll('.required-asterisk').length).toBeGreaterThanOrEqual(2);
    expect(form).not.toMatchTextContent('(optional)');
  });

  it('takes no more digits than the column holds', async () => {
    const form = await openEdit();

    await retype(form, 'yearBuilt', '200567');
    await retype(form, 'materialCost', '12345678');

    expect(within(form).getByTestId('structure-yearBuilt')).toHaveValue('2005');
    expect(within(form).getByTestId('structure-materialCost')).toHaveValue('123456');
  });

  it('needs Year Added to Inventory for the sources that call for it', async () => {
    const form = await openEdit();

    await userEvent.selectOptions(within(form).getByTestId('structure-sourceCode'), 'IR');
    await retype(form, 'inventoryAddedYear', '');
    await userEvent.click(within(form).getByTestId('structure-details-save'));

    expect(form).toMatchTextContent('Year Added to Inventory is required for this Source.');
    expect(api.updateStructureDetails).not.toHaveBeenCalled();
  });
});

describe('StructureDetailPage — editing the Bridge card', () => {
  const openEdit = async (overrides: Partial<StructureDetailResponse> = {}) => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    await showing(bridge(overrides));
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    await userEvent.click(await screen.findByTestId('bridge-details-edit'));
    const form = await screen.findByTestId('bridge-details-form');
    await vi.waitFor(() =>
      expect(within(form).getByTestId('bridge-superstructureTypeCode')).toHaveValue('STL'),
    );
    return form;
  };

  it('offers Edit bridge at Level 2 only', async () => {
    authorization.canEdit = true;
    await showing(bridge());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    await screen.findByTestId('structure-section-bridge');

    expect(screen.queryByTestId('bridge-details-edit')).not.toBeInTheDocument();
  });

  it('saves the bridge with its Type/Class, and says so', async () => {
    const form = await openEdit();

    expect(within(form).getByTestId('bridge-typeClassCode')).toHaveValue('TB');
    await userEvent.clear(within(form).getByTestId('bridge-totalBridgeLength'));
    await userEvent.type(within(form).getByTestId('bridge-totalBridgeLength'), '26');
    await userEvent.selectOptions(within(form).getByTestId('bridge-typeClassCode'), 'PB');
    await userEvent.click(within(form).getByTestId('bridge-details-save'));

    await vi.waitFor(() =>
      expect(api.updateBridgeDetails).toHaveBeenCalledWith(
        '7',
        expect.objectContaining({
          typeClassCode: 'PB',
          portable: false,
          totalBridgeLength: 26,
          superstructureTypeCode: 'STL',
          curbTypeCode: 'TMB',
          deckWidth: 4.27,
        }),
      ),
    );
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Bridge details saved' }),
      ),
    );
  });

  it("holds a Durable Bridge to its rules as soon as it's chosen", async () => {
    const form = await openEdit();

    await userEvent.selectOptions(within(form).getByTestId('bridge-curbTypeCode'), '');
    await userEvent.selectOptions(
      within(form).getByTestId('bridge-superstructureTypeCode'),
      'RAIL',
    );
    await userEvent.selectOptions(within(form).getByTestId('bridge-typeClassCode'), 'PB');
    await userEvent.click(within(form).getByTestId('bridge-details-save'));

    expect(form).toMatchTextContent('Curb Type is required for a Durable Bridge.');
    expect(form).toMatchTextContent(
      'Superstructure: temporary materials are not permitted in a Durable Bridge.',
    );
    expect(api.updateBridgeDetails).not.toHaveBeenCalled();
  });

  it('allows only a portable superstructure on a portable structure', async () => {
    const form = await openEdit();

    await userEvent.click(within(form).getAllByRole('radio', { name: 'Yes' })[0]);
    await userEvent.click(within(form).getByTestId('bridge-details-save'));

    expect(form).toMatchTextContent('are allowed when Portable Structure is checked.');
    await userEvent.selectOptions(within(form).getByTestId('bridge-superstructureTypeCode'), 'SBB');
    expect(form).not.toMatchTextContent('are allowed when Portable Structure is checked.');
  });

  it('asks for the details of an Other, as soon as the box is left', async () => {
    const form = await openEdit();

    await userEvent.selectOptions(within(form).getByTestId('bridge-superstructureTypeCode'), 'OTH');
    await userEvent.click(within(form).getByTestId('bridge-superstructureComment'));
    await userEvent.tab();

    expect(form).toMatchTextContent('Superstructure Details is required.');
  });
});

describe('StructureDetailPage — editing the Culvert card', () => {
  const openEdit = async (structure: StructureDetailResponse = culvert()) => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    await showing(structure);
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    await userEvent.click(await screen.findByTestId('culvert-details-edit'));
    const form = await screen.findByTestId('culvert-details-form');
    await vi.waitFor(() =>
      expect(within(form).getByTestId('culvert-materialCode')).toHaveValue('CSP'),
    );
    return form;
  };

  it('saves the culvert, and says so', async () => {
    const form = await openEdit();

    // A culvert's Type/Class cannot change; it is shown, not offered.
    expect(within(form).queryByTestId('bridge-typeClassCode')).not.toBeInTheDocument();
    await userEvent.clear(within(form).getByTestId('culvert-lengthMetres'));
    await userEvent.type(within(form).getByTestId('culvert-lengthMetres'), '20.5');
    await userEvent.click(within(form).getByTestId('culvert-details-save'));

    await vi.waitFor(() =>
      expect(api.updateCulvertDetails).toHaveBeenCalledWith(
        '8',
        expect.objectContaining({
          culvertNumber: 2,
          lengthMetres: 20.5,
          gradient: 2.5,
          culvertTypeCode: 'RND',
          materialCode: 'CSP',
          openingHeightMm: 1200,
        }),
      ),
    );
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Culvert details saved' }),
      ),
    );
  });

  it('needs an open bottom for an open-bottom culvert, and warns as legacy on a long culvert', async () => {
    const form = await openEdit();

    await userEvent.selectOptions(within(form).getByTestId('culvert-culvertTypeCode'), 'OPN');
    await userEvent.selectOptions(within(form).getByTestId('culvert-openBottomCode'), '');
    await userEvent.clear(within(form).getByTestId('culvert-lengthMetres'));
    await userEvent.type(within(form).getByTestId('culvert-lengthMetres'), '150');
    await userEvent.click(within(form).getByTestId('culvert-details-save'));

    expect(form).toMatchTextContent(
      'Open Bottom Substructure is required for an open-bottom culvert.',
    );
    expect(form).toMatchTextContent('Culvert Length is not in the range 5 through 100.');
    expect(api.updateCulvertDetails).not.toHaveBeenCalled();
  });

  it('offers a Wood Log Culvert no Culvert Type or Open Bottom, as legacy', async () => {
    const form = await openEdit(culvert({ typeClass: code('WLC', 'Wood Log Culvert') }));

    expect(within(form).queryByTestId('culvert-culvertTypeCode')).not.toBeInTheDocument();
    expect(within(form).queryByTestId('culvert-openBottomCode')).not.toBeInTheDocument();
  });
});

describe('StructureDetailPage — Replacement Details', () => {
  const openEdit = async () => {
    authorization.canEdit = true;
    authorization.isPeng = true;
    await showing(bridge());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    const card = await screen.findByTestId('structure-section-replacement');
    await userEvent.click(within(card).getByTestId('replacement-details-edit'));
    return within(card).findByTestId('replacement-details-form');
  };

  const retype = async (form: HTMLElement, field: string, value: string) => {
    const box = within(form).getByTestId(`replacement-${field}`);
    await userEvent.clear(box);
    if (value) await userEvent.type(box, value);
  };

  it("is a P.Eng's to edit, as legacy", async () => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    await showing(bridge());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    await screen.findByTestId('structure-section-replacement');

    expect(screen.queryByTestId('replacement-details-edit')).not.toBeInTheDocument();
  });

  it('saves the years, cost and comments, and says so', async () => {
    const form = await openEdit();

    await retype(form, 'closureYear', '2060');
    await retype(form, 'replacementYear', '2055');
    await retype(form, 'loadRestrictionYear', '2050');
    await retype(form, 'cost', '450000');
    await userEvent.type(within(form).getByTestId('replacement-costComment'), 'Steel');
    await userEvent.click(within(form).getByTestId('replacement-details-save'));

    await vi.waitFor(() =>
      expect(api.updateReplacementDetails).toHaveBeenCalledWith(
        '7',
        expect.objectContaining({
          closureYear: 2060,
          replacementYear: 2055,
          loadRestrictionYear: 2050,
          cost: 450000,
        }),
      ),
    );
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Replacement details saved' }),
      ),
    );
  });

  it('keeps load restriction before replacement and replacement before closure', async () => {
    const form = await openEdit();

    await retype(form, 'closureYear', '2050');
    await retype(form, 'replacementYear', '2055');
    await userEvent.tab();

    expect(form).toMatchTextContent('Estimated Replacement must not be after Estimated Closure.');
    await userEvent.click(within(form).getByTestId('replacement-details-save'));
    expect(api.updateReplacementDetails).not.toHaveBeenCalled();
  });
});

describe('StructureDetailPage — Replacement History', () => {
  const withReplacedBy = () => ({ ...bridge(), replacedBy: [{ id: '9', structureName: 'B900' }] });

  const openCard = async () => {
    authorization.canEdit = true;
    authorization.isPeng = true;
    await showing(withReplacedBy());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    return screen.findByTestId('structure-section-replacement-history');
  };

  it("shows legacy's table of what this structure replaced, and what replaced it", async () => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    authorization.isPeng = false;
    await showing(withReplacedBy());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    const card = await screen.findByTestId('structure-section-replacement-history');

    const rows = within(card).getAllByRole('row');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchTextContent(
      'This structure was replaced by structure #This structure replaced structure #',
    );
    expect(rows[1]).toMatchTextContent('B100B050 (opens in a new tab)');
    expect(within(rows[1]).getByRole('link')).toHaveAttribute('href', '/inventory/structure/3');
    expect(valueOf('This structure was replaced by structure #', card)).toBe(
      'B900 (opens in a new tab)',
    );
    // A P.Eng's to change, as legacy.
    expect(within(card).queryByTestId('replacement-add')).not.toBeInTheDocument();
    expect(within(card).queryByTestId('replacement-delete-3')).not.toBeInTheDocument();
  });

  it('says when there are none', async () => {
    await showing({ ...bridge(), replaced: [], replacedBy: [] });
    await userEvent.click(screen.getByTestId('structure-tab-details'));

    expect(await screen.findByTestId('structure-section-replacement-history')).toMatchTextContent(
      'There are no replacement details for this structure.',
    );
  });

  it('adds "this structure replaces" a Structure # picked from suggestions', async () => {
    api.suggestStructureNames.mockResolvedValue([
      { id: '40', structureName: 'B040', archived: true },
    ]);
    api.createReplacement.mockResolvedValue(undefined);
    const card = await openCard();

    await userEvent.click(within(card).getByTestId('replacement-add'));
    const dialog = await screen.findByTestId('replacement-dialog');
    // This structure, shown and not editable.
    expect(within(dialog).getByTestId('replacement-replacing')).toHaveTextContent('B100');
    expect(within(dialog).getAllByRole('combobox')).toHaveLength(1);
    expect(dialog).toMatchTextContent(/replaces/);
    await userEvent.type(within(dialog).getByRole('combobox'), 'B04');
    await userEvent.click(await within(dialog).findByRole('option', { name: /B040 \(archived\)/ }));
    await userEvent.click(within(dialog).getByTestId('replacement-save'));

    await vi.waitFor(() =>
      expect(api.createReplacement).toHaveBeenCalledWith('7', { replacedName: 'B040' }),
    );
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Replacement added' }),
      ),
    );
  });

  it('refuses a repeat or this structure, and shows what the server refuses', async () => {
    api.createReplacement.mockRejectedValue({
      body: { fieldErrors: { replacedName: 'Structure # B404 does not exist.' } },
    });
    const card = await openCard();
    await userEvent.click(within(card).getByTestId('replacement-add'));
    const dialog = await screen.findByTestId('replacement-dialog');
    const box = within(dialog).getByRole('combobox');

    await userEvent.click(within(dialog).getByTestId('replacement-save'));
    expect(dialog).toMatchTextContent('Replaced Structure # is required.');
    await userEvent.type(box, 'B050');
    expect(dialog).toMatchTextContent('Replacement already exists.');
    await userEvent.clear(box);
    await userEvent.type(box, 'B100');
    expect(dialog).toMatchTextContent('A structure cannot replace itself.');
    expect(api.createReplacement).not.toHaveBeenCalled();

    await userEvent.clear(box);
    await userEvent.type(box, 'B404');
    await userEvent.click(within(dialog).getByTestId('replacement-save'));
    await vi.waitFor(() => expect(dialog).toMatchTextContent('Structure # B404 does not exist.'));
  });

  it('deletes a replacement after asking, and has no edit', async () => {
    api.deleteReplacement.mockResolvedValue(undefined);
    const card = await openCard();
    expect(within(card).queryByRole('button', { name: /^Edit/ })).not.toBeInTheDocument();

    await userEvent.click(within(card).getByTestId('replacement-delete-3'));
    const confirm = await screen.findByRole('dialog', { name: /Delete replacement/ });
    expect(confirm).toMatchTextContent(/replacement of B050 by B100/);
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete' }));
    await vi.waitFor(() => expect(api.deleteReplacement).toHaveBeenCalledWith('7', '3'));
  });
});

describe('StructureDetailPage — Load Rating Details', () => {
  const openDetailsTab = async () => {
    await showing(bridge());
    await userEvent.click(screen.getByTestId('structure-tab-details'));
    return screen.findByTestId('structure-section-load-rating');
  };

  const openEdit = async () => {
    const card = await openDetailsTab();
    await userEvent.click(within(card).getByTestId('load-rating-details-edit'));
    return within(card).findByTestId('load-rating-details-form');
  };

  it('lets Level 1 change Load Rating Review Required alone', async () => {
    authorization.canEdit = true;
    const form = await openEdit();

    expect(within(form).queryByTestId('load-rating-posting')).not.toBeInTheDocument();
    expect(within(form).queryByTestId('load-rating-vehicle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('load-rating-add')).not.toBeInTheDocument();
    await userEvent.click(within(form).getByRole('radio', { name: 'Yes' }));
    // Legacy's advice: a review still asked for although a rating is recorded.
    expect(form).toMatchTextContent(
      'Load rating review is required although a rating is recorded.',
    );
    await userEvent.click(within(form).getByTestId('load-rating-details-save'));

    await vi.waitFor(() =>
      expect(api.updateLoadRatingDetails).toHaveBeenCalledWith('7', {
        loadPostingSignsCode: 'N',
        reviewRequired: true,
        designVehicleCode: 'L75',
        designVehicleComment: '',
        // The vehicle was left as it was, so the stored 63 stays, not L-75's 75.
        designLoadRating: 63,
      }),
    );
  });

  it("refuses a Level 2 user's Other vehicle, whose words only a P.Eng may enter, as legacy", async () => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    const form = await openEdit();
    await vi.waitFor(() =>
      expect(within(form).getByTestId('load-rating-vehicle')).toHaveValue('L75'),
    );

    await userEvent.selectOptions(within(form).getByTestId('load-rating-vehicle'), 'OTHER');
    // No fixed rating for Other: the design rating is typed.
    expect(within(form).getByTestId('load-rating-design')).toBeInTheDocument();
    await userEvent.click(within(form).getByTestId('load-rating-details-save'));

    expect(form).toMatchTextContent(
      'Design Vehicle Configuration Details is required. Only a P.Eng can enter them.',
    );
    expect(api.updateLoadRatingDetails).not.toHaveBeenCalled();
  });

  it('adds a load rating with a reason that fits the change, and words for Other', async () => {
    authorization.canEdit = true;
    authorization.isPeng = true;
    const card = await openDetailsTab();

    await userEvent.click(within(card).getByTestId('load-rating-add'));
    const dialog = await screen.findByTestId('load-rating-dialog');
    expect(within(dialog).getByTestId('load-rating-reason')).toBeDisabled();
    await userEvent.type(within(dialog).getByTestId('load-rating-value'), '70');
    // An increase on the current 60: Inspection and Other, not No change.
    const reasons = within(within(dialog).getByTestId('load-rating-reason'))
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(reasons).toEqual(['Choose a reason', 'INSP - Inspection', 'OTHR - Other']);
    await userEvent.selectOptions(within(dialog).getByTestId('load-rating-reason'), 'OTHR');
    await userEvent.click(within(dialog).getByTestId('load-rating-save'));
    expect(dialog).toMatchTextContent('Load Rating Reason Comment is required for Other.');
    await userEvent.type(within(dialog).getByTestId('load-rating-comment'), 'Retested');
    await userEvent.click(within(dialog).getByTestId('load-rating-save'));

    await vi.waitFor(() =>
      expect(api.createLoadRating).toHaveBeenCalledWith('7', {
        rating: 70,
        reasonCode: 'OTHR',
        reasonComment: 'Retested',
      }),
    );
  });

  it('offers a P.Eng Edit and Delete on manual ratings only, and deletes once confirmed', async () => {
    authorization.canEdit = true;
    authorization.isPeng = true;
    await openDetailsTab();

    expect(screen.queryByTestId('load-rating-edit-r2')).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId('load-rating-edit-r1'));
    const dialog = await screen.findByTestId('load-rating-dialog');
    expect(within(dialog).getByTestId('load-rating-value')).toHaveValue('63');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await userEvent.click(screen.getByTestId('load-rating-delete-r1'));
    await userEvent.click(
      within(await screen.findByRole('dialog', { name: /Delete load rating/ })).getByRole(
        'button',
        { name: 'Delete' },
      ),
    );
    await vi.waitFor(() => expect(api.deleteLoadRating).toHaveBeenCalledWith('7', 'r1'));
  });
});

describe('StructureDetailPage — editing the structure and site', () => {
  const newSite = {
    ...bridge().site!,
    siteId: '62-145',
    siteStatus: code('ACT', 'Active'),
    crossingName: 'Bonaparte R',
    districtName: 'Thompson Rivers Natural Resource District',
    forestServiceRoad: 'BONAPARTE/FROSTY',
    kilometres: '12.5',
  };

  const openEdit = async () => {
    await showing(bridge());
    await userEvent.click(screen.getByTestId('information-edit'));
    return screen.findByTestId('information-form');
  };

  const typeSite = async (form: HTMLElement, siteId: string) => {
    const box = within(form).getByTestId('information-site-id');
    await userEvent.clear(box);
    await userEvent.type(box, siteId);
  };

  it('offers no Edit to a read-only user', async () => {
    await showing(bridge());

    expect(screen.queryByTestId('information-edit')).not.toBeInTheDocument();
  });

  it('lets Level 1 move the structure to another site, previewing it, but not rename it', async () => {
    authorization.canEdit = true;
    api.previewSite.mockResolvedValue({ site: newSite, problem: null });
    const form = await openEdit();

    expect(within(form).queryByTestId('information-structure-name')).not.toBeInTheDocument();
    expect(valueOf('Structure #', form)).toBe('B100');
    await typeSite(form, '62-145');

    // Every field stays in its place; the site's show the typed site once it is found.
    await vi.waitFor(() => expect(form).toMatchTextContent('Showing site 62-145.'));
    expect(valueOf('Crossing Name', form)).toBe('Bonaparte R');
    expect(valueOf('Forest Service Road', form)).toBe('BONAPARTE/FROSTY');
    expect(within(form).queryByText('Type/Class')).not.toBeInTheDocument();
    expect(api.previewSite).toHaveBeenLastCalledWith('62-145');
    await userEvent.click(within(form).getByTestId('information-save'));

    await vi.waitFor(() =>
      expect(api.updateStructureInformation).toHaveBeenCalledWith('7', {
        structureName: 'B100',
        siteId: '62-145',
      }),
    );
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Structure information saved' }),
      ),
    );
    expect(screen.queryByTestId('information-form')).not.toBeInTheDocument();
  });

  it('says beside Site # why the structure cannot move there, and does not save', async () => {
    authorization.canEdit = true;
    api.previewSite.mockResolvedValue({
      site: { ...newSite, siteStatus: code('PP', 'Proposed') },
      problem: 'Site 62-145 is Proposed. A structure cannot be moved to a Proposed site.',
    });
    const form = await openEdit();

    await typeSite(form, '62-145');

    await vi.waitFor(() =>
      expect(form).toMatchTextContent(
        'Site 62-145 is Proposed. A structure cannot be moved to a Proposed site.',
      ),
    );
    await userEvent.click(within(form).getByTestId('information-save'));
    expect(api.updateStructureInformation).not.toHaveBeenCalled();
  });

  it('lets Level 2 rename the structure, and shows a name the server refuses beside it', async () => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    api.updateStructureInformation.mockRejectedValue({
      body: { fieldErrors: { structureName: 'Structure B200 already exists.' } },
    });
    const form = await openEdit();

    const name = within(form).getByTestId('information-structure-name');
    expect(name).toHaveValue('B100');
    // The fields that are not edited here are still shown, the site's own.
    expect(valueOf('Crossing Name', form)).toBe('Riske Cr');
    expect(valueOf('Inspection Status', form)).toBe('OK - Inspected');
    await userEvent.clear(name);
    await userEvent.type(name, 'B200');
    await userEvent.click(within(form).getByTestId('information-save'));

    await vi.waitFor(() =>
      expect(api.updateStructureInformation).toHaveBeenCalledWith('7', {
        structureName: 'B200',
        siteId: '62-001',
      }),
    );
    await vi.waitFor(() => expect(form).toMatchTextContent('Structure B200 already exists.'));
    expect(api.previewSite).not.toHaveBeenCalled();
  });

  it('needs a name and, once the structure has one, a site', async () => {
    authorization.canEdit = true;
    authorization.canDelete = true;
    const form = await openEdit();

    await userEvent.clear(within(form).getByTestId('information-structure-name'));
    await userEvent.clear(within(form).getByTestId('information-site-id'));
    await userEvent.click(within(form).getByTestId('information-save'));

    expect(form).toMatchTextContent('Structure # is required.');
    expect(form).toMatchTextContent('Site # is required.');
    expect(api.updateStructureInformation).not.toHaveBeenCalled();
  });

  it('puts the values back with Cancel', async () => {
    authorization.canEdit = true;
    const form = await openEdit();

    await userEvent.click(within(form).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByTestId('information-form')).not.toBeInTheDocument();
    expect(screen.getByTestId('structure-header')).toBeInTheDocument();
  });
});

describe('StructureDetailPage — incomplete data', () => {
  const incomplete = () =>
    bridge({
      outstanding: [
        { section: 'DETAILS', card: 'Bridge', label: 'Deck Width' },
        {
          section: 'DETAILS',
          card: 'Replacement Details',
          label: 'Estimated Load Restriction (year)',
        },
        {
          section: 'INSPECTIONS',
          card: 'Inspection Schedule',
          label: 'Next Planned Routine Inspection',
        },
      ],
    });

  it('counts what is missing above the tabs', async () => {
    await showing(incomplete());

    expect(screen.getByTestId('structure-outstanding-summary')).toMatchTextContent(
      '3 required items outstanding across 2 tabs',
    );
  });

  it("badges each tab with its own count, as nr-frep's tab strip", async () => {
    await showing(incomplete());

    expect(screen.getByRole('img', { name: 'Details: 2 items outstanding' })).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: 'Inspections: 1 item outstanding' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /^Information:/ })).not.toBeInTheDocument();
  });

  it('lists what is missing in each tab, under the section it is in', async () => {
    await showing(incomplete());

    const details = within(screen.getByTestId('structure-details-tab')).getByTestId(
      'structure-outstanding-panel',
    );
    expect(details).toMatchTextContent(/Outstanding in this tab/);
    expect(details).toMatchTextContent(/BridgeDeck Width/);
    expect(details).toMatchTextContent(/Replacement DetailsEstimated Load Restriction \(year\)/);
    expect(details).not.toMatchTextContent(/Next Planned Routine Inspection/);

    const inspections = within(screen.getByTestId('structure-inspections-tab')).getByTestId(
      'structure-outstanding-panel',
    );
    expect(inspections).toMatchTextContent(/Inspection ScheduleNext Planned Routine Inspection/);
  });

  it('folds the list away on request', async () => {
    await showing(incomplete());
    await openDetails();

    await userEvent.click(screen.getByRole('button', { name: /Outstanding/ }));

    expect(screen.queryByText('Deck Width')).not.toBeInTheDocument();
  });

  it('shows none of it for a complete structure', async () => {
    await showing(bridge());

    expect(screen.queryByTestId('structure-outstanding-summary')).not.toBeInTheDocument();
    expect(screen.queryByTestId('structure-outstanding-panel')).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /outstanding/ })).not.toBeInTheDocument();
  });
});

describe('StructureDetailPage — Details tab', () => {
  it('shows the fields every structure has', async () => {
    await showing(bridge());
    const common = screen.getByTestId('structure-section-common');

    expect(valueOf('Built For', common)).toBe('Ministry of Forests');
    expect(valueOf('Year Superstructure Installed', common)).toBe('2000');
    expect(valueOf('As Built Information on File?', common)).toBe('Yes');
    expect(valueOf('Installation Cost ($)', common)).toBe('$125,000');
    // Zero is a value, not nothing stored.
    expect(valueOf('Material Cost ($)', common)).toBe('$0');
  });

  it('shows an em dash where nothing is stored, not an invented value', async () => {
    await showing(
      bridge({
        details: { ...bridge().details, endOfDesignLifeYear: null, builtBy: NONE },
      }),
    );
    const common = screen.getByTestId('structure-section-common');

    expect(valueOf('End of Design Life (year)', common)).toBe('—');
    expect(valueOf('Built For', common)).toBe('—');
  });

  it('shows a bridge its bridge fields and no culvert fields', async () => {
    await showing(bridge());
    const section = screen.getByTestId('structure-section-bridge');

    expect(valueOf('Type/Class', section)).toBe('Timber Bridge');
    expect(valueOf('Number of Spans', section)).toBe('2');
    expect(valueOf('Superstructure', section)).toBe('Steel Girder');
    expect(valueOf('Deck Width (metres)', section)).toBe('4.27');
    expect(screen.queryByTestId('structure-section-culvert')).not.toBeInTheDocument();
  });

  it('warns beside Number of Spans past ten, as legacy, and not before', async () => {
    await showing(bridge({ bridge: { ...bridge().bridge!, spanCount: 11 } }));
    const section = screen.getByTestId('structure-section-bridge');

    expect(within(section).getByTestId('span-count-warning')).toMatchTextContent(
      'Should not be greater than 10.',
    );
    expect(screen.queryAllByTestId('span-count-warning')).toHaveLength(1);
  });

  it('shows no warning for ten spans', async () => {
    await showing(bridge({ bridge: { ...bridge().bridge!, spanCount: 10 } }));

    expect(screen.queryByTestId('span-count-warning')).not.toBeInTheDocument();
  });

  it('adds the "If Other" comment only when a code is Other', async () => {
    await showing(
      bridge({
        bridge: {
          ...bridge().bridge!,
          deckType: code('OTH', 'Other'),
          deckTypeComment: 'Recycled rail ties',
        },
      }),
    );
    const section = screen.getByTestId('structure-section-bridge');

    expect(within(section).getAllByText('If Other, please specify')).toHaveLength(1);
    expect(within(section).getByText('Recycled rail ties')).toBeInTheDocument();
  });

  it('shows Type/Class with the structure fields when there is no bridge or culvert row', async () => {
    await showing(bridge({ bridge: null }));

    expect(valueOf('Type/Class', screen.getByTestId('structure-section-common'))).toBe(
      'Timber Bridge',
    );
  });

  it('shows a culvert its culvert fields, with its number on the site', async () => {
    await showing(culvert());
    const section = screen.getByTestId('structure-section-culvert');

    expect(valueOf('Type/Class', section)).toBe('Culvert');
    expect(valueOf('Culvert Number', section)).toBe('2 of 3');
    expect(valueOf('Culvert Material', section)).toBe('Corrugated Steel Pipe');
    expect(valueOf('Culvert Type', section)).toBe('Round');
    expect(screen.queryByTestId('structure-section-bridge')).not.toBeInTheDocument();
  });

  it('hides Culvert Type and Open Bottom Substructure on a wood log culvert, as legacy does', async () => {
    await showing(culvert({ typeClass: code('WLC', 'Wood Log Culvert') }));
    const section = screen.getByTestId('structure-section-culvert');

    expect(within(section).queryByText('Culvert Type')).not.toBeInTheDocument();
    expect(within(section).queryByText('Open Bottom Substructure')).not.toBeInTheDocument();
  });

  it('lists comments last on the Details tab, newest first, as the server sends them', async () => {
    await showing(bridge());

    await openDetails();
    const cards = Array.from(
      screen.getByTestId('structure-details-tab').querySelectorAll('.structure-detail__card'),
    );
    expect(cards.at(-1)).toBe(screen.getByTestId('structure-section-comments'));
    expect(cards.at(-2)).toBe(screen.getByTestId('structure-section-replacement'));
    const rows = within(screen.getByTestId('structure-section-comments')).getAllByRole('row');

    expect(rows[1]).toMatchTextContent('Deck replaced.');
    expect(rows[1]).toMatchTextContent('IDIR\\JSMITH');
    expect(rows[1]).toMatchTextContent('Jun 3, 2024 2:05 PM');
    expect(rows[2]).toMatchTextContent('Installed.');
  });

  it('lets Level 1 add a comment, and says so', async () => {
    authorization.canEdit = true;
    await showing(bridge());
    await openDetails();
    const card = screen.getByTestId('structure-section-comments');

    await userEvent.click(within(card).getByTestId('structure-section-comments-add'));
    const dialog = await screen.findByTestId('comment-dialog');
    expect(dialog).toMatchTextContent(/Add comment/);
    await userEvent.click(within(dialog).getByTestId('comment-save'));
    expect(dialog).toMatchTextContent('Comment is required.');
    await userEvent.type(within(dialog).getByTestId('comment-text'), 'Deck resurfaced.');
    await userEvent.click(within(dialog).getByTestId('comment-save'));

    await vi.waitFor(() =>
      expect(api.addStructureComment).toHaveBeenCalledWith('7', { comment: 'Deck resurfaced.' }),
    );
    expect(api.addPlannedInspectionComment).not.toHaveBeenCalled();
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Comment added' }),
      ),
    );
  });

  it('lets Level 1 edit any comment, as legacy', async () => {
    authorization.canEdit = true;
    await showing(bridge());
    await openDetails();
    const card = screen.getByTestId('structure-section-comments');

    await userEvent.click(within(card).getByTestId('structure-section-comments-edit-2'));
    const dialog = await screen.findByTestId('comment-dialog');
    expect(dialog).toMatchTextContent(/Edit comment/);
    const box = within(dialog).getByTestId('comment-text');
    expect(box).toHaveValue('Deck replaced.');
    await userEvent.clear(box);
    await userEvent.type(box, 'Deck replaced. Again.');
    await userEvent.click(within(dialog).getByTestId('comment-save'));

    await vi.waitFor(() =>
      expect(api.updateStructureComment).toHaveBeenCalledWith('7', '2', {
        comment: 'Deck replaced. Again.',
      }),
    );
  });

  it('offers no add or edit to a read-only user', async () => {
    authorization.canEdit = false;
    await showing(bridge());
    await openDetails();
    const card = screen.getByTestId('structure-section-comments');

    expect(within(card).queryByTestId('structure-section-comments-add')).not.toBeInTheDocument();
    expect(within(card).queryByTestId('structure-section-comments-edit-2')).not.toBeInTheDocument();
  });

  it('says when there are no comments', async () => {
    await showing(bridge({ comments: [] }));

    await openDetails();

    expect(screen.getByTestId('structure-section-comments')).toMatchTextContent('No comments.');
  });

  it('marks the current load rating', async () => {
    await showing(bridge());
    const current = screen.getByTestId('load-rating-r2');

    expect(within(current).getByText('Current')).toBeInTheDocument();
    expect(within(screen.getByTestId('load-rating-r1')).queryByText('Current')).toBeNull();
  });

  it('notes when the current rating is below the design rating', async () => {
    await showing(bridge());

    expect(screen.getByTestId('structure-downrated')).toBeInTheDocument();
  });

  it('hides ratings from before the superstructure was installed until asked, as legacy does', async () => {
    await showing(bridge());
    await openDetails();

    expect(screen.queryByTestId('load-rating-r0')).not.toBeInTheDocument();

    await userEvent.click(
      screen.getByText('Show ratings from before the superstructure was installed (1)'),
    );

    expect(screen.getByTestId('load-rating-r0')).toBeInTheDocument();
  });

  it('links the structures this one replaced, in a new tab', async () => {
    await showing(bridge());
    await openDetails();
    const section = screen.getByTestId('structure-section-replacement-history');

    const link = within(section).getByRole('link', { name: 'B050 (opens in a new tab)' });
    expect(link).toHaveAttribute('href', '/inventory/structure/3');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('shows the replacement details', async () => {
    await showing(bridge());
    const section = screen.getByTestId('structure-section-replacement');

    expect(valueOf('Estimated Load Restriction (year)', section)).toBe('2046');
    expect(valueOf('Estimated Replacement Cost ($)', section)).toBe('$300,000');
    expect(valueOf('Replacement Cost Comments', section)).toBe('Steel prices.');
  });
});

describe('StructureDetailPage — breadcrumb', () => {
  const trail = () =>
    Array.from(document.querySelectorAll('.page-title-breadcrumb .cds--breadcrumb-item')).map(
      (item) => item.textContent?.trim(),
    );

  it('leads back to Structure Search by default', async () => {
    await showing(bridge());

    expect(trail()).toEqual(['Inventory', 'Structure Search']);
  });

  it('leads back to the site when Display Structures sent the user', async () => {
    api.getStructure.mockResolvedValue(bridge());
    renderAt({ pathname: '/inventory/structure/7', state: { fromSiteId: 'BOWRON-001' } });
    await screen.findByTestId('structure-header');

    expect(trail()).toEqual(['Inventory', 'Site Search', 'Site BOWRON-001']);
  });
});

describe('StructureDetailPage — tabs', () => {
  it('opens on Information, the first of its tabs', async () => {
    // Legacy's header, moved into a tab of its own that the page opens on.
    await showing(bridge());

    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent?.trim());
    expect(tabs).toEqual([
      'Information',
      expect.stringMatching(/^Details/),
      'Spans & Piers',
      'Documents & Photos',
      'Inspections',
      'Repairs',
      'Monitoring',
    ]);
    expect(screen.getByTestId('structure-tab-information')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(
      within(screen.getByTestId('structure-information-tab')).getByRole('heading', {
        name: 'Structure and site',
      }),
    ).toBeInTheDocument();
  });
});

describe('StructureDetailPage — Spans & Piers', () => {
  const openSpansAndPiers = () =>
    userEvent.click(screen.getByTestId('structure-tab-spans-and-piers'));

  it('is a tab for a bridge only, as legacy decides by the type/class', async () => {
    await showing(culvert());

    expect(screen.queryByTestId('structure-tab-spans-and-piers')).not.toBeInTheDocument();
  });

  it('loads nothing until the tab is opened', async () => {
    await showing(bridge());

    expect(api.getSpansAndPiers).not.toHaveBeenCalled();

    await openSpansAndPiers();

    expect(api.getSpansAndPiers).toHaveBeenCalledWith('7');
  });

  it('lists the spans in the order the server sends, under the numbering note', async () => {
    api.getSpansAndPiers.mockResolvedValue({
      spans: [
        { id: 's1', number: 1, lengthMetres: 9.75 },
        { id: 's2', number: 2, lengthMetres: 12.5 },
      ],
      piers: [],
    });
    await showing(bridge());

    await openSpansAndPiers();
    const spans = await screen.findByTestId('structure-section-spans');
    const rows = within(spans).getAllByRole('row');

    expect(spans).toMatchTextContent(
      'Span # is numbered Left Bank to Right Bank (Looking Downstream).',
    );
    expect(rows[1]).toMatchTextContent('19.75');
    expect(rows[2]).toMatchTextContent('212.5');
  });

  it("lists the piers with their type's description", async () => {
    api.getSpansAndPiers.mockResolvedValue({
      spans: [],
      piers: [{ id: 'p1', number: 1, type: { code: 'CRIB', description: 'Timber crib' } }],
    });
    await showing(bridge());

    await openSpansAndPiers();
    const piers = await screen.findByTestId('structure-section-piers');

    expect(within(piers).getAllByRole('row')[1]).toMatchTextContent('1Timber crib');
  });

  it('says when a bridge has no spans or piers', async () => {
    await showing(bridge());

    await openSpansAndPiers();

    expect(await screen.findByTestId('structure-section-spans')).toMatchTextContent('No spans.');
    expect(screen.getByTestId('structure-section-piers')).toMatchTextContent('No piers.');
  });

  it('says so when they cannot be loaded', async () => {
    api.getSpansAndPiers.mockRejectedValue(new Error('boom'));
    await showing(bridge());

    await openSpansAndPiers();

    expect(await screen.findByTestId('structure-spans-piers-error')).toMatchTextContent(
      'Spans and piers could not be loaded',
    );
  });

  describe('adding, editing and deleting', () => {
    const span = (id: string, number: number, lengthMetres: number) => ({
      id,
      number,
      lengthMetres,
    });
    const pier = (id: string, number: number, code = 'CRIB', description = 'Timber crib') => ({
      id,
      number,
      type: { code, description },
    });

    /** Opens the tab as a Level 2 user, the lists as given. */
    const openAsLevel2 = async (spans: object[], piers: object[] = []) => {
      authorization.canDelete = true;
      api.getSpansAndPiers.mockResolvedValue({ spans, piers });
      await showing(bridge());
      await openSpansAndPiers();
      await screen.findByTestId('structure-section-spans');
    };

    it('offers nothing to change below Level 2', async () => {
      authorization.canEdit = true;
      api.getSpansAndPiers.mockResolvedValue({
        spans: [span('s1', 1, 10)],
        piers: [pier('p1', 1)],
      });
      await showing(bridge());

      await openSpansAndPiers();
      await screen.findByTestId('structure-span-s1');

      expect(screen.queryByTestId('structure-span-add')).not.toBeInTheDocument();
      expect(screen.queryByTestId('structure-pier-add')).not.toBeInTheDocument();
      expect(screen.queryByTestId('structure-span-edit-s1')).not.toBeInTheDocument();
      expect(
        within(screen.getByTestId('structure-spans-piers-tab')).queryByText('Actions'),
      ).not.toBeInTheDocument();
    });

    it('adds a span at the next number, and says so', async () => {
      await openAsLevel2([span('s1', 1, 10), span('s2', 4, 10)]);

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const dialog = await screen.findByTestId('span-dialog');
      expect(within(dialog).getByTestId('span-number')).toHaveValue('5');
      await userEvent.type(within(dialog).getByTestId('span-length'), '12.5');
      await userEvent.click(within(dialog).getByTestId('span-save'));

      await vi.waitFor(() =>
        expect(api.createSpan).toHaveBeenCalledWith('7', { number: 5, lengthMetres: 12.5 }),
      );
      await vi.waitFor(() =>
        expect(display).toHaveBeenCalledWith(
          expect.objectContaining({ kind: 'success', title: 'Span 5 added' }),
        ),
      );
      expect(screen.queryByTestId('span-dialog')).not.toBeInTheDocument();
    });

    it('says a required field is empty once it is left, and not before', async () => {
      await openAsLevel2([span('s1', 1, 10)]);

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const dialog = await screen.findByTestId('span-dialog');
      expect(dialog).not.toMatchTextContent('Length is required.');
      await userEvent.click(within(dialog).getByTestId('span-length'));
      await userEvent.tab();

      expect(dialog).toMatchTextContent('Length is required.');
      expect(api.createSpan).not.toHaveBeenCalled();
    });

    it('saves on the first click even when leaving a field brings up its error', async () => {
      await openAsLevel2([span('s1', 1, 10)]);

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const dialog = await screen.findByTestId('span-dialog');
      await userEvent.type(within(dialog).getByTestId('span-length'), '1.2345');
      await userEvent.click(within(dialog).getByTestId('span-save'));

      // One click: the save ran (and refused), rather than being lost to the blur.
      expect(dialog).toMatchTextContent('Length can have at most 3 decimal places.');
      expect(api.createSpan).not.toHaveBeenCalled();
    });

    it('checks the number and length beside each before sending', async () => {
      await openAsLevel2([span('s1', 1, 10)]);

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const dialog = await screen.findByTestId('span-dialog');
      await userEvent.clear(within(dialog).getByTestId('span-number'));
      await userEvent.type(within(dialog).getByTestId('span-number'), '0');
      await userEvent.type(within(dialog).getByTestId('span-length'), '1.2345');
      await userEvent.click(within(dialog).getByTestId('span-save'));

      expect(dialog).toMatchTextContent('Span Number must be greater than 0.');
      expect(dialog).toMatchTextContent('Length can have at most 3 decimal places.');
      expect(api.createSpan).not.toHaveBeenCalled();
    });

    it('shows beside Length a span the Bridge Length cannot hold', async () => {
      api.createSpan.mockRejectedValue({
        body: {
          fieldErrors: {
            lengthMetres:
              'The spans would total 30 metres, more than the Bridge Length of 24.5 metres.',
          },
        },
      });
      await openAsLevel2([span('s1', 1, 20)]);

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const dialog = await screen.findByTestId('span-dialog');
      await userEvent.type(within(dialog).getByTestId('span-length'), '10');
      await userEvent.click(within(dialog).getByTestId('span-save'));

      await vi.waitFor(() =>
        expect(dialog).toMatchTextContent(
          'The spans would total 30 metres, more than the Bridge Length of 24.5 metres.',
        ),
      );
      expect(display).not.toHaveBeenCalled();
    });

    it('warns, without stopping it, when adding the eleventh span', async () => {
      await openAsLevel2(Array.from({ length: 10 }, (_, i) => span(`s${i + 1}`, i + 1, 5)));

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const dialog = await screen.findByTestId('span-dialog');

      expect(dialog).toMatchTextContent(
        'This will be span 11. A bridge should not have more than 10 spans.',
      );
      await userEvent.type(within(dialog).getByTestId('span-length'), '5');
      await userEvent.click(within(dialog).getByTestId('span-save'));
      await vi.waitFor(() => expect(api.createSpan).toHaveBeenCalled());
    });

    it("edits a span's number and length", async () => {
      await openAsLevel2([span('s1', 1, 10)]);

      await userEvent.click(screen.getByTestId('structure-span-edit-s1'));
      const dialog = await screen.findByTestId('span-dialog');
      expect(within(dialog).getByTestId('span-length')).toHaveValue('10');
      await userEvent.clear(within(dialog).getByTestId('span-length'));
      await userEvent.type(within(dialog).getByTestId('span-length'), '11.25');
      await userEvent.click(within(dialog).getByTestId('span-save'));

      await vi.waitFor(() =>
        expect(api.updateSpan).toHaveBeenCalledWith('7', 's1', { number: 1, lengthMetres: 11.25 }),
      );
    });

    it("deletes a span once confirmed, and gives the server's reason when it refuses", async () => {
      api.deleteSpan.mockRejectedValueOnce({
        status: 409,
        body: { detail: "Span can't be deleted, it is associated with an inspection." },
      });
      await openAsLevel2([span('s1', 1, 10), span('s2', 2, 10)]);

      await userEvent.click(screen.getByTestId('structure-span-delete-s2'));
      expect(api.deleteSpan).not.toHaveBeenCalled();
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

      await vi.waitFor(() =>
        expect(display).toHaveBeenCalledWith(
          expect.objectContaining({
            kind: 'error',
            title: 'Span 2 was not deleted',
            subtitle: "Span can't be deleted, it is associated with an inspection.",
          }),
        ),
      );
      await userEvent.click(screen.getByTestId('structure-span-delete-s2'));
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
      await vi.waitFor(() =>
        expect(display).toHaveBeenCalledWith(
          expect.objectContaining({ kind: 'success', title: 'Span 2 deleted' }),
        ),
      );
      expect(api.deleteSpan).toHaveBeenCalledWith('7', 's2');
    });

    it('adds a pier with its type when the spans will still outnumber the piers', async () => {
      await openAsLevel2([span('s1', 1, 10), span('s2', 2, 10)]);

      await userEvent.click(screen.getByTestId('structure-pier-add'));
      const dialog = await screen.findByTestId('pier-dialog');
      expect(within(dialog).getByTestId('pier-number')).toHaveValue('1');
      await vi.waitFor(() =>
        expect(within(within(dialog).getByTestId('pier-type')).getAllByRole('option')).toHaveLength(
          3,
        ),
      );
      await userEvent.selectOptions(within(dialog).getByTestId('pier-type'), 'CONC');
      await userEvent.click(within(dialog).getByTestId('pier-save'));

      await vi.waitFor(() =>
        expect(api.createPier).toHaveBeenCalledWith('7', { number: 1, typeCode: 'CONC' }),
      );
    });

    it('refuses another pier while the spans would not outnumber the piers', async () => {
      await openAsLevel2([span('s1', 1, 10), span('s2', 2, 10)], [pier('p1', 1)]);

      await userEvent.click(screen.getByTestId('structure-pier-add'));
      const dialog = await screen.findByTestId('pier-dialog');
      await vi.waitFor(() =>
        expect(within(within(dialog).getByTestId('pier-type')).getAllByRole('option')).toHaveLength(
          3,
        ),
      );
      await userEvent.selectOptions(within(dialog).getByTestId('pier-type'), 'CONC');
      await userEvent.click(within(dialog).getByTestId('pier-save'));

      expect(within(dialog).getByTestId('pier-error')).toMatchTextContent(
        'Number of spans must be greater than number of piers.',
      );
      expect(api.createPier).not.toHaveBeenCalled();
    });

    it('says in the dialog that a structure with no bridge record can take no span or pier', async () => {
      authorization.canDelete = true;
      await showing(bridge({ bridge: null }));
      await openSpansAndPiers();
      await screen.findByTestId('structure-section-spans');

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const spanDialog = await screen.findByTestId('span-dialog');
      expect(within(spanDialog).getByTestId('span-refusal')).toMatchTextContent(
        'This structure has no bridge record, so no spans or piers can be added to it.',
      );
      expect(within(spanDialog).getByTestId('span-length')).toBeDisabled();
      expect(within(spanDialog).getByTestId('span-save')).toBeDisabled();
      await userEvent.click(within(spanDialog).getByRole('button', { name: 'Cancel' }));

      await userEvent.click(screen.getByTestId('structure-pier-add'));
      const pierDialog = await screen.findByTestId('pier-dialog');
      expect(within(pierDialog).getByTestId('pier-error')).toMatchTextContent(
        'This structure has no bridge record, so no spans or piers can be added to it.',
      );
      expect(within(pierDialog).getByTestId('pier-save')).toBeDisabled();
    });

    it("shows the server's refusal for want of a bridge record in the dialog, not a toast", async () => {
      api.createSpan.mockRejectedValue({
        status: 409,
        body: { detail: 'Structure 7 has no bridge record, so it has no spans or piers.' },
      });
      await openAsLevel2([]);

      await userEvent.click(screen.getByTestId('structure-span-add'));
      const dialog = await screen.findByTestId('span-dialog');
      await userEvent.type(within(dialog).getByTestId('span-length'), '10');
      await userEvent.click(within(dialog).getByTestId('span-save'));

      await vi.waitFor(() =>
        expect(within(dialog).getByTestId('span-refusal')).toMatchTextContent(
          'Structure 7 has no bridge record, so it has no spans or piers.',
        ),
      );
      expect(within(dialog).getByTestId('span-save')).toBeDisabled();
      expect(display).not.toHaveBeenCalled();
    });

    it("keeps a pier's expired type in the list, so it can be saved unchanged", async () => {
      await openAsLevel2(
        [span('s1', 1, 10), span('s2', 2, 10)],
        [pier('p1', 1, 'OLD', 'Retired type')],
      );

      await userEvent.click(screen.getByTestId('structure-pier-edit-p1'));
      const dialog = await screen.findByTestId('pier-dialog');
      expect(within(dialog).getByTestId('pier-type')).toHaveValue('OLD');
      expect(within(dialog).getByRole('option', { name: 'Retired type' })).toBeInTheDocument();
      await userEvent.clear(within(dialog).getByTestId('pier-number'));
      await userEvent.type(within(dialog).getByTestId('pier-number'), '3');
      await userEvent.click(within(dialog).getByTestId('pier-save'));

      await vi.waitFor(() =>
        expect(api.updatePier).toHaveBeenCalledWith('7', 'p1', { number: 3, typeCode: 'OLD' }),
      );
    });
  });
});

describe('StructureDetailPage — Documents & Photos', () => {
  const openDocuments = () => userEvent.click(screen.getByTestId('structure-tab-documents'));

  const doc = (id: string, overrides: object = {}) => ({
    id,
    inspectionId: null,
    inspectionDate: null,
    attachmentType: { code: 'PHOTO', description: 'Photograph' },
    created: '2020-05-01',
    extension: 'JPG',
    description: 'Deck from the north abutment',
    filename: `${id}.jpg`,
    auditRecord: false,
    ...overrides,
  });

  describe('adding, editing and deleting', () => {
    const fileOf = (name: string, bytes = 10) =>
      new File([new Uint8Array(bytes)], name, { type: 'application/octet-stream' });

    const todayIso = () => {
      const now = new Date();
      const pad = (value: number) => String(value).padStart(2, '0');
      return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
    };

    const openUpload = async (files: File[]) => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      await showing(bridge());
      await openDocuments();
      await userEvent.click(await screen.findByTestId('structure-documents-add'));
      const dialog = await screen.findByTestId('upload-documents-dialog');
      // Wait for the type list, which a PDF's default depends on.
      await vi.waitFor(() =>
        expect(dialog.querySelectorAll('input[type="file"]').length).toBeGreaterThan(0),
      );
      await vi.waitFor(() =>
        expect(configurationApi.getFileAttachmentTypeCodes).toHaveBeenCalled(),
      );
      await userEvent.upload(dialog.querySelector('input[type="file"]') as HTMLInputElement, files);
      return dialog;
    };

    it('offers Add documents to Level 2 and up only, and Edit from Level 1', async () => {
      authorization.canEdit = true;
      api.getDocuments.mockResolvedValue({ documents: [doc('1')] });
      await showing(bridge());
      await openDocuments();

      await screen.findByTestId('structure-document-1');
      expect(screen.queryByTestId('structure-documents-add')).not.toBeInTheDocument();
      expect(screen.getByTestId('structure-document-edit-1')).toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-delete-1')).not.toBeInTheDocument();
    });

    it('offers View alone on an audit record, to every user', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      api.getDocuments.mockResolvedValue({
        documents: [
          doc('1', {
            inspectionId: '30',
            attachmentType: { code: 'OTH', description: 'Other' },
            extension: 'PDF',
            description: 'Audit record - Submitted - saved by IDIR\\JSMITH on 2026-10-26 14:05',
            filename: '62-001 - B1 - 2025 inspection - Submitted - 2026-10-26 1405.pdf',
            auditRecord: true,
          }),
          doc('2'),
        ],
      });
      await showing(bridge());
      await openDocuments();

      await screen.findByTestId('structure-document-1');
      expect(screen.getByTestId('structure-document-view-1')).toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-edit-1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-delete-1')).not.toBeInTheDocument();
      // Any other file keeps its own.
      expect(screen.getByTestId('structure-document-edit-2')).toBeInTheDocument();
      expect(screen.getByTestId('structure-document-delete-2')).toBeInTheDocument();
    });

    it("shows each inspection's latest audit record, the earlier ones once asked", async () => {
      const audit = (id: string, inspectionId: string, created: string) =>
        doc(id, {
          inspectionId,
          inspectionDate: '2023-06-01',
          attachmentType: { code: 'OTH', description: 'Other' },
          created,
          extension: 'PDF',
          description: `Audit record - Submitted - saved by IDIR\\JSMITH on ${created} 10:00`,
          filename: `audit-${id}.pdf`,
          auditRecord: true,
        });
      api.getDocuments.mockResolvedValue({
        documents: [
          // The same day: the newest is the highest id, whatever the order.
          audit('11', '30', '2026-10-26'),
          audit('13', '30', '2026-10-26'),
          audit('12', '30', '2026-10-26'),
          audit('21', '31', '2026-10-20'),
          doc('5', { inspectionId: '30', inspectionDate: '2023-06-01' }),
        ],
      });
      await showing(bridge());
      await openDocuments();

      await screen.findByTestId('structure-document-13');
      expect(screen.getByTestId('structure-document-21')).toBeInTheDocument();
      expect(screen.getByTestId('structure-document-5')).toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-11')).not.toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-12')).not.toBeInTheDocument();

      await userEvent.click(screen.getByLabelText('Show earlier audit records (2)'));

      expect(screen.getByTestId('structure-document-11')).toBeInTheDocument();
      expect(screen.getByTestId('structure-document-12')).toBeInTheDocument();
    });

    it('offers no audit-record checkbox when no inspection has more than one', async () => {
      api.getDocuments.mockResolvedValue({ documents: [doc('1')] });
      await showing(bridge());
      await openDocuments();

      await screen.findByTestId('structure-document-1');
      expect(screen.queryByLabelText(/Show earlier audit records/)).not.toBeInTheDocument();
    });

    it('offers a read-only user View alone', async () => {
      api.getDocuments.mockResolvedValue({ documents: [doc('1')] });
      await showing(bridge());
      await openDocuments();

      await screen.findByTestId('structure-document-1');
      // View alone — it is everyone's.
      expect(screen.getByTestId('structure-document-view-1')).toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-edit-1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-delete-1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('structure-documents-add')).not.toBeInTheDocument();
    });

    it('adds the chosen files one request at a time, each with its type, date and description', async () => {
      const deck = fileOf('deck.jpg');
      const plan = fileOf('plan.pdf');
      const dialog = await openUpload([deck, plan]);

      const rows = within(dialog).getAllByRole('region');
      // As legacy, a PDF starts as Other; the photo waits for a choice; both dated today.
      expect(within(rows[1]).getByRole('combobox')).toHaveValue('OTH');
      expect(within(rows[0]).getByRole('combobox')).toHaveValue('');
      await userEvent.selectOptions(within(rows[0]).getByRole('combobox'), 'PHOTO');
      await userEvent.type(within(rows[1]).getByRole('textbox', { name: 'Description' }), 'Plan.');
      await userEvent.click(within(dialog).getByTestId('upload-save'));

      await vi.waitFor(() => expect(api.addDocument).toHaveBeenCalledTimes(2));
      expect(api.addDocument).toHaveBeenNthCalledWith(1, '7', deck, {
        attachmentTypeCode: 'PHOTO',
        createdDate: todayIso(),
        description: '',
      });
      expect(api.addDocument).toHaveBeenNthCalledWith(2, '7', plan, {
        attachmentTypeCode: 'OTH',
        createdDate: todayIso(),
        description: 'Plan.',
      });
      await vi.waitFor(() =>
        expect(screen.queryByTestId('upload-documents-dialog')).not.toBeInTheDocument(),
      );
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: '2 documents added' }),
      );
    });

    it('checks every file and its details before sending, beside each', async () => {
      const dialog = await openUpload([
        fileOf('notes'),
        fileOf('big.jpg', 10 * 1024 * 1024 + 1),
        fileOf('plan.pdf'),
      ]);

      await userEvent.click(within(dialog).getByTestId('upload-save'));

      // Which types are accepted is the server's to say, as legacy's; a file with no type at all is
      // caught here.
      const rows = within(dialog).getAllByRole('region');
      expect(rows[0]).toMatchTextContent('notes has no file type.');
      expect(rows[1]).toMatchTextContent('big.jpg is larger than 10 MB.');
      expect(rows[0]).toMatchTextContent('Photo View is required.');
      expect(rows[2]).toMatchTextContent('Description is required for this Photo View.');
      expect(api.addDocument).not.toHaveBeenCalled();
    });

    it('lists the accepted formats in the hint', async () => {
      const dialog = await openUpload([]);

      await vi.waitFor(() =>
        expect(within(dialog).getByTestId('upload-documents-helper')).toHaveTextContent(
          'Up to 15 files at a time, 10 MB each. Accepted formats: JPG, PDF.',
        ),
      );
    });

    it('flags a file type that may not be added as soon as it is picked', async () => {
      const dialog = await openUpload([fileOf('notes.exe'), fileOf('deck.jpg')]);

      const rows = within(dialog).getAllByRole('region');
      await vi.waitFor(() =>
        expect(rows[0]).toMatchTextContent('Files of type .exe cannot be added.'),
      );
      expect(rows[1]).not.toMatchTextContent('cannot be added');
      await userEvent.click(within(dialog).getByTestId('upload-save'));
      expect(api.addDocument).not.toHaveBeenCalled();
    });

    it('drops a file from the batch with Remove', async () => {
      const dialog = await openUpload([fileOf('a.jpg'), fileOf('b.jpg')]);

      await userEvent.click(within(dialog).getByRole('button', { name: 'Remove a.jpg' }));

      expect(within(dialog).getAllByRole('region')).toHaveLength(1);
    });

    it('sends the next file only once the last has been stored', async () => {
      let finish: (value: { id: string }) => void = () => undefined;
      api.addDocument.mockImplementationOnce(() => new Promise((resolve) => (finish = resolve)));
      const dialog = await openUpload([fileOf('a.jpg'), fileOf('b.jpg')]);
      for (const row of within(dialog).getAllByRole('region')) {
        await userEvent.selectOptions(within(row).getByRole('combobox'), 'PHOTO');
      }

      await userEvent.click(within(dialog).getByTestId('upload-save'));

      await vi.waitFor(() =>
        expect(within(dialog).getByTestId('upload-save')).toMatchTextContent('Adding 1 of 2…'),
      );
      expect(api.addDocument).toHaveBeenCalledTimes(1);
      finish({ id: '91' });
      await vi.waitFor(() => expect(api.addDocument).toHaveBeenCalledTimes(2));
    });

    it('keeps the files not added, with the reason beside each, and lets the rest go', async () => {
      api.addDocument
        .mockResolvedValueOnce({ id: '91' })
        .mockRejectedValueOnce({
          body: {
            fieldErrors: { attachmentTypeCode: 'Photo View is not one of the listed types.' },
          },
        })
        .mockRejectedValueOnce({ status: 413, body: { detail: 'The file is too large.' } });
      const dialog = await openUpload([fileOf('a.jpg'), fileOf('b.jpg'), fileOf('c.jpg')]);
      for (const row of within(dialog).getAllByRole('region')) {
        await userEvent.selectOptions(within(row).getByRole('combobox'), 'PHOTO');
      }

      await userEvent.click(within(dialog).getByTestId('upload-save'));

      await vi.waitFor(() => expect(within(dialog).getAllByRole('region')).toHaveLength(2));
      const [b, c] = within(dialog).getAllByRole('region');
      expect(b).toHaveAccessibleName('b.jpg');
      expect(b).toMatchTextContent('Photo View is not one of the listed types.');
      expect(c).toHaveAccessibleName('c.jpg');
      expect(c).toMatchTextContent('Not added: The file is too large.');
      expect(within(dialog).getByTestId('upload-batch-error')).toMatchTextContent(
        '1 of 3 files were added. Correct the files below and add them again.',
      );
      expect(screen.getByTestId('upload-documents-dialog')).toBeInTheDocument();
    });

    it("edits a document's Photo View and description", async () => {
      authorization.canEdit = true;
      api.getDocuments.mockResolvedValue({ documents: [doc('1')] });
      await showing(bridge());
      await openDocuments();

      await userEvent.click(await screen.findByTestId('structure-document-edit-1'));
      const dialog = await screen.findByTestId('edit-document-dialog');
      await vi.waitFor(() =>
        expect(within(dialog).getByTestId('edit-document-type')).toHaveValue('PHOTO'),
      );
      expect(dialog).toMatchTextContent('1.jpg');
      await userEvent.selectOptions(within(dialog).getByTestId('edit-document-type'), 'OTH');
      await userEvent.clear(within(dialog).getByTestId('edit-document-description'));
      await userEvent.click(within(dialog).getByTestId('edit-document-save'));
      expect(dialog).toMatchTextContent('Description is required for this Photo View.');

      await userEvent.type(within(dialog).getByTestId('edit-document-description'), 'South side.');
      await userEvent.click(within(dialog).getByTestId('edit-document-save'));

      await vi.waitFor(() =>
        expect(api.updateDocument).toHaveBeenCalledWith('7', '1', {
          attachmentTypeCode: 'OTH',
          description: 'South side.',
        }),
      );
    });

    it('shows a thumbnail of the photo being edited, and names a file it cannot draw', async () => {
      authorization.canEdit = true;
      api.getDocuments.mockResolvedValue({
        documents: [doc('1'), doc('2', { filename: 'notes.doc', extension: 'DOC' })],
      });
      api.getDocumentFile.mockResolvedValue(new Blob(['x'], { type: 'image/jpeg' }));
      await showing(bridge());
      await openDocuments();

      await userEvent.click(await screen.findByTestId('structure-document-edit-1'));
      const photo = await screen.findByTestId('edit-document-dialog');
      const thumbnail = await within(photo).findByTestId('document-thumbnail');
      expect(thumbnail.getAttribute('src')).toMatch(/^blob:/);
      expect(api.getDocumentFile).toHaveBeenCalledWith('7', '1');
      await userEvent.click(within(photo).getByRole('button', { name: 'Cancel' }));

      api.getDocumentFile.mockClear();
      await userEvent.click(screen.getByTestId('structure-document-edit-2'));
      const notes = await screen.findByTestId('edit-document-dialog');
      expect(within(notes).getByTestId('document-thumbnail-placeholder')).toMatchTextContent(
        'notes.doc',
      );
      expect(api.getDocumentFile).not.toHaveBeenCalled();
    });

    it('deletes a document after confirmation, an inspection one too', async () => {
      authorization.canDelete = true;
      api.getDocuments.mockResolvedValue({
        documents: [doc('2', { inspectionId: '30', inspectionDate: '2023-06-01' })],
      });
      await showing(bridge());
      await openDocuments();

      await userEvent.click(await screen.findByTestId('structure-document-delete-2'));
      expect(api.deleteDocument).not.toHaveBeenCalled();
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

      await vi.waitFor(() => expect(api.deleteDocument).toHaveBeenCalledWith('7', '2'));
      await vi.waitFor(() =>
        expect(display).toHaveBeenCalledWith(
          expect.objectContaining({ kind: 'success', title: '2.jpg deleted' }),
        ),
      );
    });

    it("keeps the structure's folder, to hold Add, when only inspections have files", async () => {
      authorization.canDelete = true;
      api.getDocuments.mockResolvedValue({
        documents: [doc('2', { inspectionId: '30', inspectionDate: '2023-06-01' })],
      });
      await showing(bridge());
      await openDocuments();

      const own = await screen.findByTestId('structure-documents-structure');
      expect(own).toMatchTextContent('No photos and/or documents have been loaded.');
      expect(within(own).getByTestId('structure-documents-add')).toBeInTheDocument();
    });
  });

  it('is a tab for a culvert too, after Details where there is no Spans & Piers', async () => {
    await showing(culvert());

    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent?.trim());
    expect(tabs[2]).toBe('Documents & Photos');

    await openDocuments();

    await vi.waitFor(() => expect(api.getDocuments).toHaveBeenCalledWith('8'));
  });

  it('loads nothing until the tab is opened', async () => {
    await showing(bridge());

    expect(api.getDocuments).not.toHaveBeenCalled();
  });

  it("groups the files into the structure's own and each inspection's, as legacy's folders do", async () => {
    api.getDocuments.mockResolvedValue({
      documents: [
        doc('1'),
        doc('2', { inspectionId: '30', inspectionDate: '2023-06-01' }),
        doc('3', { inspectionId: '20', inspectionDate: '2021-06-01' }),
      ],
    });
    await showing(bridge());

    await openDocuments();
    await screen.findByTestId('structure-documents-structure');

    const titles = Array.from(
      screen.getByTestId('structure-documents-tab').querySelectorAll('.structure-detail__card h2'),
    ).map((title) => title.textContent);
    expect(titles).toEqual([
      'Structure Documents & Photos',
      'Inspection: Jun 1, 2023',
      'Inspection: Jun 1, 2021',
    ]);
    const row = screen.getByTestId('structure-document-1');
    expect(row).toMatchTextContent('Photograph');
    expect(row).toMatchTextContent('May 1, 2020');
    expect(row).toMatchTextContent('JPG');
    expect(row).toMatchTextContent('Deck from the north abutment');
    expect(within(row).getAllByRole('cell')[3]).toMatchTextContent('Structure');
    expect(
      within(screen.getByTestId('structure-document-2')).getAllByRole('cell')[3],
    ).toMatchTextContent('Inspection');
  });

  it('shows ten files a card at first, and the rest a page at a time', async () => {
    api.getDocuments.mockResolvedValue({
      documents: Array.from({ length: 12 }, (_, index) => doc(String(index + 1))),
    });
    await showing(bridge());

    await openDocuments();
    await screen.findByTestId('structure-document-1');

    expect(screen.getByTestId('structure-document-10')).toBeInTheDocument();
    expect(screen.queryByTestId('structure-document-11')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(screen.getByTestId('structure-document-11')).toBeInTheDocument();
    expect(screen.getByTestId('structure-document-12')).toBeInTheDocument();
    expect(screen.queryByTestId('structure-document-1')).not.toBeInTheDocument();
  });

  it('hides files from before the superstructure was installed until asked, as legacy does', async () => {
    // Here the superstructure went in in 2015.
    api.getDocuments.mockResolvedValue({
      documents: [doc('1', { created: '2010-01-01' }), doc('2', { created: '2016-01-01' })],
    });
    await showing(bridge({ details: { ...bridge().details, yearBuilt: 2015 } }));

    await openDocuments();
    await screen.findByTestId('structure-document-2');

    expect(screen.queryByTestId('structure-document-1')).not.toBeInTheDocument();

    await userEvent.click(
      screen.getByText('Show documents from before the superstructure was installed (1)'),
    );

    expect(screen.getByTestId('structure-document-1')).toBeInTheDocument();
  });

  it("says so when there are none, in legacy's words", async () => {
    await showing(bridge());

    await openDocuments();

    expect(await screen.findByTestId('structure-documents-empty')).toMatchTextContent(
      'No photos and/or documents have been loaded.',
    );
  });

  it('opens a photo in a new tab, fetched with the signed-in request', async () => {
    api.getDocuments.mockResolvedValue({ documents: [doc('1')] });
    api.getDocumentFile.mockResolvedValue(new Blob(['x'], { type: 'image/jpeg' }));
    const opened = { close: vi.fn(), location: { href: '' } };
    const open = vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);
    await showing(bridge());

    await openDocuments();
    await userEvent.click(
      await screen.findByRole('button', { name: 'View 1.jpg (opens in a new tab)' }),
    );

    expect(api.getDocumentFile).toHaveBeenCalledWith('7', '1');
    expect(open).toHaveBeenCalledWith('', '_blank');
    await vi.waitFor(() => expect(opened.location.href).toMatch(/^blob:/));
    open.mockRestore();
  });

  it('shows the Photo View as text, and View on every row — a file a browser cannot show downloads', async () => {
    api.getDocuments.mockResolvedValue({
      documents: [doc('1'), doc('2', { filename: 'report.docx', extension: 'DOCX' })],
    });
    await showing(bridge());

    await openDocuments();
    const row = await screen.findByTestId('structure-document-1');

    expect(within(row).queryByRole('link')).not.toBeInTheDocument();
    expect(within(row).getByText('Photograph')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('structure-document-2')).getByRole('button', {
        name: 'View report.docx (downloads)',
      }),
    ).toBeInTheDocument();
  });

  it('downloads a file a browser cannot show when its View is pressed, opening no tab', async () => {
    api.getDocuments.mockResolvedValue({
      documents: [doc('2', { filename: 'readings.csv', extension: 'CSV' })],
    });
    api.getDocumentFile.mockResolvedValue(new Blob(['a,b'], { type: 'application/octet-stream' }));
    const open = vi.spyOn(window, 'open');
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await showing(bridge());

    await openDocuments();
    await userEvent.click(
      await screen.findByRole('button', { name: 'View readings.csv (downloads)' }),
    );

    await vi.waitFor(() => expect(click).toHaveBeenCalled());
    expect(api.getDocumentFile).toHaveBeenCalledWith('7', '2');
    expect(open).not.toHaveBeenCalled();
    open.mockRestore();
    click.mockRestore();
  });

  it('says so beside the files when one cannot be opened', async () => {
    api.getDocuments.mockResolvedValue({ documents: [doc('1')] });
    api.getDocumentFile.mockRejectedValue(new Error('boom'));
    const opened = { close: vi.fn(), location: { href: '' } };
    const open = vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);
    await showing(bridge());

    await openDocuments();
    await userEvent.click(
      await screen.findByRole('button', { name: 'View 1.jpg (opens in a new tab)' }),
    );

    expect(await screen.findByTestId('structure-document-open-error')).toMatchTextContent(
      'The file could not be opened',
    );
    expect(opened.close).toHaveBeenCalled();
    open.mockRestore();
  });

  it('says so when the list cannot be loaded', async () => {
    api.getDocuments.mockRejectedValue(new Error('boom'));
    await showing(bridge());

    await openDocuments();

    expect(await screen.findByTestId('structure-documents-error')).toMatchTextContent(
      'Documents and photos could not be loaded',
    );
  });
});

describe('StructureDetailPage — Inspections', () => {
  const openInspections = () => userEvent.click(screen.getByTestId('structure-tab-inspections'));

  describe('adding an inspection', () => {
    it('offers Add unplanned inspection from Level 0, and Add routine inspection from Level 1', async () => {
      authorization.canWriteInspection = true;
      await showing(bridge());
      await openInspections();
      await screen.findByTestId('structure-section-inspections');

      expect(screen.getByTestId('inspection-add-unplanned')).toBeEnabled();
      expect(screen.queryByTestId('inspection-add-routine')).not.toBeInTheDocument();
    });

    it('offers neither to a read-only user', async () => {
      await showing(bridge());
      await openInspections();
      await screen.findByTestId('structure-section-inspections');

      expect(screen.queryByTestId('inspection-add-unplanned')).not.toBeInTheDocument();
      expect(screen.queryByTestId('inspection-add-routine')).not.toBeInTheDocument();
    });

    it('opens a new routine inspection of the structure', async () => {
      authorization.canWriteInspection = true;
      authorization.canEdit = true;
      await showing(bridge());
      await openInspections();

      await userEvent.click(await screen.findByTestId('inspection-add-routine'));

      expect(await screen.findByTestId('landed')).toMatchTextContent(
        '/inspection/new?structureId=7&type=ROUT',
      );
    });

    it('opens a new unplanned inspection of the structure', async () => {
      authorization.canWriteInspection = true;
      await showing(bridge());
      await openInspections();

      await userEvent.click(await screen.findByTestId('inspection-add-unplanned'));

      expect(await screen.findByTestId('landed')).toMatchTextContent(
        '/inspection/new?structureId=7&type=UNP',
      );
    });

    it('waits until the structure is complete, as legacy: pressed early, the outstanding turns red', async () => {
      authorization.canWriteInspection = true;
      authorization.canEdit = true;
      await showing(
        bridge({ outstanding: [{ section: 'DETAILS', card: 'Bridge', label: 'Deck Width' }] }),
      );
      await openInspections();
      expect(screen.getByTestId('inspection-add-note')).toMatchTextContent(
        'once the structure has nothing outstanding',
      );
      expect(screen.getByTestId('structure-outstanding-summary')).toMatchTextContent(
        '1 required item outstanding across 1 tab',
      );

      await userEvent.click(await screen.findByTestId('inspection-add-routine'));

      // Not opened: the banner, the tab counts and the lists say why, in red.
      expect(screen.queryByTestId('landed')).not.toBeInTheDocument();
      expect(screen.getByTestId('structure-outstanding-summary')).toMatchTextContent(
        /Inspection cannot be added.*All outstanding items must be complete first\./,
      );
      expect(
        screen.getByRole('img', {
          name: 'Details: 1 item outstanding, blocking adding an inspection',
        }),
      ).toBeInTheDocument();
    });
  });

  describe('recording a completed close proximity inspection', () => {
    const openDialog = async () => {
      await showing(bridge());
      await openInspections();
      await userEvent.click(await screen.findByTestId('close-proximity-add'));
      return screen.findByTestId('close-proximity-dialog');
    };

    it('offers Add close proximity inspection to a P.Eng only', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      await showing(bridge());
      await openInspections();
      await screen.findByTestId('structure-section-close-proximity');

      expect(screen.queryByTestId('close-proximity-add')).not.toBeInTheDocument();
    });

    it('records the date entered, any date', async () => {
      authorization.isPeng = true;
      const dialog = await openDialog();

      await userEvent.type(within(dialog).getByTestId('close-proximity-date'), '2031/05/04');
      await userEvent.click(within(dialog).getByTestId('close-proximity-save'));

      await vi.waitFor(() =>
        expect(api.addCloseProximityInspection).toHaveBeenCalledWith('7', {
          completedDate: '2031-05-04',
        }),
      );
      await vi.waitFor(() =>
        expect(screen.queryByTestId('close-proximity-dialog')).not.toBeInTheDocument(),
      );
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Close proximity inspection recorded' }),
      );
    });

    it('needs a real date, and says so under the box', async () => {
      authorization.isPeng = true;
      const dialog = await openDialog();

      await userEvent.click(within(dialog).getByTestId('close-proximity-save'));
      expect(dialog).toMatchTextContent('Date is required.');

      await userEvent.type(within(dialog).getByTestId('close-proximity-date'), '2024/02/30');
      await userEvent.click(within(dialog).getByTestId('close-proximity-save'));
      expect(dialog).toMatchTextContent('Date must be a date, as yyyy/mm/dd.');
      expect(api.addCloseProximityInspection).not.toHaveBeenCalled();
    });

    it('says so when the structure cannot take one', async () => {
      authorization.isPeng = true;
      api.addCloseProximityInspection.mockRejectedValue(new Error('stands on no site'));
      const dialog = await openDialog();

      await userEvent.type(within(dialog).getByTestId('close-proximity-date'), '2024/02/01');
      await userEvent.click(within(dialog).getByTestId('close-proximity-save'));

      await vi.waitFor(() =>
        expect(display).toHaveBeenCalledWith(
          expect.objectContaining({
            kind: 'error',
            title: 'The close proximity inspection was not recorded',
          }),
        ),
      );
      expect(screen.getByTestId('close-proximity-dialog')).toBeInTheDocument();
    });
  });

  describe('planned inspection comments', () => {
    const withComment = schedule({
      plannedInspectionComments: [
        { id: '5', text: 'Bring a boat.', userId: 'IDIR\\A', timestamp: '2024-01-01T09:00:00' },
      ],
    });
    const card = () => screen.findByTestId('structure-section-planned-comments');

    it('offers Add comment to Level 2 and up only, and Edit from Level 1 up', async () => {
      authorization.canEdit = true;
      api.getInspectionSchedule.mockResolvedValue(withComment);
      await showing(bridge());
      await openInspections();

      const section = await card();
      expect(
        within(section).queryByTestId('structure-section-planned-comments-add'),
      ).not.toBeInTheDocument();
      expect(
        within(section).getByTestId('structure-section-planned-comments-edit-5'),
      ).toBeInTheDocument();
    });

    it('offers neither to a read-only user', async () => {
      api.getInspectionSchedule.mockResolvedValue(withComment);
      await showing(bridge());
      await openInspections();

      const section = await card();
      expect(within(section).queryByRole('button')).not.toBeInTheDocument();
      expect(within(section).queryByText('Actions')).not.toBeInTheDocument();
    });

    it('adds a comment in a dialog', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      api.getInspectionSchedule.mockResolvedValue(withComment);
      await showing(bridge());
      await openInspections();

      await userEvent.click(
        within(await card()).getByTestId('structure-section-planned-comments-add'),
      );
      const dialog = await screen.findByTestId('comment-dialog');
      expect(dialog).toMatchTextContent('Add planned inspection comment');
      await userEvent.type(within(dialog).getByTestId('comment-text'), 'Use the UBIU.');
      await userEvent.click(within(dialog).getByTestId('comment-save'));

      await vi.waitFor(() =>
        expect(api.addPlannedInspectionComment).toHaveBeenCalledWith('7', {
          comment: 'Use the UBIU.',
        }),
      );
      await vi.waitFor(() =>
        expect(screen.queryByTestId('comment-dialog')).not.toBeInTheDocument(),
      );
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Comment added' }),
      );
    });

    it('adds nothing without text, and says so under the box', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      api.getInspectionSchedule.mockResolvedValue(withComment);
      await showing(bridge());
      await openInspections();

      await userEvent.click(
        within(await card()).getByTestId('structure-section-planned-comments-add'),
      );
      const dialog = await screen.findByTestId('comment-dialog');
      await userEvent.click(within(dialog).getByTestId('comment-save'));

      expect(dialog).toMatchTextContent('Comment is required.');
      expect(api.addPlannedInspectionComment).not.toHaveBeenCalled();
    });

    it('edits a comment in the dialog, opened with its text', async () => {
      authorization.canEdit = true;
      api.getInspectionSchedule.mockResolvedValue(withComment);
      await showing(bridge());
      await openInspections();

      await userEvent.click(
        within(await card()).getByTestId('structure-section-planned-comments-edit-5'),
      );
      const dialog = await screen.findByTestId('comment-dialog');
      expect(dialog).toMatchTextContent('Edit planned inspection comment');
      expect(within(dialog).getByTestId('comment-text')).toHaveValue('Bring a boat.');
      await userEvent.clear(within(dialog).getByTestId('comment-text'));
      await userEvent.type(within(dialog).getByTestId('comment-text'), 'Bring two boats.');
      await userEvent.click(within(dialog).getByTestId('comment-save'));

      await vi.waitFor(() =>
        expect(api.updateStructureComment).toHaveBeenCalledWith('7', '5', {
          comment: 'Bring two boats.',
        }),
      );
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Comment saved' }),
      );
    });

    it('shows what the server refused under the box', async () => {
      authorization.canEdit = true;
      api.getInspectionSchedule.mockResolvedValue(withComment);
      api.updateStructureComment.mockRejectedValue({
        body: { fieldErrors: { comment: 'Comment can be at most 2000 characters.' } },
      });
      await showing(bridge());
      await openInspections();

      await userEvent.click(
        within(await card()).getByTestId('structure-section-planned-comments-edit-5'),
      );
      const dialog = await screen.findByTestId('comment-dialog');
      await userEvent.click(within(dialog).getByTestId('comment-save'));

      await vi.waitFor(() =>
        expect(dialog).toMatchTextContent('Comment can be at most 2000 characters.'),
      );
      expect(display).not.toHaveBeenCalledWith(expect.objectContaining({ kind: 'error' }));
    });
  });

  describe('editing the schedule', () => {
    const scheduled = schedule({
      closeProximityRequired: true,
      closeProximityEquipment: { code: 'UBIU', description: 'Under-bridge unit' },
      nextCloseProximityDate: '2028-02-01',
      nextRoutineDate: '2027-05-01',
      routineFrequencyYears: 3,
      latestReviewedInspectionDate: '2024-06-15',
    });

    const openEdit = async () => {
      api.getInspectionSchedule.mockResolvedValue(scheduled);
      await showing(bridge());
      await openInspections();
      await userEvent.click(await screen.findByTestId('schedule-edit'));
      return screen.findByTestId('schedule-form');
    };

    it('offers Edit inspection schedule in the card header only from Level 1 up', async () => {
      api.getInspectionSchedule.mockResolvedValue(scheduled);
      await showing(bridge());
      await openInspections();
      await screen.findByTestId('structure-section-schedule');

      expect(screen.queryByTestId('schedule-edit')).not.toBeInTheDocument();
    });

    it('edits the card in place and saves all of it for Level 2 and up', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      const form = await openEdit();

      expect(screen.queryByTestId('schedule-edit')).not.toBeInTheDocument();
      await vi.waitFor(() =>
        expect(within(form).getByTestId('schedule-equipment')).toHaveValue('UBIU'),
      );
      expect(within(form).getByTestId('schedule-next-close-proximity')).toHaveValue('2028/02/01');
      expect(within(form).getByTestId('schedule-next-routine')).toHaveValue('2027/05/01');
      await userEvent.selectOptions(within(form).getByTestId('schedule-equipment'), 'BOAT');
      await userEvent.click(within(form).getByTestId('schedule-save'));

      await vi.waitFor(() =>
        expect(api.updateInspectionSchedule).toHaveBeenCalledWith('7', {
          closeProximityRequired: true,
          closeProximityEquipmentCode: 'BOAT',
          nextCloseProximityDate: '2028-02-01',
          nextRoutineDate: '2027-05-01',
          routineFrequencyYears: 3,
        }),
      );
      await vi.waitFor(() => expect(screen.queryByTestId('schedule-form')).not.toBeInTheDocument());
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Inspection schedule saved' }),
      );
    });

    it('moves the next routine date to the latest reviewed inspection plus a new frequency', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      const form = await openEdit();

      await userEvent.selectOptions(within(form).getByTestId('schedule-frequency'), '5');

      expect(within(form).getByTestId('schedule-next-routine')).toHaveValue('2029/06/15');
    });

    it('hides the close proximity fields when unticked, and keeps their values', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      const form = await openEdit();

      await userEvent.click(within(form).getByLabelText('No'));
      expect(within(form).queryByTestId('schedule-equipment')).not.toBeInTheDocument();
      await userEvent.click(within(form).getByTestId('schedule-save'));

      await vi.waitFor(() =>
        expect(api.updateInspectionSchedule).toHaveBeenCalledWith(
          '7',
          expect.objectContaining({
            closeProximityRequired: false,
            closeProximityEquipmentCode: 'UBIU',
            nextCloseProximityDate: '2028-02-01',
          }),
        ),
      );
    });

    it('lets Level 1 change only the frequency, showing the date it moves', async () => {
      authorization.canEdit = true;
      const form = await openEdit();

      expect(within(form).queryByTestId('schedule-next-routine')).not.toBeInTheDocument();
      expect(within(form).queryByLabelText('Yes')).not.toBeInTheDocument();
      await userEvent.selectOptions(within(form).getByTestId('schedule-frequency'), '2');

      expect(form).toMatchTextContent('Jun 15, 2026');
    });

    it('needs the next routine date once the structure has one, and a real date', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      const form = await openEdit();

      await userEvent.clear(within(form).getByTestId('schedule-next-routine'));
      await userEvent.click(within(form).getByTestId('schedule-save'));
      expect(form).toMatchTextContent('Next Planned Routine Inspection is required.');

      await userEvent.type(within(form).getByTestId('schedule-next-routine'), '2027/02/30');
      await userEvent.click(within(form).getByTestId('schedule-save'));
      expect(form).toMatchTextContent(
        'Next Planned Routine Inspection must be a date, as yyyy/mm/dd.',
      );
      expect(api.updateInspectionSchedule).not.toHaveBeenCalled();
    });

    it('shows what the server refused beside the field', async () => {
      authorization.canEdit = true;
      authorization.canDelete = true;
      api.updateInspectionSchedule.mockRejectedValue({
        body: {
          fieldErrors: {
            closeProximityEquipmentCode:
              'Close Proximity Special Equipment Requirements is not one of the listed requirements.',
          },
        },
      });
      const form = await openEdit();

      await userEvent.click(within(form).getByTestId('schedule-save'));

      await vi.waitFor(() =>
        expect(form).toMatchTextContent('is not one of the listed requirements.'),
      );
      expect(display).not.toHaveBeenCalledWith(expect.objectContaining({ kind: 'error' }));
    });

    it('goes back to the values, saving nothing, on Cancel', async () => {
      authorization.canEdit = true;
      const form = await openEdit();

      await userEvent.click(within(form).getByRole('button', { name: 'Cancel' }));

      expect(screen.queryByTestId('schedule-form')).not.toBeInTheDocument();
      expect(screen.getByTestId('schedule-edit')).toBeInTheDocument();
      expect(api.updateInspectionSchedule).not.toHaveBeenCalled();
    });
  });

  const inspection = (id: string, overrides: object = {}) => ({
    id,
    type: { code: 'ROUT', description: 'Routine' },
    inspectionDate: '2023-06-01',
    siteId: '62-001',
    status: { code: 'RVD', description: 'Reviewed' },
    reviewedDate: '2023-07-02',
    reviewedBy: 'Pat Engineer',
    inspectorName: 'Sam Inspector',
    viewable: true,
    ...overrides,
  });

  it('loads nothing until the tab is opened, then its first page of ten', async () => {
    await showing(bridge());

    expect(api.getInspectionSchedule).not.toHaveBeenCalled();
    expect(api.getStructureInspections).not.toHaveBeenCalled();

    await openInspections();

    await vi.waitFor(() => {
      expect(api.getInspectionSchedule).toHaveBeenCalledWith('7');
      expect(api.getStructureInspections).toHaveBeenCalledWith('7', 0, 10, false);
    });
  });

  it('shows the schedule, the close proximity fields only when one is required', async () => {
    api.getInspectionSchedule.mockResolvedValue(
      schedule({
        closeProximityRequired: true,
        closeProximityEquipment: { code: 'UBIU', description: 'Under-bridge inspection unit' },
        nextCloseProximityDate: '2027-05-01',
        nextRoutineDate: '2026-09-01',
        routineFrequencyYears: 3,
      }),
    );
    await showing(bridge());

    await openInspections();
    const card = await screen.findByTestId('structure-section-schedule');

    expect(valueOf('Close Proximity Inspection Required?', card)).toBe('Yes');
    expect(valueOf('Close Proximity Special Equipment Requirements', card)).toBe(
      'Under-bridge inspection unit',
    );
    expect(valueOf('Next Planned Close Proximity Inspection', card)).toBe('May 1, 2027');
    expect(valueOf('Next Planned Routine Inspection', card)).toBe('Sep 1, 2026');
    expect(valueOf('Routine Inspection Frequency (years)', card)).toBe('3');
  });

  it('hides the close proximity fields when none is required, as legacy does', async () => {
    await showing(bridge());

    await openInspections();
    const card = await screen.findByTestId('structure-section-schedule');

    expect(
      within(card).queryByText('Close Proximity Special Equipment Requirements'),
    ).not.toBeInTheDocument();
  });

  it('lists the planned-inspection comments and the completed close proximity inspections', async () => {
    api.getInspectionSchedule.mockResolvedValue(
      schedule({
        plannedInspectionComments: [
          { id: '1', text: 'Bring a boat.', userId: 'IDIR\\B', timestamp: '2024-01-01T09:00:00' },
        ],
        completedCloseProximity: [{ id: '5', completed: '2024-06-01', userId: 'IDIR\\P' }],
      }),
    );
    await showing(bridge());

    await openInspections();

    expect(await screen.findByTestId('structure-section-planned-comments')).toMatchTextContent(
      'Bring a boat.',
    );
    // Last of the tab's cards.
    const cards = Array.from(
      screen.getByTestId('structure-inspections-tab').querySelectorAll('.structure-detail__card'),
    );
    expect(cards.at(-1)).toBe(screen.getByTestId('structure-section-planned-comments'));
    expect(screen.getByTestId('structure-section-close-proximity')).toMatchTextContent(
      'Jun 1, 2024',
    );
  });

  it('lists each inspection, linked to its page in a new tab', async () => {
    api.getStructureInspections.mockResolvedValue(inspectionsPage([inspection('41')]));
    await showing(bridge());

    await openInspections();
    const row = await screen.findByTestId('structure-inspection-41');

    expect(row).toMatchTextContent('Routine');
    expect(row).toMatchTextContent('Reviewed');
    expect(row).toMatchTextContent('Jul 2, 2023');
    expect(row).toMatchTextContent('Pat Engineer');
    expect(row).toMatchTextContent('Sam Inspector');
    const link = within(row).getByRole('link', { name: 'Jun 1, 2023 (opens in a new tab)' });
    expect(link).toHaveAttribute('href', '/inspection/41');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('links an inspection not dated yet as "No date yet"', async () => {
    api.getStructureInspections.mockResolvedValue(
      inspectionsPage([
        inspection('42', {
          inspectionDate: null,
          status: { code: 'PRO', description: 'In Progress' },
        }),
      ]),
    );
    await showing(bridge());

    await openInspections();
    const row = await screen.findByTestId('structure-inspection-42');

    expect(
      within(row).getByRole('link', { name: 'No date yet (opens in a new tab)' }),
    ).toHaveAttribute('href', '/inspection/42');
  });

  it('offers no link to an inspection still out on the offline client', async () => {
    api.getStructureInspections.mockResolvedValue(
      inspectionsPage([
        inspection('41', { viewable: false, status: { code: 'OFL', description: 'Offline' } }),
      ]),
    );
    await showing(bridge());

    await openInspections();
    const row = await screen.findByTestId('structure-inspection-41');

    expect(within(row).queryByRole('link', { name: /Jun 1, 2023/ })).not.toBeInTheDocument();
  });

  it('asks the server for the next page', async () => {
    api.getStructureInspections.mockResolvedValue(
      inspectionsPage(
        Array.from({ length: 10 }, (_, index) => inspection(String(index + 1))),
        { page: { content: [], totalElements: 12, totalPages: 2, pageNumber: 0, pageSize: 10 } },
      ),
    );
    await showing(bridge());

    await openInspections();
    await screen.findByTestId('structure-section-inspections');
    await userEvent.click(await screen.findByRole('button', { name: 'Next page' }));

    await vi.waitFor(() =>
      expect(api.getStructureInspections).toHaveBeenLastCalledWith('7', 1, 10, false),
    );
  });

  it('asks for the inspections from before the superstructure went in when ticked', async () => {
    api.getStructureInspections.mockResolvedValue(
      inspectionsPage([inspection('41')], { beforeInstallCount: 2 }),
    );
    await showing(bridge());

    await openInspections();
    await userEvent.click(
      await screen.findByText('Show inspections from before the superstructure was installed (2)'),
    );

    await vi.waitFor(() =>
      expect(api.getStructureInspections).toHaveBeenLastCalledWith('7', 0, 10, true),
    );
  });

  it("says so when there are none, in legacy's words", async () => {
    await showing(bridge());

    await openInspections();

    expect(await screen.findByTestId('structure-section-inspections')).toMatchTextContent(
      'There have been no inspections for this structure.',
    );
  });

  it('says so when the inspections cannot be loaded', async () => {
    api.getStructureInspections.mockRejectedValue(new Error('boom'));
    await showing(bridge());

    await openInspections();

    expect(await screen.findByTestId('structure-inspections-error')).toMatchTextContent(
      'Inspections could not be loaded',
    );
  });
});

describe('StructureDetailPage — Repairs', () => {
  const openRepairs = () => userEvent.click(screen.getByTestId('structure-tab-repairs'));

  const repair = (id: string, overrides: object = {}) => ({
    id,
    number: Number(id),
    status: { code: 'REQ', description: 'Required' },
    type: { code: 'DECK', description: 'Deck planks' },
    suggested: { userId: 'IDIR\\A', date: '2023-06-02' },
    required: { userId: 'IDIR\\B', date: '2023-06-05' },
    completed: null,
    inspectionId: '41',
    inspectionDate: '2023-06-01',
    priority: { code: 'P1', description: 'Urgent' },
    completedDate: null,
    estimate: 4000,
    actualCost: null,
    quantity: 12,
    unit: 'm2',
    description: 'Replace worn planks.',
    ...overrides,
  });

  it('loads the outstanding repairs, ten to a page, once the tab is opened', async () => {
    await showing(bridge());

    expect(api.getStructureRepairs).not.toHaveBeenCalled();

    await openRepairs();

    await vi.waitFor(() =>
      expect(api.getStructureRepairs).toHaveBeenCalledWith('7', 'OUTSTANDING', 0, 10, false),
    );
  });

  it('lists each repair, its user audits written out in the cell', async () => {
    api.getStructureRepairs.mockResolvedValue(repairsPage([repair('3')]));
    await showing(bridge());

    await openRepairs();
    const row = await screen.findByTestId('structure-repair-3');

    expect(row).toMatchTextContent('Required');
    expect(row).toMatchTextContent('Deck planks');
    expect(row).toMatchTextContent('Suggested: IDIR\\A, Jun 2, 2023');
    expect(row).toMatchTextContent('Required: IDIR\\B, Jun 5, 2023');
    expect(row).not.toMatchTextContent('Completed:');
    expect(row).toMatchTextContent('Urgent');
    expect(row).toMatchTextContent('$4,000');
    expect(row).toMatchTextContent('12');
    // The number only, as legacy's column; the unit is in the dialog.
    expect(row).not.toMatchTextContent('m2');
    expect(row).toMatchTextContent('Replace worn planks.');
    expect(
      within(row).getByRole('link', { name: 'Jun 1, 2023 (opens in a new tab)' }),
    ).toHaveAttribute('href', '/inspection/41');
  });

  it('asks for every repair when All Items is chosen, back on the first page', async () => {
    await showing(bridge());

    await openRepairs();
    await userEvent.click(
      within(await screen.findByTestId('structure-section-repairs')).getByText('All Items'),
    );

    await vi.waitFor(() =>
      expect(api.getStructureRepairs).toHaveBeenLastCalledWith('7', 'ALL', 0, 10, false),
    );
  });

  it('asks the server for the next page', async () => {
    api.getStructureRepairs.mockResolvedValue(
      repairsPage(
        Array.from({ length: 10 }, (_, index) => repair(String(index + 1))),
        12,
      ),
    );
    await showing(bridge());

    await openRepairs();
    await userEvent.click(await screen.findByRole('button', { name: 'Next page' }));

    await vi.waitFor(() =>
      expect(api.getStructureRepairs).toHaveBeenLastCalledWith('7', 'OUTSTANDING', 1, 10, false),
    );
  });

  it('asks for the repairs from before the superstructure went in when ticked', async () => {
    api.getStructureRepairs.mockResolvedValue(repairsPage([], 0, 3));
    await showing(bridge());

    await openRepairs();
    await userEvent.click(
      await screen.findByText('Show repairs from before the superstructure was installed (3)'),
    );

    await vi.waitFor(() =>
      expect(api.getStructureRepairs).toHaveBeenLastCalledWith('7', 'OUTSTANDING', 0, 10, true),
    );
  });

  it('offers no such box when nothing predates the superstructure', async () => {
    await showing(bridge());

    await openRepairs();
    await screen.findByTestId('structure-section-repairs');

    expect(
      screen.queryByText(/Show repairs from before the superstructure was installed/),
    ).not.toBeInTheDocument();
  });

  it('offers Delete in an Actions column only to a user who may delete', async () => {
    api.getStructureRepairs.mockResolvedValue(repairsPage([repair('3')]));
    await showing(bridge());

    await openRepairs();
    const section = await screen.findByTestId('structure-section-repairs');
    await within(section).findByTestId('structure-repair-3');

    expect(within(section).queryByText('Actions')).not.toBeInTheDocument();
    expect(screen.queryByTestId('structure-repair-delete-3')).not.toBeInTheDocument();
  });

  it('deletes a repair once confirmed, and says so', async () => {
    authorization.canDelete = true;
    api.getStructureRepairs.mockResolvedValue(repairsPage([repair('3')]));
    await showing(bridge());

    await openRepairs();
    const button = await screen.findByTestId('structure-repair-delete-3');
    expect(screen.getByRole('button', { name: 'Delete repair 3' })).toBe(button);
    await userEvent.click(button);
    expect(api.deleteStructureRepair).not.toHaveBeenCalled();
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    await vi.waitFor(() => expect(api.deleteStructureRepair).toHaveBeenCalledWith('7', '3'));
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Repair 3 deleted' }),
      ),
    );
  });

  it('deletes nothing when the confirmation is cancelled', async () => {
    authorization.canDelete = true;
    api.getStructureRepairs.mockResolvedValue(repairsPage([repair('3')]));
    await showing(bridge());

    await openRepairs();
    await userEvent.click(await screen.findByTestId('structure-repair-delete-3'));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }));

    expect(api.deleteStructureRepair).not.toHaveBeenCalled();
  });

  it('says so when the delete fails', async () => {
    authorization.canDelete = true;
    api.getStructureRepairs.mockResolvedValue(repairsPage([repair('3')]));
    api.deleteStructureRepair.mockRejectedValue(new Error('boom'));
    await showing(bridge());

    await openRepairs();
    await userEvent.click(await screen.findByTestId('structure-repair-delete-3'));
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The repair was not deleted' }),
      ),
    );
  });

  it('says when there are none in the view chosen', async () => {
    await showing(bridge());

    await openRepairs();

    expect(await screen.findByTestId('structure-section-repairs')).toMatchTextContent(
      'No outstanding repairs.',
    );
  });

  it('says so when the repairs cannot be loaded', async () => {
    api.getStructureRepairs.mockRejectedValue(new Error('boom'));
    await showing(bridge());

    await openRepairs();

    expect(await screen.findByTestId('structure-repairs-error')).toMatchTextContent(
      'Repairs could not be loaded',
    );
  });
  describe('Add', () => {
    it('offers Add repair over the table only from Level 1 up', async () => {
      await showing(bridge());

      await openRepairs();
      await screen.findByTestId('structure-section-repairs');

      expect(screen.queryByTestId('structure-repair-add')).not.toBeInTheDocument();
    });

    it('adds a repair as Suggested, and says which number it took', async () => {
      authorization.canEdit = true;
      await showing(bridge());
      await openRepairs();

      await userEvent.click(await screen.findByTestId('structure-repair-add'));
      const dialog = await screen.findByTestId('repair-dialog');
      await vi.waitFor(() =>
        expect(within(dialog).getByTestId('repair-type').querySelectorAll('option')).toHaveLength(
          4,
        ),
      );
      expect(dialog).toMatchTextContent('Add repair');
      // No number before saving, a locked status, and nothing only a completed repair has.
      expect(within(dialog).queryByText('Repair Number')).not.toBeInTheDocument();
      expect(dialog).toMatchTextContent('Suggested');
      expect(within(dialog).queryByTestId('repair-status')).not.toBeInTheDocument();
      expect(within(dialog).queryByTestId('repair-completed-date')).not.toBeInTheDocument();
      expect(within(dialog).queryByTestId('repair-actualCost')).not.toBeInTheDocument();

      await userEvent.selectOptions(within(dialog).getByTestId('repair-priority'), 'H');
      await userEvent.selectOptions(within(dialog).getByTestId('repair-type'), 'DECK');
      await userEvent.type(within(dialog).getByTestId('repair-estimate'), '2500');
      await userEvent.type(within(dialog).getByTestId('repair-quantity'), '8');
      await userEvent.type(within(dialog).getByTestId('repair-description'), 'New planks.');
      await userEvent.click(within(dialog).getByTestId('repair-save'));

      await vi.waitFor(() =>
        expect(api.createStructureRepair).toHaveBeenCalledWith('7', {
          priorityCode: 'H',
          estimate: 2500,
          typeCode: 'DECK',
          quantity: 8,
          description: 'New planks.',
        }),
      );
      await vi.waitFor(() =>
        expect(display).toHaveBeenCalledWith(
          expect.objectContaining({ kind: 'success', title: 'Repair 4 added' }),
        ),
      );
      expect(screen.queryByTestId('repair-dialog')).not.toBeInTheDocument();
    });

    it('adds nothing without a priority and a type, and says so beside each', async () => {
      authorization.canEdit = true;
      await showing(bridge());
      await openRepairs();

      await userEvent.click(await screen.findByTestId('structure-repair-add'));
      const dialog = await screen.findByTestId('repair-dialog');
      await userEvent.click(within(dialog).getByTestId('repair-save'));

      expect(dialog).toMatchTextContent('Repair Priority is required.');
      expect(dialog).toMatchTextContent('Repair Type is required.');
      expect(api.createStructureRepair).not.toHaveBeenCalled();
    });

    it('says so when the add fails for no field', async () => {
      authorization.canEdit = true;
      api.createStructureRepair.mockRejectedValue(new Error('boom'));
      await showing(bridge());
      await openRepairs();

      await userEvent.click(await screen.findByTestId('structure-repair-add'));
      const dialog = await screen.findByTestId('repair-dialog');
      await vi.waitFor(() =>
        expect(
          within(dialog).getByTestId('repair-priority').querySelectorAll('option'),
        ).toHaveLength(3),
      );
      await userEvent.selectOptions(within(dialog).getByTestId('repair-priority'), 'H');
      await vi.waitFor(() =>
        expect(within(dialog).getByTestId('repair-type').querySelectorAll('option')).toHaveLength(
          4,
        ),
      );
      await userEvent.selectOptions(within(dialog).getByTestId('repair-type'), 'DECK');
      await userEvent.click(within(dialog).getByTestId('repair-save'));

      await vi.waitFor(() =>
        expect(display).toHaveBeenCalledWith(
          expect.objectContaining({ kind: 'error', title: 'The repair was not added' }),
        ),
      );
      expect(screen.getByTestId('repair-dialog')).toBeInTheDocument();
    });
  });

  describe('Edit', () => {
    const openEdit = async (overrides: object = {}) => {
      authorization.canEdit = true;
      api.getStructureRepairs.mockResolvedValue(repairsPage([repair('3', overrides)]));
      await showing(bridge());
      await openRepairs();
      await userEvent.click(await screen.findByTestId('structure-repair-edit-3'));
      const dialog = await screen.findByTestId('repair-dialog');
      // Wait for the lists, which the dialog fetches as it opens.
      await vi.waitFor(() => expect(within(dialog).getByTestId('repair-type')).toHaveValue('DECK'));
      await vi.waitFor(() =>
        expect(within(dialog).getByTestId('repair-priority')).toHaveValue('P1'),
      );
      return dialog;
    };

    it('offers Edit, in the Actions column, from Level 1 up', async () => {
      authorization.canEdit = true;
      api.getStructureRepairs.mockResolvedValue(repairsPage([repair('3')]));
      await showing(bridge());

      await openRepairs();
      await screen.findByTestId('structure-repair-3');

      expect(screen.getByRole('button', { name: 'Edit repair 3' })).toBe(
        screen.getByTestId('structure-repair-edit-3'),
      );
      expect(screen.queryByTestId('structure-repair-delete-3')).not.toBeInTheDocument();
    });

    it('opens the repair with its number shown and not edited, and saves the edit', async () => {
      authorization.isInspectionReviewer = true;
      const dialog = await openEdit();

      expect(dialog).toMatchTextContent('Edit repair 3');
      expect(within(dialog).getByTestId('repair-status')).toHaveValue('REQ');
      expect(within(dialog).getByTestId('repair-estimate')).toHaveValue('4000');
      expect(within(dialog).getByTestId('repair-quantity')).toHaveValue('12');
      expect(within(dialog).getByTestId('repair-unit')).toMatchTextContent('m²');
      await userEvent.selectOptions(within(dialog).getByTestId('repair-priority'), 'H');
      await userEvent.clear(within(dialog).getByTestId('repair-estimate'));
      await userEvent.clear(within(dialog).getByTestId('repair-description'));
      await userEvent.type(within(dialog).getByTestId('repair-description'), 'Replace all.');
      await userEvent.click(within(dialog).getByTestId('repair-save'));

      await vi.waitFor(() =>
        expect(api.updateStructureRepair).toHaveBeenCalledWith('7', '3', {
          statusCode: 'REQ',
          priorityCode: 'H',
          completedDate: null,
          estimate: null,
          actualCost: null,
          typeCode: 'DECK',
          quantity: 12,
          description: 'Replace all.',
        }),
      );
      await vi.waitFor(() => expect(screen.queryByTestId('repair-dialog')).not.toBeInTheDocument());
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Repair 3 saved' }),
      );
    });

    it('offers Required and Not Required to an inspection reviewer only, as legacy does', async () => {
      // A P.Eng is not, by that alone.
      authorization.isPeng = true;
      const dialog = await openEdit();
      const offered = () =>
        Array.from(
          (within(dialog).getByTestId('repair-status') as HTMLSelectElement).options,
          (option) => option.value,
        );

      // The stored Required is not this user's to keep: the status opens unchosen.
      expect(within(dialog).getByTestId('repair-status')).toHaveValue('');
      expect(offered()).toEqual(['', 'CF', 'COM', 'SUG']);
      await userEvent.click(within(dialog).getByTestId('repair-save'));
      expect(dialog).toMatchTextContent('Repair Status is required.');
      expect(api.updateStructureRepair).not.toHaveBeenCalled();
    });

    it('opens the completed date and actual cost only for Completed, and needs the date', async () => {
      const dialog = await openEdit({ status: { code: 'SUG', description: 'Suggested' } });

      expect(within(dialog).getByTestId('repair-completed-date')).toBeDisabled();
      expect(within(dialog).getByTestId('repair-actualCost')).toBeDisabled();
      await userEvent.selectOptions(within(dialog).getByTestId('repair-status'), 'COM');
      expect(within(dialog).getByTestId('repair-actualCost')).toBeEnabled();
      await userEvent.type(within(dialog).getByTestId('repair-actualCost'), '3800');
      await userEvent.click(within(dialog).getByTestId('repair-save'));

      expect(dialog).toMatchTextContent(
        'Repair Completed Date is required when the status is Completed.',
      );
      expect(api.updateStructureRepair).not.toHaveBeenCalled();

      await userEvent.type(within(dialog).getByTestId('repair-completed-date'), '2026/09/01');
      await userEvent.click(within(dialog).getByTestId('repair-save'));

      await vi.waitFor(() =>
        expect(api.updateStructureRepair).toHaveBeenCalledWith(
          '7',
          '3',
          expect.objectContaining({
            statusCode: 'COM',
            completedDate: '2026-09-01',
            actualCost: 3800,
          }),
        ),
      );
    });

    it('clears the completed date and actual cost when the status moves off Completed', async () => {
      const dialog = await openEdit({
        status: { code: 'COM', description: 'Completed' },
        completedDate: '2026-09-01',
        actualCost: 3800,
      });

      expect(within(dialog).getByTestId('repair-completed-date')).toHaveValue('2026/09/01');
      await userEvent.selectOptions(within(dialog).getByTestId('repair-status'), 'SUG');

      expect(within(dialog).getByTestId('repair-completed-date')).toHaveValue('');
      expect(within(dialog).getByTestId('repair-actualCost')).toHaveValue('');
    });

    it('narrows the types to the ticked groups, each type once, every type when none', async () => {
      const dialog = await openEdit();
      const offered = () =>
        Array.from(
          (within(dialog).getByTestId('repair-type') as HTMLSelectElement).options,
          (option) => option.value,
        );

      expect(offered()).toEqual(['', 'RAIL', 'DECK', '800A']);
      await userEvent.click(within(dialog).getByLabelText('Superstructure'));
      expect(offered()).toEqual(['', 'DECK', 'RAIL']);
      expect(within(dialog).getByTestId('repair-type')).toHaveValue('DECK');

      // A type the ticked groups do not hold is cleared, as legacy's list replaced it.
      await userEvent.click(within(dialog).getByLabelText('Superstructure'));
      await userEvent.click(within(dialog).getByLabelText('Approach'));
      expect(offered()).toEqual(['', 'RAIL']);
      expect(within(dialog).getByTestId('repair-type')).toHaveValue('');
    });

    it('needs a description for the type 800A, and whole amounts', async () => {
      const dialog = await openEdit({ description: null });

      await userEvent.selectOptions(within(dialog).getByTestId('repair-type'), '800A');
      await userEvent.clear(within(dialog).getByTestId('repair-quantity'));
      await userEvent.type(within(dialog).getByTestId('repair-quantity'), '1.5');
      await userEvent.click(within(dialog).getByTestId('repair-save'));

      expect(dialog).toMatchTextContent('Repair Description is required for this Repair Type.');
      expect(dialog).toMatchTextContent('Qty must be a whole number from 0 to 999,999.');
      expect(api.updateStructureRepair).not.toHaveBeenCalled();
    });

    it('shows what the server refused beside the field', async () => {
      authorization.isInspectionReviewer = true;
      api.updateStructureRepair.mockRejectedValue({
        body: { fieldErrors: { typeCode: 'Repair Type is not one of the listed types.' } },
      });
      const dialog = await openEdit();

      await userEvent.click(within(dialog).getByTestId('repair-save'));

      await vi.waitFor(() =>
        expect(dialog).toMatchTextContent('Repair Type is not one of the listed types.'),
      );
      expect(display).not.toHaveBeenCalledWith(expect.objectContaining({ kind: 'error' }));
    });

    it('saves nothing when cancelled', async () => {
      const dialog = await openEdit();

      await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

      expect(screen.queryByTestId('repair-dialog')).not.toBeInTheDocument();
      expect(api.updateStructureRepair).not.toHaveBeenCalled();
    });
  });
});

describe('StructureDetailPage — Monitoring', () => {
  const openMonitoring = () => userEvent.click(screen.getByTestId('structure-tab-monitoring'));

  const monitor = (id: string, overrides: object = {}) => ({
    id,
    number: Number(id),
    status: { code: 'REQ', description: 'Required' },
    suggested: null,
    required: { userId: 'IDIR\\B', date: '2023-06-05' },
    completed: null,
    inspectionId: '41',
    inspectionDate: '2023-06-01',
    description: 'Watch the scour at the south abutment.',
    frequency: { code: 'ANN', description: 'Annually' },
    frequencyComment: 'After freshet.',
    ...overrides,
  });

  it('loads the outstanding items, ten to a page, once the tab is opened', async () => {
    await showing(bridge());

    expect(api.getStructureMonitors).not.toHaveBeenCalled();

    await openMonitoring();

    await vi.waitFor(() =>
      expect(api.getStructureMonitors).toHaveBeenCalledWith('7', 'OUTSTANDING', 0, 10, false),
    );
  });

  it('lists each item, its user audits written out and its frequency with its comment', async () => {
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2')]));
    await showing(bridge());

    await openMonitoring();
    const row = await screen.findByTestId('structure-monitor-2');

    expect(row).toMatchTextContent('Required');
    expect(row).toMatchTextContent('Required: IDIR\\B, Jun 5, 2023');
    expect(row).not.toMatchTextContent('Suggested:');
    expect(row).toMatchTextContent('Watch the scour at the south abutment.');
    expect(row).toMatchTextContent('Annually — After freshet.');
    expect(
      within(row).getByRole('link', { name: 'Jun 1, 2023 (opens in a new tab)' }),
    ).toHaveAttribute('href', '/inspection/41');
  });

  it('asks for every item when All Items is chosen, without changing Repairs', async () => {
    await showing(bridge());

    await openMonitoring();
    await userEvent.click(
      within(await screen.findByTestId('structure-section-monitoring')).getByText('All Items'),
    );

    await vi.waitFor(() =>
      expect(api.getStructureMonitors).toHaveBeenLastCalledWith('7', 'ALL', 0, 10, false),
    );
    expect(api.getStructureRepairs).not.toHaveBeenCalled();
  });

  it('asks for the monitoring items from before the superstructure went in when ticked', async () => {
    api.getStructureMonitors.mockResolvedValue(repairsPage([], 0, 3));
    await showing(bridge());

    await openMonitoring();
    await userEvent.click(
      await screen.findByText(
        'Show monitoring items from before the superstructure was installed (3)',
      ),
    );

    await vi.waitFor(() =>
      expect(api.getStructureMonitors).toHaveBeenLastCalledWith('7', 'OUTSTANDING', 0, 10, true),
    );
  });

  it('offers no such box when nothing predates the superstructure', async () => {
    await showing(bridge());

    await openMonitoring();
    await screen.findByTestId('structure-section-monitoring');

    expect(
      screen.queryByText(/Show monitoring items from before the superstructure was installed/),
    ).not.toBeInTheDocument();
  });

  it('offers Delete in an Actions column only to a user who may delete', async () => {
    // Legacy's delete icon sits behind /deleteStructureMonitor; an action that cannot be
    // performed is not shown, nor is an empty column.
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2')]));
    await showing(bridge());

    await openMonitoring();
    await screen.findByTestId('structure-monitor-2');

    expect(screen.queryByText('Actions')).not.toBeInTheDocument();
    expect(screen.queryByTestId('structure-monitor-delete-2')).not.toBeInTheDocument();
  });

  it('deletes an item once confirmed, and says so', async () => {
    authorization.canDelete = true;
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2')]));
    await showing(bridge());

    await openMonitoring();
    const section = await screen.findByTestId('structure-section-monitoring');
    expect(within(section).getByRole('columnheader', { name: 'Actions' })).toBeInTheDocument();
    // "Delete" beside the icon, naming what it deletes to a screen reader.
    expect(screen.getByRole('button', { name: 'Delete monitoring item 2' })).toBe(
      screen.getByTestId('structure-monitor-delete-2'),
    );
    await userEvent.click(screen.getByTestId('structure-monitor-delete-2'));
    expect(api.deleteStructureMonitor).not.toHaveBeenCalled();
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    await vi.waitFor(() => expect(api.deleteStructureMonitor).toHaveBeenCalledWith('7', '2'));
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Monitoring item 2 deleted' }),
      ),
    );
  });

  it('deletes nothing when the confirmation is cancelled', async () => {
    authorization.canDelete = true;
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2')]));
    await showing(bridge());

    await openMonitoring();
    await userEvent.click(await screen.findByTestId('structure-monitor-delete-2'));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }));

    expect(api.deleteStructureMonitor).not.toHaveBeenCalled();
  });

  it('says so when the delete fails', async () => {
    authorization.canDelete = true;
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2')]));
    api.deleteStructureMonitor.mockRejectedValue(new Error('boom'));
    await showing(bridge());

    await openMonitoring();
    await userEvent.click(await screen.findByTestId('structure-monitor-delete-2'));
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The monitoring item was not deleted' }),
      ),
    );
  });

  /** Opens the edit dialog for item 2, as a Level 1 user who is an inspection reviewer. */
  const openEdit = async (overrides: object = {}) => {
    authorization.canEdit = true;
    authorization.isInspectionReviewer = true;
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2', overrides)]));
    await showing(bridge());
    await openMonitoring();
    await userEvent.click(await screen.findByTestId('structure-monitor-edit-2'));
    const dialog = await screen.findByTestId('monitor-dialog');
    // Wait for the dropdowns' lists, which the dialog fetches as it opens.
    await vi.waitFor(() => expect(within(dialog).getByTestId('monitor-status')).toHaveValue('REQ'));
    return dialog;
  };

  it('offers Edit, in the Actions column, from Level 1 up', async () => {
    authorization.canEdit = true;
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2')]));
    await showing(bridge());

    await openMonitoring();
    await screen.findByTestId('structure-monitor-2');

    expect(screen.getByRole('button', { name: 'Edit monitoring item 2' })).toBe(
      screen.getByTestId('structure-monitor-edit-2'),
    );
    expect(screen.queryByTestId('structure-monitor-delete-2')).not.toBeInTheDocument();
  });

  it('offers Required and Not Required to an inspection reviewer only, as legacy does', async () => {
    authorization.canEdit = true;
    authorization.isPeng = true;
    api.getStructureMonitors.mockResolvedValue(repairsPage([monitor('2')]));
    await showing(bridge());
    await openMonitoring();
    await userEvent.click(await screen.findByTestId('structure-monitor-edit-2'));
    const dialog = await screen.findByTestId('monitor-dialog');
    const status = within(dialog).getByTestId('monitor-status') as HTMLSelectElement;
    await vi.waitFor(() => expect(status.options).toHaveLength(3));

    // The stored Required is not this user's to keep: the status opens unchosen.
    expect(status).toHaveValue('');
    expect(Array.from(status.options, (option) => option.value)).toEqual(['', 'COM', 'SUG']);
    await userEvent.click(within(dialog).getByTestId('monitor-save'));
    expect(dialog).toMatchTextContent('Monitoring Status is required.');
    expect(api.updateStructureMonitor).not.toHaveBeenCalled();
  });

  it('opens the item in a dialog, its number shown and not edited, and saves the edit', async () => {
    const dialog = await openEdit();

    expect(dialog).toMatchTextContent('Edit monitoring item 2');
    expect(within(dialog).getByTestId('monitor-frequency')).toHaveValue('ANN');
    expect(within(dialog).getByTestId('monitor-description')).toHaveValue(
      'Watch the scour at the south abutment.',
    );
    await userEvent.selectOptions(within(dialog).getByTestId('monitor-status'), 'COM');
    await userEvent.clear(within(dialog).getByTestId('monitor-description'));
    await userEvent.type(within(dialog).getByTestId('monitor-description'), 'Scour gone.');
    await userEvent.click(within(dialog).getByTestId('monitor-save'));

    await vi.waitFor(() =>
      expect(api.updateStructureMonitor).toHaveBeenCalledWith('7', '2', {
        statusCode: 'COM',
        frequencyCode: 'ANN',
        frequencyComment: '',
        description: 'Scour gone.',
      }),
    );
    await vi.waitFor(() => expect(screen.queryByTestId('monitor-dialog')).not.toBeInTheDocument());
    expect(display).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'success', title: 'Monitoring item 2 saved' }),
    );
  });

  it('asks for the comment when the frequency is Other, beside the box', async () => {
    const dialog = await openEdit({ frequencyComment: null });

    await userEvent.selectOptions(within(dialog).getByTestId('monitor-frequency'), 'OTH');
    await userEvent.click(within(dialog).getByTestId('monitor-save'));

    expect(dialog).toMatchTextContent(
      'Monitor Freq. Comment is required when the frequency is Other.',
    );
    expect(api.updateStructureMonitor).not.toHaveBeenCalled();
  });

  it('shows what the server refused beside the field', async () => {
    api.updateStructureMonitor.mockRejectedValue({
      body: { fieldErrors: { description: 'Monitor Description can be at most 2000 characters.' } },
    });
    const dialog = await openEdit();

    await userEvent.click(within(dialog).getByTestId('monitor-save'));

    await vi.waitFor(() =>
      expect(dialog).toMatchTextContent('Monitor Description can be at most 2000 characters.'),
    );
    expect(display).not.toHaveBeenCalledWith(expect.objectContaining({ kind: 'error' }));
  });

  it('offers Add monitoring item over the table only from Level 1 up', async () => {
    await showing(bridge());

    await openMonitoring();
    await screen.findByTestId('structure-section-monitoring');

    expect(screen.queryByTestId('structure-monitor-add')).not.toBeInTheDocument();
  });

  it('adds an item as Suggested, at the default frequency, and says which number it took', async () => {
    authorization.canEdit = true;
    await showing(bridge());
    await openMonitoring();

    await userEvent.click(await screen.findByTestId('structure-monitor-add'));
    const dialog = await screen.findByTestId('monitor-dialog');
    // Legacy's default frequency, once the list shows it; the status is locked.
    await vi.waitFor(() =>
      expect(within(dialog).getByTestId('monitor-frequency')).toHaveValue('INS'),
    );
    expect(dialog).toMatchTextContent('Add monitoring item');
    // No number before saving: the server gives the next, and the toast names it.
    expect(within(dialog).queryByText('Monitor Number')).not.toBeInTheDocument();
    expect(dialog).toMatchTextContent('Suggested');
    expect(within(dialog).queryByTestId('monitor-status')).not.toBeInTheDocument();
    await userEvent.type(within(dialog).getByTestId('monitor-description'), 'New scour.');
    await userEvent.click(within(dialog).getByTestId('monitor-save'));

    await vi.waitFor(() =>
      expect(api.createStructureMonitor).toHaveBeenCalledWith('7', {
        frequencyCode: 'INS',
        frequencyComment: '',
        description: 'New scour.',
      }),
    );
    await vi.waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Monitoring item 3 added' }),
      ),
    );
    expect(screen.queryByTestId('monitor-dialog')).not.toBeInTheDocument();
  });

  it('adds nothing without a description, and says so beside the box', async () => {
    authorization.canEdit = true;
    await showing(bridge());
    await openMonitoring();

    await userEvent.click(await screen.findByTestId('structure-monitor-add'));
    const dialog = await screen.findByTestId('monitor-dialog');
    await userEvent.click(within(dialog).getByTestId('monitor-save'));

    expect(dialog).toMatchTextContent('Monitor Description is required.');
    expect(api.createStructureMonitor).not.toHaveBeenCalled();
  });

  it('saves nothing when cancelled', async () => {
    const dialog = await openEdit();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByTestId('monitor-dialog')).not.toBeInTheDocument();
    expect(api.updateStructureMonitor).not.toHaveBeenCalled();
  });

  it('says when there are none in the view chosen', async () => {
    await showing(bridge());

    await openMonitoring();

    expect(await screen.findByTestId('structure-section-monitoring')).toMatchTextContent(
      'No outstanding monitoring items.',
    );
  });

  it('says so when the items cannot be loaded', async () => {
    api.getStructureMonitors.mockRejectedValue(new Error('boom'));
    await showing(bridge());

    await openMonitoring();

    expect(await screen.findByTestId('structure-monitors-error')).toMatchTextContent(
      'Monitoring items could not be loaded',
    );
  });
});
