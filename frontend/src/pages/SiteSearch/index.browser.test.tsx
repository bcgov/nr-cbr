import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@vitest/browser/context';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SiteSearchPage from './index';

import type { CodeOption } from '@/types/configuration';

const authorization = vi.hoisted(() => ({ canDelete: false }));
const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getStructureInspectionStatusCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getManagementAreas: vi.fn(),
}));

vi.mock('@/hooks/useAuthorization', () => ({
  useAuthorization: () => authorization,
}));

// The delete outcome is a toast now, so the page reads the notification context. Mocked rather
// than wrapped in a real NotificationProvider so the assertions are on what the page asked to show,
// not on Carbon's rendering of it — which NotificationProvider's own test already covers.
const display = vi.hoisted(() => vi.fn());
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

// Mocked at the service rather than at the hook, so the query key, the fallback to an empty list
// and the error branch are all exercised by these tests instead of being stubbed past.
const siteSearchApi = vi.hoisted(() => ({ searchSites: vi.fn(), deleteSite: vi.fn() }));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, siteSearch: siteSearchApi, client: clientApi },
}));

const renderPage = (canDelete = false) => {
  authorization.canDelete = canDelete;
  // retry: false so a rejected lookup surfaces immediately; the app's own default already disables
  // retries, but a test should not depend on that staying true.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SiteSearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const statuses: CodeOption[] = [
  { code: 'ACT', description: 'Active' },
  { code: 'DEC', description: 'Decommissioned' },
];

/** An empty page, in the shape the backend returns. */
const emptyPage = (pageSize = 20) => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  pageNumber: 0,
  pageSize,
});

const site = (id: string) => ({
  id,
  orgUnitCode: 'DPG',
  orgUnitName: 'Prince George Natural Resource District',
  forestServiceRoad: 'Bowron FSR',
  pointOfCommencementDistance: '12.50',
  crossingName: 'Deadman Creek',
  forestFileId: 'R00123',
  roadSectionId: '01',
  crossingSiteStatusCode: 'ACT',
  crossingSiteStatusDescription: 'Active',
});

/** Runs a search and waits for the results to land, rather than for the loading skeleton. */
const searchAndWait = async () => {
  search();
  await waitFor(() => {
    expect(screen.getByTestId('site-search-results')).toBeInTheDocument();
  });
};

beforeEach(() => {
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  siteSearchApi.searchSites.mockReset();
  siteSearchApi.searchSites.mockResolvedValue(emptyPage());
  siteSearchApi.deleteSite.mockReset();
  siteSearchApi.deleteSite.mockResolvedValue(undefined);
  clientApi.searchClients.mockReset();
  clientApi.searchClients.mockResolvedValue([]);
  // Shared across tests because vi.hoisted runs once — without this a "was a toast shown" assertion
  // passes on a call the previous test made.
  display.mockClear();
});

const search = () => fireEvent.click(screen.getByTestId('site-search-submit'));

