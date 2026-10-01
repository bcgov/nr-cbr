import { describe, expect, it, vi } from 'vitest';

import { StructureSearchService } from './structureSearch.service';

import type { APIConfig } from '@/config/api/types';
import type { StructureSearchCriteria } from '@/pages/StructureSearch/types';

import { EMPTY_CRITERIA } from '@/pages/StructureSearch/types';

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
  const service = new StructureSearchService(config);
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

const criteria = (overrides: Partial<StructureSearchCriteria> = {}): StructureSearchCriteria => ({
  ...EMPTY_CRITERIA,
  ...overrides,
});

describe('StructureSearchService', () => {
  it('calls the search endpoint with paging', async () => {
    const { service, request } = withMockedRequest();

    await service.searchStructures(criteria(), 2, 50);

    expect(urlOf(request)).toContain('/api/v1/structures/search?');
    expect(urlOf(request)).toContain('pageNumber=2');
    expect(urlOf(request)).toContain('pageSize=50');
  });

  it('sends only the criteria the user set, trimmed', async () => {
    const { service, request } = withMockedRequest();

    await service.searchStructures(criteria({ structureName: ' B100 ' }), 0, 20);

    expect(urlOf(request)).toContain('structureName=B100&');
    expect(urlOf(request)).not.toContain('crossingName=');
  });

  it('sends a toggle only when it is on', async () => {
    // Each is a one-way filter: off means no filter, so `false` and absent mean the same thing.
    const { service, request } = withMockedRequest();

    await service.searchStructures(criteria({ includeArchived: true }), 0, 20);

    expect(urlOf(request)).toContain('includeArchived=true');
    expect(urlOf(request)).not.toContain('downrated');
    expect(urlOf(request)).not.toContain('portableStructure');
  });

  it('never puts the maintainer label on the wire', async () => {
    const { service, request } = withMockedRequest();

    await service.searchStructures(
      criteria({ maintainerLabel: 'CANFOR CORPORATION', clientNumber: '00001012' }),
      0,
      20,
    );

    expect(urlOf(request)).not.toContain('maintainerLabel');
    expect(urlOf(request)).toContain('clientNumber=00001012');
  });

  it('sends the sort only when a header was chosen', async () => {
    const { service, request } = withMockedRequest();

    await service.searchStructures(criteria(), 0, 20, { column: 'MAINTAINER', direction: 'DESC' });

    expect(urlOf(request)).toContain('sortBy=MAINTAINER');
    expect(urlOf(request)).toContain('sortDirection=DESC');
  });

  it('leaves the sort off for legacy order', async () => {
    const { service, request } = withMockedRequest();

    await service.searchStructures(criteria(), 0, 20);

    expect(urlOf(request)).not.toContain('sortBy');
  });
});
