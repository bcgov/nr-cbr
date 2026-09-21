import { describe, expect, it, vi } from 'vitest';

import { InspectionSearchService } from './inspectionSearch.service';

import type { APIConfig } from '@/config/api/types';
import type { InspectionSearchCriteria } from '@/pages/InspectionSearch/types';

import { EMPTY_CRITERIA } from '@/pages/InspectionSearch/types';

const config: APIConfig = {
  BASE: '/api',
  VERSION: '0',
  WITH_CREDENTIALS: false,
  CREDENTIALS: 'omit',
  TOKEN: undefined,
  USERNAME: undefined,
  PASSWORD: undefined,
  HEADERS: undefined,
  ENCODE_PATH: undefined,
};

const withMockedRequest = () => {
  const service = new InspectionSearchService(config);
  const request = vi.fn().mockResolvedValue({
    data: { content: [], totalElements: 0, totalPages: 0, pageNumber: 0, pageSize: 20 },
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {},
  });
  service.axiosInstance.request = request;
  return { service, request };
};

const criteria = (overrides: Partial<InspectionSearchCriteria> = {}): InspectionSearchCriteria => ({
  ...EMPTY_CRITERIA,
  ...overrides,
});

/** The query is serialised into the URL by `doRequest`, so that is where these read it. */
const urlOf = (request: ReturnType<typeof vi.fn>) => String(request.mock.calls[0][0].url);

describe('InspectionSearchService', () => {
  it('calls the search endpoint', async () => {
    // The path is the contract with the backend's @GetMapping and nothing else checks it: a typo is
    // a 404 the page turns into "the search could not be run".
    const { service, request } = withMockedRequest();

    await service.searchInspections(criteria({ siteId: '12345' }), 0, 20);

    expect(urlOf(request)).toContain('/api/v1/inspections/search?');
    expect(request).toHaveBeenCalledWith(expect.objectContaining({ method: 'GET' }));
  });

  it('sends only the criteria the user set', async () => {
    // Nineteen criteria, most of them blank on any real search. Sending them all makes the request
    // unreadable in a network tab, which is where this gets debugged.
    const { service, request } = withMockedRequest();

    await service.searchInspections(criteria({ siteId: '12345', inspectorName: 'Smith' }), 0, 20);

    expect(urlOf(request)).toContain('siteId=12345');
    expect(urlOf(request)).toContain('inspectorName=Smith');
    expect(urlOf(request)).not.toContain('structureName=');
    expect(urlOf(request)).not.toContain('forestFileId=');
  });

  it('trims a pasted criterion', async () => {
    const { service, request } = withMockedRequest();

    await service.searchInspections(criteria({ siteId: '  12345  ' }), 0, 20);

    expect(urlOf(request)).toContain('siteId=12345');
  });

  it('sends a toggle only when it is on', async () => {
    // These are one-way filters: off means "do not filter", not "the ones that are not". Sending
    // false would suggest the server should do something with it.
    const { service, request } = withMockedRequest();

    await service.searchInspections(
      criteria({ closeProximity: true, mostRecentInspections: false }),
      0,
      20,
    );

    expect(urlOf(request)).toContain('closeProximity=true');
    expect(urlOf(request)).not.toContain('mostRecentInspections');
  });

  it('always sends sortBy, which is not a criterion but is what the server orders by', async () => {
    // Omitting it falls through to the legacy default ordering — district, road, section, km —
    // which is not what the form asked for and not what the user would see in the table.
    const { service, request } = withMockedRequest();

    await service.searchInspections(criteria({ siteId: '12345' }), 0, 20);

    expect(urlOf(request)).toContain('sortBy=structureIdDateSort');
  });

  it('passes the page and size through as given', async () => {
    const { service, request } = withMockedRequest();

    await service.searchInspections(criteria({ siteId: '12345' }), 3, 50);

    expect(urlOf(request)).toContain('pageNumber=3');
    expect(urlOf(request)).toContain('pageSize=50');
  });
});