describe('SiteSearchPage — criteria form', () => {
  it('offers every criterion the legacy form does', () => {
    // Field-for-field with site_search.jsp. A missing criterion is a search someone can no longer
    // run, and there is no error to notice — the form just quietly does less.
    renderPage();

    for (const field of [
      'siteId',
      'siteStatusCode',
      'forestFileId',
      'roadSectionId',
      'structureInspectionStatusCode',
      'forestServiceRoad',
      // One control where legacy had three boxes: "Designated Maintainer Client Number", "Client
      // Location Code" and "Designated Maintainer". The criteria behind it are unchanged — a pick
      // sets the number and the location code, typed text searches the name.
      'maintainer',
      'crossingName',
      'orgUnit',
      'kiloStart',
      'kiloEnd',
      'managementOrgUnit',
      'userKmStart',
      'userKmEnd',
      'specialAccessCode',
      'siteTypeCode',
      'incomplete',
      'capitalRoad',
    ]) {
      expect(screen.getByTestId(`site-search-${field}`)).toBeInTheDocument();
    }
  });

  it('keeps the legacy labels, which is the vocabulary the business uses', () => {
    renderPage();

    expect(screen.getByLabelText('Site #')).toBeInTheDocument();
    expect(screen.getByLabelText('Project File ID#')).toBeInTheDocument();
    // "Designated Maintainer", not "...Client Number": the lookup asks for the maintainer, and the
    // client number is now something it returns rather than something the user is made to know.
    // By role, because Carbon's ComboBox labels both the input and its toggle button — and because
    // asserting the role is what pins this as a lookup rather than the text box it replaced.
    expect(screen.getByRole('combobox', { name: 'Designated Maintainer' })).toBeInTheDocument();
    // The two filters are Carbon Toggles, which label a `switch` by aria-labelledby rather than a
    // <label for>. Asserting the role as well as the name keeps this honest about the control type.
    expect(screen.getByRole('switch', { name: /Incomplete Data\?/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Capital Road/ })).toBeInTheDocument();
  });

  it('validates each kilometre bound on its own, beside the box', async () => {
    // Legacy raises one page-level "Kilometres must be numeric" for the pair, leaving the user to
    // work out which of the two boxes it means.
    renderPage();

    fireEvent.change(screen.getByTestId('site-search-kiloStart'), { target: { value: 'abc' } });

    expect(await screen.findByText('Kilometres must be a number, e.g. 12.5')).toBeInTheDocument();
    expect(screen.getByTestId('site-search-kiloStart')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByTestId('site-search-kiloEnd')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('validates User Kilometres To with nothing else filled in', async () => {
    // The legacy bug this replaces: createSearch() guards the User Km upper bound with a test on
    // kiloEnd, so this box does nothing unless an unrelated one happens to be filled.
    renderPage();

    fireEvent.change(screen.getByTestId('site-search-userKmEnd'), { target: { value: 'abc' } });

    expect(
      await screen.findByText('User Kilometres must be a number, e.g. 12.5'),
    ).toBeInTheDocument();
  });

  it('still names the range boxes for a screen reader with the labels hidden', () => {
    // hideLabel keeps the <label> and hides it visually. The temptation when removing a visible
    // label is to drop it altogether, which leaves two identical unnamed boxes either side of a
    // decorative dash — unusable without sight of the layout.
    renderPage();

    expect(
      screen.getByLabelText('From', { selector: '#site-search-kiloStart' }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('To', { selector: '#site-search-userKmEnd' })).toBeInTheDocument();
  });

  it('accepts a decimal bound', () => {
    renderPage();

    fireEvent.change(screen.getByTestId('site-search-kiloStart'), { target: { value: '12.5' } });

    expect(screen.getByTestId('site-search-kiloStart')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('does not run a search while a bound is invalid', () => {
    renderPage();
    fireEvent.change(screen.getByTestId('site-search-kiloStart'), { target: { value: 'abc' } });

    search();

    expect(screen.queryByTestId('site-search-results')).not.toBeInTheDocument();
  });

  it('enforces the legacy maxlengths', () => {
    // These mirror the underlying column widths, so a longer value could never have matched.
    renderPage();

    expect(screen.getByTestId('site-search-siteId')).toHaveAttribute('maxlength', '14');
    expect(screen.getByTestId('site-search-forestFileId')).toHaveAttribute('maxlength', '10');
    expect(screen.getByTestId('site-search-crossingName')).toHaveAttribute('maxlength', '20');
  });

  it('records what the user types', () => {
    renderPage();
    const siteId = screen.getByTestId('site-search-siteId');

    fireEvent.change(siteId, { target: { value: '12345' } });

    expect(siteId).toHaveValue('12345');
  });

  it('toggles the two boolean filters', () => {
    renderPage();
    const incomplete = screen.getByRole('switch', { name: /Incomplete Data\?/ });
    expect(incomplete).not.toBeChecked();

    fireEvent.click(incomplete);

    expect(incomplete).toBeChecked();
  });

  it('clears every criterion at once', () => {
    renderPage();
    const siteId = screen.getByTestId('site-search-siteId');
    fireEvent.change(siteId, { target: { value: '12345' } });

    fireEvent.click(screen.getByTestId('site-search-reset'));

    expect(siteId).toHaveValue('');
  });
});

describe('SiteSearchPage — results', () => {
  it('shows no results table until a search is run', () => {
    // Legacy gates the whole block on `<c:if test="${search}">`. An empty table on an untouched
    // page reads as "no sites match", which is a different statement.
    renderPage();

    expect(screen.queryByTestId('site-search-results')).not.toBeInTheDocument();
  });

  it('shows the table, empty, once a search has been run', async () => {
    renderPage();

    await searchAndWait();

    expect(screen.getByText('No sites found.')).toBeInTheDocument();
  });

  it('shows a skeleton while the search runs, not "No sites found."', async () => {
    // An empty table mid-search says the search finished and matched nothing — a different answer,
    // and one a user would act on by changing their criteria.
    let resolve: (page: unknown) => void = () => {};
    siteSearchApi.searchSites.mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    renderPage();

    search();

    expect(await screen.findByTestId('site-search-loading')).toBeInTheDocument();
    expect(screen.queryByText('No sites found.')).not.toBeInTheDocument();
    resolve(emptyPage());
  });

  it('sends the criteria the user submitted', async () => {
    renderPage();
    fireEvent.change(screen.getByTestId('site-search-siteId'), { target: { value: '12345' } });

    await searchAndWait();

    expect(siteSearchApi.searchSites).toHaveBeenCalledWith(
      expect.objectContaining({ siteId: '12345' }),
      0,
      20,
    );
  });

  it('does not search while the user is still typing', async () => {
    // The query is keyed on the submitted criteria, not the form. Keyed on the form it would fire a
    // request per keystroke, and editing after a search would change what the visible rows claim.
    renderPage();

    fireEvent.change(screen.getByTestId('site-search-siteId'), { target: { value: '12345' } });

    expect(siteSearchApi.searchSites).not.toHaveBeenCalled();
  });

  it('renders the rows the server returned', async () => {
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [site('SITE-1')],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();

    await searchAndWait();

    expect(screen.getByTestId('site-row-SITE-1')).toBeInTheDocument();
    expect(screen.getByText('Deadman Creek')).toBeInTheDocument();
    // Code and name together, as nr-frep renders an org unit. Legacy hides the name in a tooltip.
    expect(screen.getByText('DPG — Prince George Natural Resource District')).toBeInTheDocument();
    // Project File ID#-Br. is the two columns joined, which is how legacy renders it.
    expect(screen.getByText('R00123-01')).toBeInTheDocument();
  });

  it('asks the server for the next page, one-based on screen and zero-based on the wire', async () => {
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [site('SITE-1')],
      totalElements: 40,
      totalPages: 2,
    });
    renderPage();
    await searchAndWait();

    fireEvent.click(screen.getByLabelText('Next page'));

    await waitFor(() => {
      expect(siteSearchApi.searchSites).toHaveBeenLastCalledWith(expect.anything(), 1, 20);
    });
  });

  it('goes back to page one when a new search is run', async () => {
    // Page 4 of the previous result set is an empty table under a search that now matches twelve.
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [site('SITE-1')],
      totalElements: 40,
      totalPages: 2,
    });
    renderPage();
    await searchAndWait();
    fireEvent.click(screen.getByLabelText('Next page'));
    await waitFor(() => {
      expect(siteSearchApi.searchSites).toHaveBeenLastCalledWith(expect.anything(), 1, 20);
    });

    search();

    await waitFor(() => {
      expect(siteSearchApi.searchSites).toHaveBeenLastCalledWith(expect.anything(), 0, 20);
    });
  });

  it('says so when the search fails, rather than showing an empty table', async () => {
    siteSearchApi.searchSites.mockRejectedValue(new Error('500'));
    renderPage();

    search();

    expect(await screen.findByTestId('site-search-error')).toBeInTheDocument();
    expect(screen.queryByText('No sites found.')).not.toBeInTheDocument();
  });

  it('reports the total number of matches, not the size of the page', async () => {
    // The count comes from the server's totalElements. Taken from the rows on screen it would say
    // "20 matches" for a result set of two thousand, which is worse than no count at all.
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [site('SITE-1')],
      totalElements: 1997,
      totalPages: 100,
    });
    renderPage();

    await searchAndWait();

    expect(screen.getByText('Sites — 1997 matches')).toBeInTheDocument();
  });

  it('says "1 match", not "1 matches"', async () => {
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [site('SITE-1')],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();

    await searchAndWait();

    expect(screen.getByText('Sites — 1 match')).toBeInTheDocument();
  });

  it('falls back to the code alone when a site has no district name', async () => {
    // A site with no org unit at all is normal — it is what "Incomplete Data?" finds — so the cell
    // has to degrade rather than render "DPG — undefined" or a leading dash.
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [{ ...site('NO-NAME'), orgUnitName: null }],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();

    await searchAndWait();

    expect(screen.getByText('DPG')).toBeInTheDocument();
  });

  it('renders the status as a coloured pill', async () => {
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [
        site('SITE-1'),
        {
          ...site('SITE-2'),
          crossingSiteStatusCode: 'BAR',
          crossingSiteStatusDescription: 'Barricaded',
        },
      ],
      totalElements: 2,
      totalPages: 1,
    });
    renderPage();

    await searchAndWait();

    // The colour comes from the code, so a reworded description cannot silently change it.
    expect(screen.getByText('Active').closest('.cds--tag')).toHaveClass('cds--tag--green');
    expect(screen.getByText('Barricaded').closest('.cds--tag')).toHaveClass('cds--tag--red');
  });

  it('shows no pill at all for a site with no status', async () => {
    // "Incomplete Data?" exists to find these. An empty outlined pill would read as a status whose
    // name failed to load.
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [
        { ...site('NO-STATUS'), crossingSiteStatusCode: null, crossingSiteStatusDescription: null },
      ],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();

    await searchAndWait();

    expect(screen.getByTestId('site-row-NO-STATUS').querySelector('.cds--tag')).toBeNull();
  });

  it('renders the legacy columns in the legacy order', async () => {
    renderPage();
    await searchAndWait();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).toEqual([
      'Site #',
      'District Code',
      'Forest Service Road',
      'KM',
      'Crossing Name',
      'Project File ID#-Br.',
      'Status',
    ]);
  });
});

