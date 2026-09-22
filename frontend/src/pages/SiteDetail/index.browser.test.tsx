import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
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
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn() }));
const siteApi = vi.hoisted(() => ({ getSite: vi.fn() }));
vi.mock('@/services/APIs', () => ({
  default: { configuration: api, client: clientApi, siteSearch: siteApi },
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

    expect(isReadOnlyCell('Longitude (west)')).toBe(true);
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
    // -122.504306 is 122° 30′ 15.5″ west. Shown unsigned, because every site in the province is —
    // the sign carries nothing a reader can use.
    await renderPage();

    expect(screen.getByText('122° 30′ 15.5″')).toBeInTheDocument();
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

  it('disables both until the structure screens exist', async () => {
    // Disabled rather than hidden: withholding them would say this site has no structures, which
    // is a different statement and possibly an untrue one.
    await renderPage({ canEdit: true });

    expect(screen.getByTestId('site-detail-add-structure')).toBeDisabled();
    expect(screen.getByTestId('site-detail-structures')).toBeDisabled();
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
    // number is the key, the maintainer comes from the lookup, Capital Road from the road record.
    await renderPage({ canEdit: true, canDelete: true });

    edit();

    expect(isReadOnlyCell('Site #')).toBe(true);
    expect(isReadOnlyCell('Designated Maintainer')).toBe(true);
    expect(isReadOnlyCell('User Kilometres')).toBe(true);
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
