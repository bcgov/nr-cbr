import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

const authorization = vi.hoisted(() => ({ canDelete: false }));

vi.mock('@/hooks/useAuthorization', () => ({
  useAuthorization: () => authorization,
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

import SiteSearchPage from './index';

const renderPage = (canDelete = false) => {
  authorization.canDelete = canDelete;
  return render(
    <MemoryRouter>
      <SiteSearchPage />
    </MemoryRouter>,
  );
};

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

  it('shows the table, empty, once a search has been run', () => {
    renderPage();

    search();

    expect(screen.getByTestId('site-search-results')).toBeInTheDocument();
    expect(screen.getByText('No sites found.')).toBeInTheDocument();
  });

  it('renders the legacy columns in the legacy order', () => {
    renderPage();
    search();

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
  it('is absent for a user without the destructive capability', () => {
    // Legacy wraps this column in <cbr:authorize grantedAction="/deleteSite">. An action you cannot
    // perform is never rendered — not disabled (cbr-navigation.local.md §2).
    renderPage(false);
    search();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).not.toContain('Delete');
  });

  it('is present for CBR_LEVEL_2 and above', () => {
    renderPage(true);
    search();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).toContain('Delete');
  });
});

describe('SiteSearchPage — backend status', () => {
  it('says plainly that nothing is wired yet', () => {
    // The dropdowns are empty because the code tables have never been extracted. Without this the
    // page looks broken rather than unfinished.
    renderPage();

    expect(screen.getByText('Not connected yet')).toBeInTheDocument();
  });
});