describe('SiteSearchPage — the Delete column is privilege-gated', () => {
  it('is absent for a user without the destructive capability', async () => {
    // Legacy wraps this column in <cbr:authorize grantedAction="/deleteSite">. An action you cannot
    // perform is never rendered — not disabled (cbr-navigation.local.md §2).
    renderPage(false);
    await searchAndWait();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).not.toContain('Actions');
  });

  it('is present for CBR_LEVEL_2 and above', async () => {
    renderPage(true);
    await searchAndWait();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).toContain('Actions');
  });
});

describe('SiteSearchPage — the dropdowns come from the server', () => {
  it('names the unfiltered option rather than leaving it blank', async () => {
    // A blank first option reads as a value that failed to load, and gives a user who has set the
    // filter no obvious way to unset it.
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('Any status');
    });
    expect(screen.getByTestId('site-search-siteTypeCode')).toHaveTextContent('Any site type');
    expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('Any district');
  });

  it('keeps the unfiltered option valueless, so choosing it clears the filter', async () => {
    api.getSiteStatusCodes.mockResolvedValue([{ code: 'ACT', description: 'Active' }]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('ACT - Active');
    });

    fireEvent.change(screen.getByTestId('site-search-siteStatusCode'), {
      target: { value: 'ACT' },
    });
    fireEvent.change(screen.getByTestId('site-search-siteStatusCode'), { target: { value: '' } });
    await searchAndWait();

    expect(siteSearchApi.searchSites).toHaveBeenLastCalledWith(
      expect.objectContaining({ siteStatusCode: '' }),
      0,
      20,
    );
  });

  it('tells the user a district is needed before Management Area means anything', async () => {
    // The list is empty by design until a district is chosen, so "Any management area" would be
    // offering a filter over nothing.
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
    ]);
    api.getManagementAreas.mockResolvedValue([
      { orgUnitNo: '26', orgUnitCode: 'DRV', orgUnitName: 'Robson Valley' },
    ]);
    renderPage();
    expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveTextContent(
      'Select a forest district first',
    );
    // The districts have to be on the page first: a <select> silently ignores a value it has no
    // option for, so firing the change early leaves the criterion blank and the test green for the
    // wrong reason.
    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('DPG - Prince George');
    });

    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '18' } });

    await waitFor(() => {
      expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveTextContent(
        'Any management area',
      );
    });
  });

  it('labels an option "CODE - Description", as nr-frep does', async () => {
    // The code is what the business says out loud and what appears on paper; the description alone
    // leaves the user translating between the two.
    api.getSiteStatusCodes.mockResolvedValue([{ code: 'ACT', description: 'Active' }]);
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('ACT - Active');
    });
    expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('DPG - Prince George');
  });

  it('still submits the org unit number, not its code', async () => {
    // Only the label gained the code. The value stays the number CROSSING_SITE.ORG_UNIT_NO holds —
    // a code is not unique across the org hierarchy, so submitting one would be ambiguous.
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
    ]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('DPG - Prince George');
    });

    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '18' } });
    await searchAndWait();

    expect(siteSearchApi.searchSites).toHaveBeenLastCalledWith(
      expect.objectContaining({ orgUnit: '18' }),
      0,
      20,
    );
  });

  it('falls back to whichever half exists when reference data is incomplete', async () => {
    api.getSiteTypeCodes.mockResolvedValue([{ code: 'CRS', description: null }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteTypeCode')).toHaveTextContent('CRS');
    });
    expect(screen.getByTestId('site-search-siteTypeCode')).not.toHaveTextContent('CRS -');
  });

  it('fills every parameterless list from its own lookup', async () => {
    // Six lists, six near-identical wirings: crossing two of them produces a form that renders
    // fine and filters on the wrong column. Distinct values per list are what catch that.
    api.getStructureInspectionStatusCodes.mockResolvedValue([
      { code: 'INS', description: 'From the inspection lookup' },
    ]);
    api.getSpecialAccessCodes.mockResolvedValue([
      { code: 'ACC', description: 'From the access lookup' },
    ]);
    api.getSiteTypeCodes.mockResolvedValue([{ code: 'TYP', description: 'From the type lookup' }]);
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'From the district lookup' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-structureInspectionStatusCode')).toHaveTextContent(
        'From the inspection lookup',
      );
    });
    expect(screen.getByTestId('site-search-specialAccessCode')).toHaveTextContent(
      'From the access lookup',
    );
    expect(screen.getByTestId('site-search-siteTypeCode')).toHaveTextContent(
      'From the type lookup',
    );
    expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('From the district lookup');
  });

  it('leaves Management Area empty until a district is chosen', async () => {
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('Prince George');
    });
    expect(api.getManagementAreas).not.toHaveBeenCalled();
  });

  it('loads the management areas of the district that was picked', async () => {
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
    ]);
    api.getManagementAreas.mockResolvedValue([
      { orgUnitNo: '26', orgUnitCode: 'DRV', orgUnitName: 'Robson Valley' },
    ]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('Prince George');
    });

    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '18' } });

    await waitFor(() => {
      expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveTextContent(
        'Robson Valley',
      );
    });
    expect(api.getManagementAreas).toHaveBeenCalledWith('18');
  });

  it('clears the chosen management area when the district changes', async () => {
    // The areas belong to the old district. Keeping the selection would submit a filter that is
    // not in the list the user can now see — and they would have no way to tell it was applied.
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
      { orgUnitNo: '21', orgUnitCode: 'DKA', orgUnitName: 'Thompson Rivers' },
    ]);
    api.getManagementAreas.mockResolvedValue([
      { orgUnitNo: '26', orgUnitCode: 'DRV', orgUnitName: 'Robson Valley' },
    ]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('Prince George');
    });
    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '18' } });
    await waitFor(() => {
      expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveTextContent(
        'Robson Valley',
      );
    });
    fireEvent.change(screen.getByTestId('site-search-managementOrgUnit'), {
      target: { value: '26' },
    });
    expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveValue('26');

    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '21' } });

    expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveValue('');
  });
});

