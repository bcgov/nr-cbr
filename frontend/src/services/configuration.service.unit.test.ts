import { describe, expect, it, vi } from 'vitest';

import { ConfigurationService } from './configuration.service';

import type { APIConfig } from '@/config/api/types';

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

const mockResponse = <T>(data: T) => ({
  data,
  status: 200,
  statusText: 'OK',
  headers: {},
  config: {},
});

const withMockedRequest = () => {
  const service = new ConfigurationService(config);
  const request = vi.fn().mockResolvedValue(mockResponse([]));
  service.axiosInstance.request = request;
  return { service, request };
};

describe('ConfigurationService', () => {
  // The paths are the contract with the backend's @GetMapping and nothing else checks them: a typo
  // is a 404 the page swallows into an empty dropdown. Each lookup is listed rather than looped
  // over a map, so the expected path is written out next to the method that must produce it.
  it.each([
    ['getSiteStatusCodes', 'site-status-codes'],
    ['getStructureInspectionStatusCodes', 'structure-inspection-status-codes'],
    ['getSpecialAccessCodes', 'special-access-codes'],
    ['getSiteTypeCodes', 'site-type-codes'],
    ['getForestDistricts', 'forest-districts'],
  ] as const)('calls the %s endpoint', async (method, path) => {
    const { service, request } = withMockedRequest();

    await service[method]();

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: `/api/v1/configuration/${path}`,
        method: 'GET',
      }),
    );
  });

  it('sends the selected district when asking for management areas', async () => {
    // Dropping the parameter would 400 rather than quietly widen the lookup — the backend makes it
    // required — but it would do so only once a district was chosen, which is late to find out.
    const { service, request } = withMockedRequest();

    await service.getManagementAreas('1809');

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: '/api/v1/configuration/management-areas?forestDistrictOrgUnitNo=1809',
        method: 'GET',
      }),
    );
  });

  it('returns the rows as the server ordered them', async () => {
    // Display order, not alphabetical — see CROSSING_SITE_STATUS_XREF.
    const service = new ConfigurationService(config);
    service.axiosInstance.request = vi.fn().mockResolvedValue(
      mockResponse([
        { code: 'Z', description: 'First by display order' },
        { code: 'A', description: 'Second by display order' },
      ]),
    );

    await expect(service.getSiteStatusCodes()).resolves.toEqual([
      { code: 'Z', description: 'First by display order' },
      { code: 'A', description: 'Second by display order' },
    ]);
  });
});
