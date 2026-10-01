import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { userEvent } from '@vitest/browser/context';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import StructureSearchPage from './index';

const api = vi.hoisted(() => ({
  getStructureTypeClassCodes: vi.fn(),
  getSuperstructureTypeCodes: vi.fn(),
  getStructureCurbTypeCodes: vi.fn(),
  getCulvertTypeCodes: vi.fn(),
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getSpecialEquipmentCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getManagementAreas: vi.fn(),
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

// Mocked at the service rather than the hook, so the query key and the error branch are exercised.
const structureSearchApi = vi.hoisted(() => ({ searchStructures: vi.fn() }));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, structureSearch: structureSearchApi, client: clientApi },
}));

const renderPage = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <StructureSearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const emptyPage = (pageSize = 20) => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  pageNumber: 0,
  pageSize,
});

const structure = (id: string, overrides: Record<string, string> = {}) => ({
  id,
  structureName: `B${id}`,
  siteId: '12345',
  orgUnitCode: 'DPG',
  orgUnitName: 'Prince George Natural Resource District',
  forestServiceRoad: 'Bowron FSR',
  kilometres: '12.50',
  crossingName: 'Deadman Creek',
  structureTypeClass: 'PB - Permanent Bridge',
  forestFileId: 'R00123',
  roadSectionId: '01',
  clientNumber: '00001012',
  clientLocationCode: '01',
  clientName: 'CANFOR CORPORATION',
  ...overrides,
});

const search = () => fireEvent.click(screen.getByTestId('structure-search-submit'));

const searchAndWait = async () => {
  search();
  await waitFor(() => {
    expect(screen.getByTestId('structure-search-results')).toBeInTheDocument();
  });
};

/** The criteria the page sent on the most recent search. */
const sentCriteria = () => structureSearchApi.searchStructures.mock.calls.at(-1)?.[0];

beforeEach(() => {
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  structureSearchApi.searchStructures.mockReset();
  structureSearchApi.searchStructures.mockResolvedValue(emptyPage());
  clientApi.searchClients.mockReset();
  clientApi.searchClients.mockResolvedValue([]);
  clientApi.clientLocations.mockReset();
  clientApi.clientLocations.mockResolvedValue([]);
});

describe('StructureSearchPage — criteria form', () => {
  it('offers every criterion the legacy form does, bar User Kilometres', () => {
    renderPage();

    for (const field of [
      'structureName',
      'downrated',
      'structureTypeClassCode',
      'portableStructure',
      'superstructureTypeCode',
      'structureCurbTypeCode',
      'culvertTypeCode',
      'incomplete',
      'siteId',
      'siteStatusCode',
      'forestFileId',
      'roadSectionId',
      'siteTypeCode',
      'forestServiceRoad',
      'kiloStart',
      'kiloEnd',
      'crossingName',
      'orgUnit',
      'includeArchived',
      'managementOrgUnit',
      'maintainer',
      'clientLocationCode',
      'loadRestrictionYearStart',
      'loadRestrictionYearEnd',
      'replacementYearStart',
      'replacementYearEnd',
      'closureYearStart',
      'closureYearEnd',
      'yearBuiltStart',
      'yearBuiltEnd',
      'specialAccessCode',
      'specialEquipmentCode',
    ]) {
      expect(screen.getByTestId(`structure-search-${field}`)).toBeInTheDocument();
    }
    // Dropped from Site Search, so dropped here too; the backend still accepts it.
    expect(screen.queryByTestId('structure-search-userKmStart')).not.toBeInTheDocument();
  });

  it('is badged as under construction', () => {
    renderPage();

    expect(screen.getByText('Under construction')).toBeInTheDocument();
  });

  it('keeps the legacy labels', () => {
    renderPage();

    expect(screen.getByLabelText('Structure #')).toBeInTheDocument();
    expect(screen.getByLabelText('Type/Class')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Downrated Structure\?/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Portable Structure\?/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Incomplete Data\?/ })).toBeInTheDocument();
    expect(
      screen.getByRole('switch', { name: /Include Archived Structure\?/ }),
    ).toBeInTheDocument();
    expect(screen.getByText('Estimated Load Restriction')).toBeInTheDocument();
    expect(screen.getByText('Estimated Replacement')).toBeInTheDocument();
    expect(screen.getByText('Estimated Closure')).toBeInTheDocument();
    expect(screen.getByText('Year Superstructure Installed')).toBeInTheDocument();
  });

  it('leaves archived structures out by default', () => {
    renderPage();

    expect(screen.getByRole('switch', { name: /Include Archived Structure\?/ })).not.toBeChecked();
  });

  it('validates a year bound beside its box, and does not search', async () => {
    renderPage();

    fireEvent.change(screen.getByTestId('structure-search-closureYearStart'), {
      target: { value: '30' },
    });
    search();

    expect(await screen.findByText('Enter a year as yyyy, e.g. 2030')).toBeInTheDocument();
    expect(screen.getByTestId('structure-search-closureYearStart')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(screen.getByTestId('structure-search-closureYearEnd')).not.toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(structureSearchApi.searchStructures).not.toHaveBeenCalled();
  });

  it('validates a kilometre bound beside its box', async () => {
    renderPage();

    fireEvent.change(screen.getByTestId('structure-search-kiloEnd'), { target: { value: 'x' } });

    expect(await screen.findByText('Kilometres must be a number, e.g. 12.5')).toBeInTheDocument();
  });

  it('names each range box for a screen reader', () => {
    renderPage();

    expect(screen.getByLabelText('Estimated Closure From')).toBeInTheDocument();
    expect(screen.getByLabelText('Estimated Closure To')).toBeInTheDocument();
  });

  it('clears every criterion at once', () => {
    renderPage();
    const name = screen.getByTestId('structure-search-structureName');
    fireEvent.change(name, { target: { value: 'B100' } });
    const downrated = screen.getByRole('switch', { name: /Downrated Structure\?/ });
    fireEvent.click(downrated);

    fireEvent.click(screen.getByTestId('structure-search-reset'));

    expect(name).toHaveValue('');
    expect(downrated).not.toBeChecked();
  });
});