describe('SiteSearchPage — the Status list comes from the server', () => {
  it('fills the Status select with what the endpoint returned', async () => {
    api.getSiteStatusCodes.mockResolvedValue(statuses);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('Active');
    });
    expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('Decommissioned');
  });

  it('keeps the display order the server sent, rather than sorting', async () => {
    // CROSSING_SITE_STATUS_XREF.DISPLAY_ORDER exists for no other purpose; re-sorting anywhere in
    // the chain silently throws it away, and alphabetical order looks plausible enough to pass
    // review.
    api.getSiteStatusCodes.mockResolvedValue([
      { code: 'Z', description: 'Zed, shown first' },
      { code: 'A', description: 'Ay, shown second' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent(
        'Zed, shown first',
      );
    });
    const options = Array.from(
      screen.getByTestId('site-search-siteStatusCode').querySelectorAll('option'),
    ).map((option) => option.value);
    expect(options).toEqual(['', 'Z', 'A']);
  });

  it('keeps the blank first option, which means "do not filter on status"', async () => {
    api.getSiteStatusCodes.mockResolvedValue(statuses);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('Active');
    });
    expect(screen.getByTestId('site-search-siteStatusCode')).toHaveValue('');
  });

  it('still lets the user search when the lookup fails', async () => {
    // Every criterion is optional, so a missing Status list is a lost filter, not a lost page.
    // Blocking the form on it would turn a reference-data outage into an outage of the screen.
    api.getSiteStatusCodes.mockRejectedValue(new Error('500'));
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-codes-error')).toBeInTheDocument();
    });
    expect(screen.getByText('Some filters could not be loaded')).toBeInTheDocument();
    await searchAndWait();
  });
});

