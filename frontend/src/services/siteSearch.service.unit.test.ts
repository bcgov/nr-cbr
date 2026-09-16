import { describe, expect, it, vi } from 'vitest';

import { SiteSearchService } from './siteSearch.service';

import type { APIConfig } from '@/config/api/types';
import type { SiteSearchCriteria } from '@/pages/SiteSearch/types';

import { EMPTY_CRITERIA } from '@/pages/SiteSearch/types';

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
  const service = new SiteSearchService(config);
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

const urlOf = (request: ReturnType<typeof vi.fn>) => String(request.mock.calls[0][0].url);

const criteria = (overrides: Partial<SiteSearchCriteria> = {}): SiteSearchCriteria => ({
  ...EMPTY_CRITERIA,
  ...overrides,
});

describe('SiteSearchService', () => {
  it('calls the search endpoint with paging', async () => {
    const { service, request } = withMockedRequest();

    await service.searchSites(criteria(), 2, 50);

    expect(urlOf(request)).toContain('/api/v1/sites/search?');
    expect(urlOf(request)).toContain('pageNumber=2');
    expect(urlOf(request)).toContain('pageSize=50');
  });

  it('sends only the criteria the user set', async () => {
    // Seventeen empty parameters are harmless to the server — it treats a blank as unset — but they
    // make the URL unreadable in a log or a network tab, which is where this gets debugged.
    const { service, request } = withMockedRequest();

    await service.searchSites(criteria({ siteId: '12345' }), 0, 20);

    expect(urlOf(request)).toContain('siteId=12345');
    expect(urlOf(request)).not.toContain('crossingName=');
    expect(urlOf(request)).not.toContain('forestFileId=');
  });

  it('trims a criterion rather than searching for the spaces', async () => {
    const { service, request } = withMockedRequest();

    await service.searchSites(criteria({ crossingName: '  Deadman  ' }), 0, 20);

    expect(urlOf(request)).toContain('crossingName=Deadman');
  });

  it('treats a whitespace-only criterion as unset', async () => {
    const { service, request } = withMockedRequest();

    await service.searchSites(criteria({ crossingName: '   ' }), 0, 20);

    expect(urlOf(request)).not.toContain('crossingName');
  });

  it('sends a toggle only when it is on', async () => {
    // Both are one-way filters: on means "capital roads only", off means no filter rather than
    // "non-capital roads only". Sending false would suggest the server should exclude them.
    const { service, request } = withMockedRequest();

    await service.searchSites(criteria({ capitalRoad: true, incomplete: false }), 0, 20);

    expect(urlOf(request)).toContain('capitalRoad=true');
    expect(urlOf(request)).not.toContain('incomplete');
  });
});