describe('StructureSearchPage — searching', () => {
  it('shows no results table until a search is run', () => {
    renderPage();

    expect(screen.queryByTestId('structure-search-results')).not.toBeInTheDocument();
  });

  it('does not search while the user is still typing', () => {
    renderPage();

    fireEvent.change(screen.getByTestId('structure-search-structureName'), {
      target: { value: 'B100' },
    });

    expect(structureSearchApi.searchStructures).not.toHaveBeenCalled();
  });

  it('sends the criteria the user submitted, toggles and years included', async () => {
    renderPage();
    fireEvent.change(screen.getByTestId('structure-search-structureName'), {
      target: { value: 'B100' },
    });
    fireEvent.click(screen.getByRole('switch', { name: /Downrated Structure\?/ }));
    fireEvent.click(screen.getByRole('switch', { name: /Include Archived Structure\?/ }));
    fireEvent.change(screen.getByTestId('structure-search-replacementYearStart'), {
      target: { value: '2030' },
    });

    await searchAndWait();

    expect(structureSearchApi.searchStructures).toHaveBeenCalledWith(
      expect.objectContaining({
        structureName: 'B100',
        downrated: true,
        includeArchived: true,
        replacementYearStart: '2030',
      }),
      0,
      20,
      null,
    );
  });

  it('shows the table, empty, once a search has been run', async () => {
    renderPage();

    await searchAndWait();

    expect(screen.getByText('No structures found.')).toBeInTheDocument();
  });

  it('says so when the search fails, rather than showing an empty table', async () => {
    structureSearchApi.searchStructures.mockRejectedValue(new Error('boom'));
    renderPage();

    search();

    expect(await screen.findByTestId('structure-search-error')).toBeInTheDocument();
    expect(screen.queryByText('No structures found.')).not.toBeInTheDocument();
  });

  it('asks for the next page zero-based, and goes back to page one on a new search', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('1')],
      totalElements: 45,
      totalPages: 3,
    });
    renderPage();
    await searchAndWait();

    fireEvent.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        20,
        null,
      );
    });

    search();
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        null,
      );
    });
  });
});

describe('StructureSearchPage — results', () => {
  it('renders the legacy columns, with one Maintainer column', async () => {
    renderPage();
    await searchAndWait();

    // A sortable header also carries text only a screen reader hears, so the visible label is read.
    const headers = within(screen.getByTestId('structure-search-results'))
      .getAllByRole('columnheader')
      .map(
        (cell) => cell.querySelector('.cds--table-header-label')?.textContent ?? cell.textContent,
      );
    expect(headers).toEqual([
      'Structure #',
      'Site #',
      'District Code',
      'Forest Service Road',
      'KM',
      'Crossing Name',
      'Type/Class',
      'Project File ID#-Br.',
      'Maintainer',
    ]);
  });

  it('renders a row as the server returned it', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('7')],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();
    await searchAndWait();

    const row = within(screen.getByTestId('structure-row-7'));
    expect(row.getByText('B7')).toBeInTheDocument();
    expect(row.getByText('DPG — Prince George Natural Resource District')).toBeInTheDocument();
    expect(row.getByText('PB - Permanent Bridge')).toBeInTheDocument();
    expect(row.getByText('R00123-01')).toBeInTheDocument();
    expect(row.getByText('00001012-01 CANFOR CORPORATION')).toBeInTheDocument();
    expect(screen.getByText('Structures — 1 match')).toBeInTheDocument();
  });

  it('links the structure and the site to their detail pages', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('7')],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();
    await searchAndWait();

    expect(screen.getByRole('link', { name: '12345' })).toHaveAttribute(
      'href',
      '/inventory/site/12345',
    );
    // By id, not by name: CROSSING_STRUCTURE_ID is the key, and a name is not unique.
    expect(screen.getByRole('link', { name: 'B7' })).toHaveAttribute(
      'href',
      '/inventory/structure/7',
    );
  });

  it('leaves the maintainer cell empty for a site with no maintainer', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('7', { clientNumber: '', clientLocationCode: '', clientName: '' })],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();
    await searchAndWait();

    const cells = within(screen.getByTestId('structure-row-7')).getAllByRole('cell');
    expect(cells.at(-1)).toHaveTextContent(/^$/);
  });
});