describe('SiteSearchPage — backend status', () => {
  it('shows no standing notice when everything is working', () => {
    // There was a "Search is not connected yet" notice here while the search was a stub. It is
    // gone, and this test is what stops it — or anything like it — coming back unnoticed: a banner
    // that outlives the condition it described tells users their results are fake.
    renderPage();

    expect(screen.queryByTestId('site-search-codes-error')).not.toBeInTheDocument();
    expect(screen.queryByText(/not connected/i)).not.toBeInTheDocument();
  });

  it('links a site number to its detail page', async () => {
    siteSearchApi.searchSites.mockResolvedValue({
      ...emptyPage(),
      content: [site('SITE-1')],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();

    await searchAndWait();

    expect(screen.getByRole('link', { name: 'SITE-1' })).toHaveAttribute(
      'href',
      '/inventory/site/SITE-1',
    );
  });
});

describe('SiteSearchPage — deleting a site', () => {
  const oneResult = () => ({
    ...emptyPage(),
    content: [site('SITE-1')],
    totalElements: 1,
    totalPages: 1,
  });

  /** Searches, then opens the confirmation for the single result. */
  const openDeleteConfirmation = async () => {
    siteSearchApi.searchSites.mockResolvedValue(oneResult());
    renderPage(true);
    await searchAndWait();

    fireEvent.click(screen.getByTestId('site-delete-SITE-1'));
    await screen.findByText('Delete site');
  };

  it('asks before deleting, and says it cannot be undone', async () => {
    // A hard delete with no history row behind it. The warning is the only thing standing between
    // a mis-click and unrecoverable data.
    await openDeleteConfirmation();

    expect(screen.getByText(/cannot be undone/i)).toBeInTheDocument();
    expect(siteSearchApi.deleteSite).not.toHaveBeenCalled();
  });

  it('deletes nothing when the confirmation is dismissed', async () => {
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Cancel'));

    expect(siteSearchApi.deleteSite).not.toHaveBeenCalled();
  });

  it('deletes the site that was chosen', async () => {
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(siteSearchApi.deleteSite).toHaveBeenCalledWith('SITE-1');
    });
  });

  it('re-runs the search afterwards rather than dropping the row locally', async () => {
    // The deleted site changes the total and therefore the paging. A page that removes a row
    // without re-counting shows "20 matches" above nineteen rows.
    await openDeleteConfirmation();
    const searchesBefore = siteSearchApi.searchSites.mock.calls.length;

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(siteSearchApi.searchSites.mock.calls.length).toBeGreaterThan(searchesBefore);
    });
  });

  it('shows the server’s reason when the delete is refused', async () => {
    // The 409 detail is the only place the user learns that an archived structure is in the way —
    // the results table shows no structures at all.
    siteSearchApi.deleteSite.mockRejectedValue({
      body: { detail: 'Site SITE-1 has 2 associated archived structure(s) and cannot be deleted.' },
    });
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'error',
          title: 'The site was not deleted',
          subtitle: 'Site SITE-1 has 2 associated archived structure(s) and cannot be deleted.',
        }),
      );
    });
  });

  it('reports a failed delete as a toast, not as a banner on the page', async () => {
    // The action failed, so nothing on screen changed and there is no region to replace — the row
    // is still in the table. nr-frep and nr-fspts both report an action failure this way and keep
    // in-place notifications for a region that could not load.
    siteSearchApi.deleteSite.mockRejectedValue(new Error('network'));
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => expect(display).toHaveBeenCalled());
    expect(screen.queryByTestId('site-delete-error')).toBeNull();
  });

  it('falls back to its own wording when the failure carries no explanation', async () => {
    // An ApiError's own message is the bare status phrase — "Conflict" — which tells the user
    // nothing. A sentence naming the site is worth more.
    siteSearchApi.deleteSite.mockRejectedValue({ body: null });
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ subtitle: expect.stringMatching(/could not be deleted/i) }),
      );
    });
  });

  it('confirms a delete that worked, since the row simply vanishes otherwise', async () => {
    siteSearchApi.deleteSite.mockResolvedValue(undefined);
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Site SITE-1 deleted' }),
      );
    });
  });

  it('offers no delete at all to a user without the capability', async () => {
    // Legacy wraps the column in <cbr:authorize grantedAction="/deleteSite">. The backend refuses
    // as well; this is the half that stops the action being offered in the first place.
    siteSearchApi.searchSites.mockResolvedValue(oneResult());
    renderPage(false);
    await searchAndWait();

    expect(screen.queryByTestId('site-delete-SITE-1')).not.toBeInTheDocument();
  });
});

