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

  it('archives with one PUT, carrying the ids as numbers', async () => {
    const { service, request } = withMockedRequest();

    await service.archiveStructures(['12', '345']);

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PUT',
        url: '/api/v1/structures/archive',
        data: { structureIds: [12, 345] },
      }),
    );
  });

  it('deletes one structure by id', async () => {
    const { service, request } = withMockedRequest();

    await service.deleteStructure('12');

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'DELETE', url: '/api/v1/structures/12' }),
    );
  });

  it('reads one structure by id', async () => {
    const { service, request } = withMockedRequest();

    await service.getStructure('7');

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/v1/structures/7' }),
    );
  });

  it("reads a structure's spans and piers", async () => {
    const { service, request } = withMockedRequest();

    await service.getSpansAndPiers('7');

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/v1/structures/7/spans-and-piers' }),
    );
  });

  it("reads a structure's documents", async () => {
    const { service, request } = withMockedRequest();

    await service.getDocuments('7');

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/v1/structures/7/documents' }),
    );
  });

  it("reads one document's file as a blob", async () => {
    const { service, request } = withMockedRequest();

    await service.getDocumentFile('7', '12');

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'GET',
        url: '/api/v1/structures/7/documents/12/file',
        responseType: 'blob',
      }),
    );
  });

  it("reads a structure's inspection schedule", async () => {
    const { service, request } = withMockedRequest();

    await service.getInspectionSchedule('7');

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/v1/structures/7/inspection-schedule' }),
    );
  });

  it("reads a page of a structure's inspections", async () => {
    const { service, request } = withMockedRequest();

    await service.getStructureInspections('7', 1, 10, true);

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'GET',
        url: '/api/v1/structures/7/inspections?pageNumber=1&pageSize=10&includeBeforeInstall=true',
      }),
    );
  });

  it("reads a page of a structure's repairs", async () => {
    const { service, request } = withMockedRequest();

    await service.getStructureRepairs('7', 'ALL', 2, 10);

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'GET',
        url: '/api/v1/structures/7/repairs?view=ALL&pageNumber=2&pageSize=10',
      }),
    );
  });

  it("reads a page of a structure's monitoring items", async () => {
    const { service, request } = withMockedRequest();

    await service.getStructureMonitors('7', 'OUTSTANDING', 0, 10);

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'GET',
        url: '/api/v1/structures/7/monitors?view=OUTSTANDING&pageNumber=0&pageSize=10',
      }),
    );
  });
});