describe('StructureSearchPage — the dropdowns come from the server', () => {
  it('fills the structure lists from their own lookups', async () => {
    api.getSuperstructureTypeCodes.mockResolvedValue([{ code: 'STL', description: 'Steel' }]);
    api.getStructureCurbTypeCodes.mockResolvedValue([{ code: 'TMB', description: 'Timber' }]);
    api.getCulvertTypeCodes.mockResolvedValue([{ code: 'CSP', description: 'Corrugated' }]);
    api.getSpecialEquipmentCodes.mockResolvedValue([{ code: 'CRN', description: 'Crane' }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('structure-search-superstructureTypeCode')).toHaveTextContent(
        'STL - Steel',
      );
    });
    expect(screen.getByTestId('structure-search-structureCurbTypeCode')).toHaveTextContent(
      'TMB - Timber',
    );
    expect(screen.getByTestId('structure-search-culvertTypeCode')).toHaveTextContent(
      'CSP - Corrugated',
    );
    expect(screen.getByTestId('structure-search-specialEquipmentCode')).toHaveTextContent(
      'CRN - Crane',
    );
  });

  it('says some filters are missing when a lookup fails, and still searches', async () => {
    api.getCulvertTypeCodes.mockRejectedValue(new Error('boom'));
    renderPage();

    expect(await screen.findByTestId('structure-search-codes-error')).toBeInTheDocument();
    await searchAndWait();
  });

  it('clears the chosen management area when the district changes', async () => {
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
      { orgUnitNo: '21', orgUnitCode: 'DKA', orgUnitName: 'Thompson Rivers' },
    ]);
    api.getManagementAreas.mockResolvedValue([
      { orgUnitNo: '26', orgUnitCode: 'DRV', orgUnitName: 'Robson Valley' },
    ]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('structure-search-orgUnit')).toHaveTextContent('Prince George');
    });
    fireEvent.change(screen.getByTestId('structure-search-orgUnit'), { target: { value: '18' } });
    await waitFor(() => {
      expect(screen.getByTestId('structure-search-managementOrgUnit')).toHaveTextContent(
        'Robson Valley',
      );
    });
    fireEvent.change(screen.getByTestId('structure-search-managementOrgUnit'), {
      target: { value: '26' },
    });

    fireEvent.change(screen.getByTestId('structure-search-orgUnit'), { target: { value: '21' } });

    expect(screen.getByTestId('structure-search-managementOrgUnit')).toHaveValue('');
  });
});

describe('StructureSearchPage — Designated Maintainer', () => {
  const canfor = {
    clientNumber: '00001012',
    clientLocnCode: '00',
    clientName: 'CANFOR CORPORATION',
    clientLocnName: null,
    city: 'Vancouver',
  };

  const maintainerField = () => screen.getByRole('combobox', { name: 'Designated Maintainer' });

  it('searches on the client number once a suggestion is picked', async () => {
    clientApi.searchClients.mockResolvedValue([canfor]);
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');
    await userEvent.click(await screen.findByText(/CANFOR CORPORATION/));
    await searchAndWait();

    expect(sentCriteria()).toMatchObject({ clientNumber: '00001012', primaryUserName: '' });
  });

  it('searches on the name when the text is not a pick', async () => {
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');
    await searchAndWait();

    expect(sentCriteria()).toMatchObject({ primaryUserName: 'canfor', clientNumber: '' });
  });
});

describe('StructureSearchPage — sorting by a header', () => {
  // Carbon puts a sortable header's own props on the button inside it, so the test id is the button.
  const sortBy = async (column: string) => {
    fireEvent.click(await screen.findByTestId(`structure-search-sort-${column}`));
    await screen.findByTestId('structure-search-results');
  };

  it('asks the server to sort every match, not the rows on screen', async () => {
    renderPage();
    await searchAndWait();

    await sortBy('TYPE_CLASS');

    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        { column: 'TYPE_CLASS', direction: 'ASC' },
      );
    });
  });

  it('turns descending on a second click and back to legacy order on a third', async () => {
    renderPage();
    await searchAndWait();

    await sortBy('STRUCTURE_NAME');
    await sortBy('STRUCTURE_NAME');
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        { column: 'STRUCTURE_NAME', direction: 'DESC' },
      );
    });

    await sortBy('STRUCTURE_NAME');
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        null,
      );
    });
  });

  it('goes back to page one when the order changes', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('1')],
      totalElements: 45,
      totalPages: 3,
    });
    renderPage();
    await searchAndWait();
    fireEvent.click(screen.getByLabelText('Next page'));
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        20,
        null,
      );
    });

    await sortBy('MAINTAINER');

    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        { column: 'MAINTAINER', direction: 'ASC' },
      );
    });
  });

  it('forgets the order on Reset', async () => {
    renderPage();
    await searchAndWait();
    await sortBy('KILOMETRES');

    fireEvent.click(screen.getByTestId('structure-search-reset'));
    await searchAndWait();

    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        null,
      );
    });
  });
});