describe('SiteSearchPage — Designated Maintainer', () => {
  const canfor = {
    clientNumber: '00001012',
    clientLocnCode: '00',
    clientName: 'CANFOR CORPORATION',
    clientLocnName: null,
    city: 'Vancouver',
  };

  const maintainerField = () => screen.getByRole('combobox', { name: 'Designated Maintainer' });

  /** The criteria the page sent on the most recent search. */
  const sentCriteria = () => siteSearchApi.searchSites.mock.calls.at(-1)?.[0];

  it('does not look anything up until the term could narrow something', async () => {
    // Two letters match a large share of the client table, so the request is pure cost. The
    // backend applies the same floor; this stops the round trip happening at all.
    renderPage();

    await userEvent.fill(maintainerField(), 'ca');

    await waitFor(() => {
      expect(clientApi.searchClients).not.toHaveBeenCalled();
    });
  });

  it('looks up a term once the user stops typing, not once per keystroke', async () => {
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');

    await waitFor(() => {
      expect(clientApi.searchClients).toHaveBeenCalledWith('canfor');
    });
    expect(clientApi.searchClients).toHaveBeenCalledTimes(1);
  });

  it('searches on the client number and location code once a suggestion is picked', async () => {
    // The pair is what CROSSING_SITE records and what CRS_CL_FK1 constrains, so a pick filters on
    // both halves — which is exactly what the legacy lookup popup wrote back into the form.
    clientApi.searchClients.mockResolvedValue([canfor]);
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');
    await userEvent.click(await screen.findByText(/CANFOR CORPORATION/));
    await searchAndWait();

    expect(sentCriteria()).toMatchObject({
      clientNumber: '00001012',
      clientLocationCode: '00',
      // A pick is the precise form of the filter, so the name it replaces must not still apply.
      primaryUserName: '',
    });
  });

  it('still searches on the name when the term matches no suggestion', async () => {
    // What the field did before the lookup existed, and what a term like "canfor" needs — several
    // distinct clients carry that name and no single pick covers them. nr-frep refuses the search
    // here instead; CBR does not have to, because the backend has accepted a name all along.
    clientApi.searchClients.mockResolvedValue([]);
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');
    await searchAndWait();

    expect(sentCriteria()).toMatchObject({
      primaryUserName: 'canfor',
      clientNumber: '',
      clientLocationCode: '',
    });
  });

  it('clears the field on Reset, text and all', async () => {
    // Carbon holds `allowCustomValue` text in its own state, so clearing the criteria is not
    // enough on its own — the component is remounted. Without that the name stays on screen
    // looking like a filter that is no longer applied.
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');
    fireEvent.click(screen.getByTestId('site-search-reset'));

    await waitFor(() => {
      expect(maintainerField()).toHaveValue('');
    });
  });
});
