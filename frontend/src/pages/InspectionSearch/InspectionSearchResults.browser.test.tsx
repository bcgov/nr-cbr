import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import InspectionSearchResults from './InspectionSearchResults';

import type { InspectionSearchResult } from './types';

/**
 * The results table is tested directly rather than through the page, because the page has no search
 * to run yet — and because the two behaviours worth pinning here both turn on the status code,
 * which is a property of a row and not of the screen around it.
 */
const inspection = (overrides: Partial<InspectionSearchResult> = {}): InspectionSearchResult => ({
  id: '4001',
  inspectionDate: '2026-01-31',
  inspectionReportStatusCode: 'SUB',
  inspectionReportStatusDescription: 'Submitted',
  siteAtTimeOfInspection: 'S-00123',
  structureName: 'BR-4471',
  orgUnitCode: 'DPG',
  orgUnitName: 'Prince George Natural Resource District',
  forestServiceRoad: 'Bowron FSR',
  pointOfCommencementDistance: '12.50',
  crossingName: 'Deadman Creek',
  forestFileId: 'R00123',
  roadSectionId: '01',
  ...overrides,
});

const renderTable = (
  results: InspectionSearchResult[],
  { canDelete = false, onDelete = vi.fn(), loading = false } = {},
) => {
  render(
    <MemoryRouter>
      <InspectionSearchResults
        results={results}
        totalItems={results.length}
        loading={loading}
        page={1}
        pageSize={20}
        canDelete={canDelete}
        onPageChange={vi.fn()}
        onDelete={onDelete}
      />
    </MemoryRouter>,
  );
  return onDelete;
};

describe('InspectionSearchResults', () => {
  it('shows every column the legacy table does', () => {
    renderTable([inspection()]);

    for (const header of [
      'Id',
      'Inspection Date',
      'Status',
      'Site #',
      'Structure Name',
      'District Code',
      'Forest Service Road',
      'KM',
      'Crossing Name',
      'Project File ID#-Br.',
    ]) {
      expect(screen.getByRole('columnheader', { name: header })).toBeInTheDocument();
    }
  });

  it('prints the date as yyyy/MM/dd, as the legacy <fmt:formatDate> does', () => {
    // The column is read down as a sequence, and a fixed-width numeric date sorts by eye where a
    // month name does not.
    renderTable([inspection()]);

    expect(screen.getByText('2026/01/31')).toBeInTheDocument();
  });

  it('joins the district code to its name instead of hiding the name in a tooltip', () => {
    // Legacy's 6%-wide column forced a title attribute, which never reaches a user reading on a
    // touchscreen.
    renderTable([inspection()]);

    expect(screen.getByText('DPG — Prince George Natural Resource District')).toBeInTheDocument();
  });

  it('links an inspection that lives on the server', () => {
    renderTable([inspection()]);

    expect(screen.getByRole('link', { name: '4001' })).toHaveAttribute('href', '/inspection/4001');
  });

  it('does not link an offline inspection', () => {
    // It is checked out to a field device, so there is nothing on the server to open — legacy prints
    // the bare id for exactly this case.
    renderTable([inspection({ id: '4002', inspectionReportStatusCode: 'OFL' })]);

    expect(screen.queryByRole('link', { name: '4002' })).toBeNull();
    expect(screen.getByText('4002')).toBeInTheDocument();
  });

  it('links the site the structure sat at when the inspection happened', () => {
    // Not the structure's current site: a structure can move, which is what
    // "Include Inspections for Structures at Previous Sites?" exists to find.
    renderTable([inspection()]);

    expect(screen.getByRole('link', { name: 'S-00123' })).toHaveAttribute(
      'href',
      '/inventory/site/S-00123',
    );
  });

  it('offers delete only on an offline row, and only to a role that holds it', () => {
    renderTable(
      [inspection({ id: '4001' }), inspection({ id: '4002', inspectionReportStatusCode: 'OFL' })],
      {
        canDelete: true,
      },
    );

    expect(screen.queryByTestId('inspection-delete-4001')).toBeNull();
    expect(screen.getByTestId('inspection-delete-4002')).toBeInTheDocument();
  });

  it('hides the Actions column entirely from a role that cannot delete', () => {
    // The legacy rule: an action you cannot perform is never rendered, not disabled.
    renderTable([inspection({ id: '4002', inspectionReportStatusCode: 'OFL' })], {
      canDelete: false,
    });

    expect(screen.queryByRole('columnheader', { name: 'Actions' })).toBeNull();
    expect(screen.queryByTestId('inspection-delete-4002')).toBeNull();
  });

  it('hands the row back when delete is pressed, rather than deleting it', () => {
    // The confirmation belongs to the page — legacy confirms too, with a window.confirm().
    const onDelete = renderTable([inspection({ id: '4002', inspectionReportStatusCode: 'OFL' })], {
      canDelete: true,
    });

    fireEvent.click(screen.getByTestId('inspection-delete-4002'));

    expect(onDelete).toHaveBeenCalledWith(expect.objectContaining({ id: '4002' }));
  });

  it('uses the legacy wording when nothing matched', () => {
    renderTable([]);

    expect(
      screen.getByText(
        'No inspection record found that matches search criteria, please try again.',
      ),
    ).toBeInTheDocument();
  });

  it('shows a skeleton while a search is running, not an empty table', () => {
    // An empty table mid-search says the search finished and matched nothing, which is a different
    // answer and one the user would act on.
    renderTable([], { loading: true });

    expect(screen.getByTestId('inspection-search-loading')).toBeInTheDocument();
    expect(
      screen.queryByText(
        'No inspection record found that matches search criteria, please try again.',
      ),
    ).toBeNull();
  });

  it('counts the server total, not the rows on screen', () => {
    render(
      <MemoryRouter>
        <InspectionSearchResults
          results={[inspection()]}
          totalItems={2000}
          page={1}
          pageSize={20}
          canDelete={false}
          onPageChange={vi.fn()}
          onDelete={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('Inspections — 2000 matches')).toBeInTheDocument();
  });
});
