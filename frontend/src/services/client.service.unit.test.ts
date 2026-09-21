import { describe, expect, it, vi } from 'vitest';

import { ClientService } from './client.service';

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

const withMockedRequest = () => {
  const service = new ClientService(config);
  const request = vi.fn().mockResolvedValue({
    data: [],
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {},
  });
  service.axiosInstance.request = request;
  return { service, request };
};

describe('ClientService', () => {
  it('asks the clients endpoint for a term', async () => {
    const { service, request } = withMockedRequest();

    await service.searchClients('canfor');

    const url = String(request.mock.calls[0][0].url);
    expect(url).toContain('/api/v1/clients');
    expect(url).toContain('term=canfor');
  });

  it('sends the term untouched, because the backend decides what it means', async () => {
    // All digits is a client number there, anything else is a name, a division or a city. Parsing
    // it here as well would be a second copy of that rule, free to drift from the first.
    const { service, request } = withMockedRequest();

    await service.searchClients('66838');

    expect(String(request.mock.calls[0][0].url)).toContain('term=66838');
  });
});
