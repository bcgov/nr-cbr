import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { EMPTY_SITE, SITE_TYPE } from './types';

import SiteForm from './index';

// The client lookup inside the form reaches for the API; nothing here exercises it.
vi.mock('@/services/APIs', () => ({ default: { client: { searchClients: vi.fn() } } }));

const codeTables = {
  siteStatusCodes: [],
  siteTypeCodes: [],
  structureInspectionStatusCodes: [],
  specialAccessCodes: [],
  forestDistricts: [],
  recreationDistricts: [],
  managementAreas: [],
  businessAreas: [],
};

const renderForm = (crossingSiteTypeCode: string) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SiteForm
        values={{ ...EMPTY_SITE, crossingSiteTypeCode }}
        errors={{}}
        codeTables={codeTables}
        onChange={() => {}}
        onSettle={() => {}}
        onSave={() => {}}
        onFindRoad={() => {}}
      />
    </QueryClientProvider>,
  );

describe('SiteForm — Management Area', () => {
  it('is on the form for a crossing', () => {
    renderForm(SITE_TYPE.CROSSING);

    expect(screen.getByText('Management Area')).toBeInTheDocument();
  });

  it('is absent on a recreation site, as it is on the legacy form', () => {
    // `site.jsp` wraps the whole row in `<c:if test="${... != 'REC'}">`. A management area is a
    // former forest district, and a recreation site records a recreation district — so the list is
    // empty by construction and the field has nothing to offer.
    renderForm(SITE_TYPE.RECREATION);

    expect(screen.queryByText('Management Area')).not.toBeInTheDocument();
  });
});
