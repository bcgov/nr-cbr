import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { userEvent } from 'vitest/browser';

import StructureDetailPage from './index';

import type { StructureDetailResponse } from './structureResponse';

const authorization = vi.hoisted(() => ({ canEdit: false, canDelete: false }));
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
  getDocuments: vi.fn(),
  getDocumentFile: vi.fn(),
  getInspectionSchedule: vi.fn(),
  getStructureInspections: vi.fn(),
  getStructureRepairs: vi.fn(),
  getStructureMonitors: vi.fn(),
  deleteStructureMonitor: vi.fn(),
}));
vi.mock('@/services/APIs', () => ({ default: { structureSearch: api } }));

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
  api.getDocuments.mockReset();
  api.getDocuments.mockResolvedValue({ documents: [] });
  api.getDocumentFile.mockReset();
  api.getInspectionSchedule.mockReset();
  api.getInspectionSchedule.mockResolvedValue(schedule());
  api.getStructureInspections.mockReset();
  api.getStructureInspections.mockResolvedValue(inspectionsPage([]));
  api.getStructureRepairs.mockReset();
  api.getStructureRepairs.mockResolvedValue(repairsPage([]));
  api.getStructureMonitors.mockReset();
  api.getStructureMonitors.mockResolvedValue(repairsPage([]));
  api.deleteStructureMonitor.mockReset();
  api.deleteStructureMonitor.mockResolvedValue(undefined);
  authorization.canDelete = false;
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

  it('stays marked under construction until its other tabs exist', async () => {
    await showing(bridge());

    expect(screen.getByText('Under construction')).toBeInTheDocument();
  });
});

describe('StructureDetailPage — header', () => {
  it('titles the page with the structure number', async () => {
    await showing(bridge());

    expect(screen.getByRole('heading', { name: 'Structure B100' })).toBeInTheDocument();
  });

  it('shows the structure and its site, every code decoded', async () => {
    const header = await showing(bridge());

    expect(valueOf('Type/Class', header)).toBe('Timber Bridge');
    expect(within(header).queryByText('Site Status')).not.toBeInTheDocument();
    // Legacy shows the raw code here.
    expect(valueOf('Inspection Status', header)).toBe('Inspected');
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

  it('shows the site status as a pill beside the title', async () => {
    await showing(bridge());

    expect(screen.getByTestId('structure-site-status')).toMatchTextContent('Active');
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

describe('StructureDetailPage — incomplete data, in nr-frep form', () => {
  const incomplete = () =>
    bridge({
      outstanding: [
        { section: 'DETAILS', label: 'Deck Width' },
        { section: 'DETAILS', label: 'Estimated Load Restriction (year)' },
        { section: 'INSPECTIONS', label: 'Next Planned Routine Inspection' },
      ],
    });

  it('counts what is missing above the tabs', async () => {
    await showing(incomplete());

    expect(screen.getByTestId('structure-outstanding-summary')).toMatchTextContent(
      'Structure data is incomplete: 3 required items outstanding',
    );
  });

  it('badges the Details tab with the count', async () => {
    await showing(incomplete());

    expect(screen.getByRole('img', { name: 'Details: 3 items outstanding' })).toBeInTheDocument();
  });

  it('lists what is missing at the top of the tab, grouped by where it belongs', async () => {
    await showing(incomplete());

    const panel = screen.getByTestId('structure-outstanding-panel');
    expect(panel).toMatchTextContent('Details');
    expect(panel).toMatchTextContent('Deck Width');
    expect(panel).toMatchTextContent('Inspections');
    expect(panel).toMatchTextContent('Next Planned Routine Inspection');
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

    expect(valueOf('Number of Spans', section)).toBe('2');
    expect(valueOf('Superstructure', section)).toBe('Steel Girder');
    expect(valueOf('Deck Width (metres)', section)).toBe('4.27');
    expect(screen.queryByTestId('structure-section-culvert')).not.toBeInTheDocument();
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

  it('shows a culvert its culvert fields, with its number on the site', async () => {
    await showing(culvert());
    const section = screen.getByTestId('structure-section-culvert');

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
    expect(valueOf('This structure was replaced by structure #', section)).toBe('—');
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
    // Legacy's header, moved into a tab of its own as nr-fspts' FSP page opens on Information.
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
    ...overrides,
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
      await screen.findByRole('button', { name: 'Photograph (opens in a new tab)' }),
    );

    expect(api.getDocumentFile).toHaveBeenCalledWith('7', '1');
    expect(open).toHaveBeenCalledWith('', '_blank');
    await vi.waitFor(() => expect(opened.location.href).toMatch(/^blob:/));
    open.mockRestore();
  });

  it('says so beside the files when one cannot be opened', async () => {
    api.getDocuments.mockResolvedValue({ documents: [doc('1')] });
    api.getDocumentFile.mockRejectedValue(new Error('boom'));
    const opened = { close: vi.fn(), location: { href: '' } };
    const open = vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);
    await showing(bridge());

    await openDocuments();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Photograph (opens in a new tab)' }),
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
    expect(row).toMatchTextContent('12 m2');
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
