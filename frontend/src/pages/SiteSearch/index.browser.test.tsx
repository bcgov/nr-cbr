import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

// Mocked at the service rather than at the hook, so the query key, the fallback to an empty list
// and the error branch are all exercised by these tests instead of being stubbed past.
const siteSearchApi = vi.hoisted(() => ({ searchSites: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, siteSearch: siteSearchApi },
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
      'clientNumber',
      'crossingName',
      'clientLocationCode',
      'orgUnit',
      'kiloStart',
      'kiloEnd',
      'managementOrgUnit',
      'userKmStart',
      'userKmEnd',
      'specialAccessCode',
      'siteTypeCode',
      'primaryUserName',
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
    expect(screen.getByLabelText('Designated Maintainer Client Number')).toBeInTheDocument();
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
    expect(screen.getByTestId('site-search-clientLocationCode')).toHaveAttribute('maxlength', '2');
    expect(screen.getByTestId('site-search-primaryUserName')).toHaveAttribute('maxlength', '35');
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
