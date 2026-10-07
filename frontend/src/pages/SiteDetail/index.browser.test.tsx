import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SiteDetailPage from './index';

import type { CodeOption, OrgUnitOption } from '@/types/configuration';

const authorization = vi.hoisted(() => ({ canEdit: false, canDelete: false }));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => authorization }));

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getStructureInspectionStatusCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getManagementAreas: vi.fn(),
  getBusinessAreas: vi.fn(),
  getRecreationDistricts: vi.fn(),
  getRecreationProjectName: vi.fn(),
}));
const roadApi = vi.hoisted(() => ({ getRoadSection: vi.fn(), searchRoads: vi.fn() }));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn() }));
const siteApi = vi.hoisted(() => ({ getSite: vi.fn(), updateSite: vi.fn() }));
vi.mock('@/services/APIs', () => ({
  default: { configuration: api, client: clientApi, siteSearch: siteApi, road: roadApi },
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const statuses: CodeOption[] = [{ code: 'ACT', description: 'Active' }];
const types: CodeOption[] = [
  { code: 'CRS', description: 'Crossing' },
  { code: 'STRG', description: 'Storage' },
];
const districts: OrgUnitOption[] = [
  { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
];

/** One site as the server returns it: stored values, decimal degrees, longitude negative. */
const response = {
  siteId: 'BOWRON-001',
  crossingName: 'Deadman Creek',
  pointOfCommencementDistance: '12.50',
  userKm: '13.00',
  crossingSiteStatusCode: 'ACT',
  structureInspectionStatusCode: 'INS',
  crossingSiteTypeCode: 'CRS',
  specialAccessRqmtCode: null,
  orgUnitNo: 18,
  managementOrgUnitNo: null,
  businessAreaOrgUnitNo: null,
  forestFileId: 'R00123',
  roadSectionId: '01',
  forestServiceRoad: 'Bowron FSR',
  activeStructureIds: ['4021'],
  clientNumber: '00001012',
  clientLocnCode: '01',
  maintainerLabel: 'CANFOR CORPORATION \u00b7 Prince George \u00b7 00001012-01',
  capitalRoad: true,
  longitude: '-122.504306',
  latitude: '53.916667',
  utmZone: 10,
  utmEasting: 532000,
  utmNorthing: 5975000,
  pointOfAccessDescription: 'Helicopter required to reach the cove.',
  ntsMapSheetNumber: '92P/10',
  trimMapSheetNumber: '093G025',
};

/** A stand-in destination that shows where navigation landed, query string included. */
const Arrived = () => {
  const location = useLocation();
  const state = location.state as { fromSiteId?: string } | null;
  const from = state?.fromSiteId ? ` from ${state.fromSiteId}` : '';
  return <p data-testid="arrived">{`${location.pathname}${location.search}${from}`}</p>;
};

/**
 * Renders at a real site URL, so the page reads its id from the route as it does in the
 * application rather than from a prop no caller passes.
 */
const renderPage = async ({ canEdit = false, canDelete = false } = {}) => {
  authorization.canEdit = canEdit;
  authorization.canDelete = canDelete;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/inventory/site/BOWRON-001']}>
        <Routes>
          <Route path="/inventory/site/:siteId" element={<SiteDetailPage />} />
          {/* Stand-ins for where the structure buttons go, so a test can see it arrived. */}
          <Route
            path="/inventory/site/:siteId/add-structure"
            element={<p data-testid="arrived">add structure</p>}
          />
          <Route path="/inventory/structure/:structureId" element={<Arrived />} />
          <Route path="/inventory/structure-search" element={<Arrived />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  // Waits for the read to land — the page shows a skeleton until it does, so anything asserted
  // before this would be asserted against a loading state.
  await waitFor(() => {
    expect(screen.queryByTestId('site-detail-loading')).not.toBeInTheDocument();
  });
  return result;
};

const edit = () => fireEvent.click(screen.getByTestId('site-detail-edit'));

/** True when the field is a read-only cell rather than something the user can type into. */
const isReadOnlyCell = (labelText: string) =>
  Boolean(
    Array.from(document.querySelectorAll('.read-only-field__label')).some(
      (node) => node.textContent?.trim() === labelText,
    ),
  );

beforeEach(() => {
  api.getSiteStatusCodes.mockResolvedValue(statuses);
  api.getSiteTypeCodes.mockResolvedValue(types);
  api.getStructureInspectionStatusCodes.mockResolvedValue([]);
  api.getSpecialAccessCodes.mockResolvedValue([]);
  api.getForestDistricts.mockResolvedValue(districts);
  api.getManagementAreas.mockResolvedValue([]);
  api.getBusinessAreas.mockResolvedValue([]);
  clientApi.searchClients.mockReset();
  clientApi.searchClients.mockResolvedValue([]);
  siteApi.getSite.mockReset();
  siteApi.getSite.mockResolvedValue(response);
  siteApi.updateSite.mockReset();
  siteApi.updateSite.mockResolvedValue(undefined);
  api.getRecreationDistricts.mockResolvedValue([]);
  api.getRecreationProjectName.mockResolvedValue({ forestFileId: '', projectName: null });
  roadApi.getRoadSection.mockReset();
  // The stored pair names a road, in district 18 — the site's own.
  roadApi.getRoadSection.mockImplementation((file: string, section: string) =>
    file === 'R00123' && section === '01'
      ? Promise.resolve({
          forestFileId: 'R00123',
          roadSectionId: '01',
          forestServiceRoad: 'Bowron FSR',
          orgUnitNo: 18,
        })
      : Promise.reject(new Error('No road section was found.')),
  );
});

describe('SiteDetailPage — reading', () => {
  it('names the site it is showing', async () => {
    await renderPage();

    expect(screen.getByRole('heading', { name: 'Site BOWRON-001' })).toBeInTheDocument();
  });

  it('shows every field as a value rather than as an input', async () => {
    // The commonest thing done on this screen is looking something up. Legacy opens straight into
    // an editable form, which puts that reader one stray keystroke from changing the record.
    await renderPage({ canDelete: true });

    expect(isReadOnlyCell('Site #')).toBe(true);
    expect(isReadOnlyCell('Status')).toBe(true);
    expect(isReadOnlyCell('Crossing Name')).toBe(true);
    expect(isReadOnlyCell('Site Details')).toBe(true);
    expect(screen.queryByTestId('site-form-crossingName')).not.toBeInTheDocument();
  });

  it('shows the coordinates as one value each, not six boxes', async () => {
    await renderPage();

    expect(isReadOnlyCell('Longitude')).toBe(true);
    expect(isReadOnlyCell('Latitude')).toBe(true);
    expect(screen.queryByTestId('site-form-longitudeDegrees')).not.toBeInTheDocument();
  });
});

describe('SiteDetailPage — the values it shows', () => {
  it('shows what the server returned, decoded where it is a code', async () => {
    // "Active", not "ACT": the screen holds the code tables and is the only thing that knows how
    // a stored value should read.
    await renderPage();

    expect(screen.getByText('Deadman Creek')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('DPG - Prince George')).toBeInTheDocument();
  });

  it('reads the UTM reference as one value, with the letters that tell the numbers apart', async () => {
    // Three numbers in a row say nothing about which is the easting and which the northing.
    await renderPage();

    expect(screen.getByText('10 · 532000E · 5975000N')).toBeInTheDocument();
  });

  it('turns the stored decimal degrees back into degrees, minutes and seconds', async () => {
    // -122.504306 is 122° 30′ 15.5″ west. Shown with the minus legacy prints beside the boxes —
    // the sign carries nothing a reader can use.
    await renderPage();

    expect(screen.getByText('\u2212122° 30′ 15.5″')).toBeInTheDocument();
    expect(screen.getByText('53° 55′ 0″')).toBeInTheDocument();
  });

  it('shows the maintainer and the road, which it could not have resolved itself', async () => {
    // Both come from tables the screen has no other reason to read; legacy fetches each
    // separately, the maintainer through its own getClientDetails() call on load.
    await renderPage();

    expect(
      screen.getByText('CANFOR CORPORATION · Prince George · 00001012-01'),
    ).toBeInTheDocument();
  });

  it('shows the Forest Service Road the server returned with the site', async () => {
    // The page used to drop it on the floor: the response carried the name and the form was never
    // given it, so every site read "—".
    await renderPage();

    expect(screen.getByText('Bowron FSR')).toBeInTheDocument();
  });

  it('reads the capital road flag as a word', async () => {
    await renderPage();

    expect(screen.getByText('Yes')).toBeInTheDocument();
  });

  it('says so when the site cannot be read, rather than showing an empty form', async () => {
    siteApi.getSite.mockRejectedValue(new Error('Site BOWRON-001 was not found.'));

    await renderPage();

    expect(await screen.findByTestId('site-detail-error')).toHaveTextContent(
      'Site BOWRON-001 was not found.',
    );
  });
});

describe('SiteDetailPage — the structure actions', () => {
  it('offers both once the site is on screen', async () => {
    // Legacy keeps its `structuresButton` div hidden until the site exists — on a blank Add Site
    // form there are no structures to add one to or list.
    await renderPage({ canEdit: true });

    expect(screen.getByTestId('site-detail-add-structure')).toBeInTheDocument();
    expect(screen.getByTestId('site-detail-structures')).toBeInTheDocument();
  });

  it('withholds Add Structure from someone who cannot create one', async () => {
    // Legacy wraps it in `/level1Access` and leaves Display Structures ungated.
    await renderPage();

    expect(screen.queryByTestId('site-detail-add-structure')).not.toBeInTheDocument();
    expect(screen.getByTestId('site-detail-structures')).toBeInTheDocument();
  });

  it('opens Add Structure for this site', async () => {
    await renderPage({ canEdit: true });

    fireEvent.click(screen.getByTestId('site-detail-add-structure'));

    expect(await screen.findByTestId('arrived')).toHaveTextContent('add structure');
  });

  it('opens the structure itself when the site has one', async () => {
    await renderPage();

    fireEvent.click(screen.getByTestId('site-detail-structures'));

    // Carrying the site, so the structure's breadcrumb leads back here.
    expect(await screen.findByTestId('arrived')).toHaveTextContent(
      '/inventory/structure/4021 from BOWRON-001',
    );
  });

  it('opens Structure Search filtered to the site when it has several', async () => {
    // A site can hold several culverts at once; legacy lists them through the search.
    siteApi.getSite.mockResolvedValue({ ...response, activeStructureIds: ['4021', '4022'] });
    await renderPage();

    fireEvent.click(screen.getByTestId('site-detail-structures'));

    expect(await screen.findByTestId('arrived')).toHaveTextContent(
      '/inventory/structure-search?siteId=BOWRON-001',
    );
  });

  it('leaves Display Structures off a site with no structures', async () => {
    siteApi.getSite.mockResolvedValue({ ...response, activeStructureIds: [] });
    await renderPage();

    expect(screen.queryByTestId('site-detail-structures')).not.toBeInTheDocument();
  });
});

describe('SiteDetailPage — the panel', () => {
  it('sets the values on a grey panel, in the reading style until Edit is pressed', async () => {
    // Grey over white; bold labels with their values close under them while reading. Editing keeps
    // the form's own spacing, which lines a read-only value up with the input beside it.
    await renderPage({ canEdit: true });
    const panel = screen.getByTestId('site-detail-panel');

    expect(panel).toHaveClass('site-detail__panel', 'site-detail__panel--view');

    edit();

    expect(panel).not.toHaveClass('site-detail__panel--view');
  });
});

describe('SiteDetailPage — who is offered Edit', () => {
  it('offers it to a user who may change something', async () => {
    await renderPage({ canEdit: true });

    expect(screen.getByTestId('site-detail-edit')).toBeInTheDocument();
  });

  it('withholds it from a reader', async () => {
    // An action you cannot perform is never rendered, not disabled — the legacy rule carried
    // forward (cbr-navigation.local.md §2).
    await renderPage();

    expect(screen.queryByTestId('site-detail-edit')).not.toBeInTheDocument();
  });
});

describe('SiteDetailPage — editing', () => {
  it('swaps Edit for Cancel and Save', async () => {
    await renderPage({ canDelete: true });

    edit();

    expect(screen.getByTestId('site-detail-cancel')).toBeInTheDocument();
    expect(screen.getByTestId('site-detail-save')).toBeInTheDocument();
    expect(screen.queryByTestId('site-detail-edit')).not.toBeInTheDocument();
  });

  it('opens only the fields a Level 2 user may change', async () => {
    await renderPage({ canEdit: true, canDelete: true });

    edit();

    expect(screen.getByTestId('site-form-crossingName')).toBeInTheDocument();
    expect(screen.getByTestId('site-form-crossingSiteStatusCode')).toBeInTheDocument();
    expect(screen.getByTestId('site-form-pointOfAccessDescription')).toBeInTheDocument();
  });

  it('opens only Site Details for a Level 1 user', async () => {
    // `site.jsp` puts Level 1 inside the `isLevel1` branch for the description and nowhere else;
    // every other field's editable branch is `isLevel2`.
    await renderPage({ canEdit: true });

    edit();

    expect(screen.getByTestId('site-form-pointOfAccessDescription')).toBeInTheDocument();
    expect(screen.queryByTestId('site-form-crossingName')).not.toBeInTheDocument();
    expect(isReadOnlyCell('Crossing Name')).toBe(true);
  });

  it('keeps the fields no role may change as values, even while editing', async () => {
    // Each is disabled in every branch of the legacy form, including the Level 2 one: the site
    // number is the key, and the rest are written from LRMOPS by UPDATE_CROSSING_SITE_FROM_LRM.
    await renderPage({ canEdit: true, canDelete: true });

    edit();

    expect(isReadOnlyCell('Site #')).toBe(true);
    expect(isReadOnlyCell('Designated Maintainer')).toBe(true);
    expect(isReadOnlyCell('User Kilometres')).toBe(true);
    expect(isReadOnlyCell('BCTS BA Responsible')).toBe(true);
    expect(screen.queryByTestId('site-form-businessAreaOrgUnitNo')).not.toBeInTheDocument();
    expect(isReadOnlyCell('Capital Road')).toBe(true);
  });

  it('returns to reading on Cancel', async () => {
    await renderPage({ canDelete: true });
    edit();

    fireEvent.click(screen.getByTestId('site-detail-cancel'));

    expect(screen.getByTestId('site-detail-edit')).toBeInTheDocument();
    expect(screen.queryByTestId('site-form-crossingName')).not.toBeInTheDocument();
  });

  it('validates on Save, as Add Site does', async () => {
    await renderPage({ canDelete: true });
    edit();

    fireEvent.change(screen.getByTestId('site-form-crossingName'), { target: { value: '' } });
    fireEvent.click(screen.getByTestId('site-detail-save'));

    expect(await screen.findByText('Crossing Name is required.')).toBeInTheDocument();
  });

  it('keeps what the user is typing when the record is refetched underneath them', async () => {
    // The query refetches on window focus. Copying the server's answer over a half-finished edit
    // would be the worst possible moment to be right about what the record says.
    await renderPage({ canDelete: true });
    edit();
    fireEvent.change(screen.getByTestId('site-form-crossingName'), {
      target: { value: 'Bowron River' },
    });

    siteApi.getSite.mockResolvedValue({ ...response, crossingName: 'Something Else' });
    fireEvent.focus(window);

    await waitFor(() => {
      expect(screen.getByTestId('site-form-crossingName')).toHaveValue('Bowron River');
    });
  });
});

describe('SiteDetailPage — the road, while editing', () => {
  it('keeps showing the road name once Edit is pressed', async () => {
    await renderPage({ canEdit: true, canDelete: true });

    edit();

    expect(await screen.findByText('Bowron FSR')).toBeInTheDocument();
  });

  it("keeps a storage site's Forest District read-only while its road decides it", async () => {
    // Legacy disables the district for a storage site once the pair names a road, as it always
    // does for a crossing. The page never told the form the road had resolved, so a storage site
    // came up with an editable district dropdown.
    siteApi.getSite.mockResolvedValue({ ...response, crossingSiteTypeCode: 'STRG' });
    await renderPage({ canEdit: true, canDelete: true });

    edit();

    await waitFor(() => {
      expect(screen.queryByTestId('site-form-orgUnitNo')).not.toBeInTheDocument();
    });
    expect(isReadOnlyCell('Forest District')).toBe(true);
  });

  it('says on Project File ID# when an edit leaves the pair naming no road', async () => {
    await renderPage({ canEdit: true, canDelete: true });
    edit();
    await screen.findByText('Bowron FSR');

    fireEvent.change(screen.getByTestId('site-form-roadSectionId'), { target: { value: '99' } });

    expect(
      await screen.findByText('No road matches this Project File ID# and Br.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Bowron FSR')).not.toBeInTheDocument();
  });

  it('does not rewrite the stored district just by being viewed', async () => {
    // The road's region is not the stored district here. Reading the site must show what is
    // stored; only an edit lets the road write the district.
    roadApi.getRoadSection.mockResolvedValue({
      forestFileId: 'R00123',
      roadSectionId: '01',
      forestServiceRoad: 'Bowron FSR',
      orgUnitNo: 99,
    });
    await renderPage();

    await waitFor(() => expect(roadApi.getRoadSection).toHaveBeenCalled());
    expect(screen.getByText('DPG - Prince George')).toBeInTheDocument();
  });
});

describe('SiteDetailPage — saving', () => {
  const type = (name: string, value: string) =>
    fireEvent.change(screen.getByTestId(`site-form-${name}`), { target: { value } });
  const save = () => fireEvent.click(screen.getByTestId('site-detail-save'));

  it('sends the edit and goes back to reading the site', async () => {
    await renderPage({ canEdit: true, canDelete: true });
    edit();
    await screen.findByText('Bowron FSR');

    type('crossingName', 'Deadman Creek Bridge');
    save();

    await waitFor(() => {
      expect(siteApi.updateSite).toHaveBeenCalledWith(
        'BOWRON-001',
        expect.objectContaining({ crossingName: 'Deadman Creek Bridge' }),
      );
    });
    expect(await screen.findByTestId('site-detail-edit')).toBeInTheDocument();
    // Read again rather than trusted: the stored site is what view mode shows.
    expect(siteApi.getSite).toHaveBeenCalledTimes(2);
  });

  it('never sends the fields LRMOPS writes', async () => {
    await renderPage({ canEdit: true, canDelete: true });
    edit();
    await screen.findByText('Bowron FSR');

    save();

    await waitFor(() => expect(siteApi.updateSite).toHaveBeenCalled());
    const sent = siteApi.updateSite.mock.calls[0][1];
    for (const locked of [
      'siteId',
      'userKm',
      'businessAreaOrgUnitNo',
      'clientNumber',
      'clientLocnCode',
      'capitalRoad',
    ]) {
      expect(sent).not.toHaveProperty(locked);
    }
  });

  it('refuses to send a required field left blank', async () => {
    await renderPage({ canEdit: true, canDelete: true });
    edit();
    await screen.findByText('Bowron FSR');

    type('crossingName', '');
    save();

    expect(await screen.findByText('Crossing Name is required.')).toBeInTheDocument();
    expect(siteApi.updateSite).not.toHaveBeenCalled();
  });

  it('lets Level 1 save Site Details on a site with gaps they cannot fill', async () => {
    // Only Site Details is theirs to change, so only Site Details is checked.
    siteApi.getSite.mockResolvedValue({ ...response, crossingName: null, latitude: null });
    await renderPage({ canEdit: true });
    edit();

    type('pointOfAccessDescription', 'Gate key at the district office.');
    save();

    await waitFor(() => {
      expect(siteApi.updateSite).toHaveBeenCalledWith(
        'BOWRON-001',
        expect.objectContaining({ pointOfAccessDescription: 'Gate key at the district office.' }),
      );
    });
  });

  it('shows what the server refused beside the field', async () => {
    siteApi.updateSite.mockRejectedValue({
      body: { detail: 'Refused.', fieldErrors: { crossingName: 'Crossing Name is taken.' } },
    });
    await renderPage({ canEdit: true, canDelete: true });
    edit();
    await screen.findByText('Bowron FSR');

    save();

    expect(await screen.findByText('Crossing Name is taken.')).toBeInTheDocument();
    expect(screen.queryByTestId('site-detail-save-error')).not.toBeInTheDocument();
    expect(screen.getByTestId('site-detail-cancel')).toBeInTheDocument();
  });

  it('says so when a save fails for a reason no field explains', async () => {
    siteApi.updateSite.mockRejectedValue({ body: { detail: 'Service unavailable.' } });
    await renderPage({ canEdit: true, canDelete: true });
    edit();
    await screen.findByText('Bowron FSR');

    save();

    expect(await screen.findByTestId('site-detail-save-error')).toHaveTextContent(
      'Service unavailable.',
    );
  });
});
