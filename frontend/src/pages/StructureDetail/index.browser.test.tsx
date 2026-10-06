import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { userEvent } from '@vitest/browser/context';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import StructureDetailPage from './index';

import type { StructureDetailResponse } from './structureResponse';

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const api = vi.hoisted(() => ({ getStructure: vi.fn() }));
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

/** A read-only field's value, found by its label within a container. */
const valueOf = (label: string, container: HTMLElement = document.body) => {
  const labelElement = within(container)
    .getAllByText(label, { selector: '.read-only-field__label' })
    .at(0) as HTMLElement;
  return labelElement.parentElement?.querySelector('.read-only-field__value')?.textContent;
};

beforeEach(() => {
  api.getStructure.mockReset();
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

    expect(await screen.findByTestId('structure-detail-error')).toHaveTextContent(
      'Structure not found',
    );
  });

  it('says so when it could not be loaded', async () => {
    api.getStructure.mockRejectedValue(Object.assign(new Error('boom'), { status: 500 }));
    renderAt();

    expect(await screen.findByTestId('structure-detail-error')).toHaveTextContent(
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

    expect(screen.getByTestId('structure-site-status')).toHaveTextContent('Active');
  });

  it('shows no status pill when the site has no status', async () => {
    const structure = bridge();
    await showing({ ...structure, site: { ...structure.site!, siteStatus: NONE } });

    expect(screen.queryByTestId('structure-site-status')).not.toBeInTheDocument();
  });

  it('tags an archived structure, which legacy never showed', async () => {
    await showing(bridge({ active: false }));

    expect(screen.getByTestId('structure-archived')).toHaveTextContent('Archived');
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

    expect(screen.getByTestId('structure-outstanding-summary')).toHaveTextContent(
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
    expect(panel).toHaveTextContent('Details');
    expect(panel).toHaveTextContent('Deck Width');
    expect(panel).toHaveTextContent('Inspections');
    expect(panel).toHaveTextContent('Next Planned Routine Inspection');
  });

  it('folds the list away on request', async () => {
    await showing(incomplete());

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

  it('lists comments on their own tab, newest first, as the server sends them', async () => {
    await showing(bridge());

    await userEvent.click(screen.getByRole('tab', { name: 'Comments' }));
    const rows = within(screen.getByTestId('structure-comments-tab')).getAllByRole('row');

    expect(rows[1]).toHaveTextContent('Deck replaced.');
    expect(rows[1]).toHaveTextContent('IDIR\\JSMITH');
    expect(rows[1]).toHaveTextContent('Jun 3, 2024 2:05 PM');
    expect(rows[2]).toHaveTextContent('Installed.');
  });

  it('no longer lists comments on the Details tab', async () => {
    await showing(bridge());

    expect(
      within(screen.getByTestId('structure-details-tab')).queryByText('Deck replaced.'),
    ).not.toBeInTheDocument();
  });

  it('says when there are no comments', async () => {
    await showing(bridge({ comments: [] }));

    await userEvent.click(screen.getByRole('tab', { name: 'Comments' }));

    expect(screen.getByTestId('structure-comments-tab')).toHaveTextContent('No comments.');
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

    expect(screen.queryByTestId('load-rating-r0')).not.toBeInTheDocument();

    await userEvent.click(
      screen.getByText('Show ratings from before the superstructure was installed (1)'),
    );

    expect(screen.getByTestId('load-rating-r0')).toBeInTheDocument();
  });

  it('links the structures this one replaced, in a new tab', async () => {
    await showing(bridge());
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
