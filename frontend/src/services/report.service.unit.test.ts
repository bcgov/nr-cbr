import { describe, expect, it, vi } from 'vitest';

import { ReportService } from './report.service';

import type { APIConfig } from '@/config/api/types';

import { EMPTY_CRITERIA as DETAILED_EMPTY } from '@/pages/Reports/StructureInventoryDetailed/types';
import { EMPTY_CRITERIA } from '@/pages/Reports/StructureInventorySummary/types';

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
  const service = new ReportService(config);
  const request = vi.fn().mockResolvedValue({
    data: new Blob(['%PDF']),
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {},
  });
  service.axiosInstance.request = request;
  return { service, request };
};

const urlOf = (request: ReturnType<typeof vi.fn>) => String(request.mock.calls[0][0].url);

describe('ReportService', () => {
  it('asks for the Structure Inventory – Summary in the chosen format, as a file', async () => {
    const { service, request } = withMockedRequest();

    await service.structureInventorySummary(EMPTY_CRITERIA, 'CSV');

    expect(urlOf(request)).toContain('/api/v1/reports/structure-inventory-summary?');
    expect(urlOf(request)).toContain('format=CSV');
    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', responseType: 'blob' }),
    );
  });

  it('sends only the criteria set, trimmed, a toggle only when on, never the maintainer label', async () => {
    const { service, request } = withMockedRequest();

    await service.structureInventorySummary(
      {
        ...EMPTY_CRITERIA,
        forestFileId: ' R00123 ',
        capitalRoad: true,
        clientNumber: '00001012',
        maintainerLabel: 'CANFOR CORPORATION (00001012)',
      },
      'PDF',
    );

    const url = urlOf(request);
    expect(url).toContain('forestFileId=R00123');
    expect(url).toContain('capitalRoad=true');
    expect(url).toContain('clientNumber=00001012');
    expect(url).toContain('sortBy=PROJECT_BRANCH_USER_KM');
    expect(url).not.toContain('downrated');
    expect(url).not.toContain('kiloStart');
    expect(url).not.toContain('maintainerLabel');
  });

  it('asks for the Structure Inventory – Detailed PDF with only the criteria set', async () => {
    const { service, request } = withMockedRequest();

    await service.structureInventoryDetailed(
      {
        ...DETAILED_EMPTY,
        yearBuiltStart: '1990',
        maintainerLabel: 'CANFOR CORPORATION (00001012)',
      },
      'CSV',
    );

    const url = urlOf(request);
    expect(url).toContain('/api/v1/reports/structure-inventory-detailed?');
    expect(url).toContain('yearBuiltStart=1990');
    expect(url).toContain('format=CSV');
    expect(url).not.toContain('maintainerLabel');
    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', responseType: 'blob' }),
    );
  });

  it('asks for the Structure Inventory – Detailed KMZ at its own address, without a format', async () => {
    const { service, request } = withMockedRequest();

    await service.structureInventoryDetailed({ ...DETAILED_EMPTY, yearBuiltStart: '1990' }, 'KMZ');

    const url = urlOf(request);
    expect(url).toContain('/api/v1/reports/structure-inventory-detailed/kmz?');
    expect(url).toContain('yearBuiltStart=1990');
    expect(url).not.toContain('format=');
  });
});
