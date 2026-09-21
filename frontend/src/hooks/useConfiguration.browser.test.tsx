import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getStructureInspectionStatusCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getStructureTypeClassCodes: vi.fn(),
  getInspectionTypeCodes: vi.fn(),
  getInspectionReportStatusCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api },
}));

import {
  useManagementAreas,
  usePrefetchConfiguration,
  useSiteStatusCodes,
} from './useConfiguration';

import type { FC, ReactNode } from 'react';

const wrapper = (): FC<{ children: ReactNode }> => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

const Prefetcher: FC<{ enabled: boolean }> = ({ enabled }) => {
  usePrefetchConfiguration(enabled);
  return <span>prefetcher</span>;
};

const Reader: FC = () => {
  const { data } = useSiteStatusCodes();
  return <span data-testid="reader">{(data ?? []).map((option) => option.code).join(',')}</span>;
};

const AreaReader: FC<{ district: string }> = ({ district }) => {
  const { data } = useManagementAreas(district);
  return (
    <span data-testid="areas">{(data ?? []).map((option) => option.orgUnitNo).join(',')}</span>
  );
};

/**
 * Every parameterless lookup, in the order usePrefetchConfiguration warms them.
 *
 * <p>Kept exhaustive on purpose. A lookup added to the hook but not to this list is warmed with no
 * test watching, and one added to neither fails nothing — the prefetch swallows its own errors by
 * design, so calling a method the service does not have looks exactly like a lookup that was never
 * wired.
 */
const PARAMETERLESS = [
  api.getSiteStatusCodes,
  api.getStructureInspectionStatusCodes,
  api.getSpecialAccessCodes,
  api.getSiteTypeCodes,
  api.getForestDistricts,
  api.getStructureTypeClassCodes,
  api.getInspectionTypeCodes,
  api.getInspectionReportStatusCodes,
  api.getBusinessAreas,
];

beforeEach(() => {
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  api.getSiteStatusCodes.mockResolvedValue([{ code: 'ACT', description: 'Active' }]);
});

describe('usePrefetchConfiguration', () => {
  it('fetches nothing while there is no session', async () => {
    // Not merely wasteful: these endpoints require authentication, and an unauthenticated call
    // signs the user out and hard-redirects to the root — which would remount this and fire again.
    render(<Prefetcher enabled={false} />, { wrapper: wrapper() });

    await screen.findByText('prefetcher');
    PARAMETERLESS.forEach((fn) => expect(fn).not.toHaveBeenCalled());
  });

  it('warms every parameterless lookup once a session exists', async () => {
    render(<Prefetcher enabled />, { wrapper: wrapper() });

    await waitFor(() => {
      PARAMETERLESS.forEach((fn) => expect(fn).toHaveBeenCalledTimes(1));
    });
  });

  it('does not warm management areas, which need a district', async () => {
    // There are roughly forty districts and the user will pick one. Warming them all would turn
    // one idle request into forty to save a round trip on one.
    render(<Prefetcher enabled />, { wrapper: wrapper() });

    await waitFor(() => {
      expect(api.getForestDistricts).toHaveBeenCalledTimes(1);
    });
    expect(api.getManagementAreas).not.toHaveBeenCalled();
  });

  it('serves a later read from the warmed cache instead of fetching again', async () => {
    // The point of the prefetch. A drifted query key or staleTime would still pass the test above
    // and fail here, with the screen quietly fetching for itself.
    const Wrapper = wrapper();
    render(
      <Wrapper>
        <Prefetcher enabled />
      </Wrapper>,
    );
    await waitFor(() => {
      expect(api.getSiteStatusCodes).toHaveBeenCalledTimes(1);
    });

    render(
      <Wrapper>
        <Reader />
      </Wrapper>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('reader')).toHaveTextContent('ACT');
    });
    expect(api.getSiteStatusCodes).toHaveBeenCalledTimes(1);
  });
});

describe('useManagementAreas', () => {
  it('asks for nothing until a district is chosen', async () => {
    render(<AreaReader district="" />, { wrapper: wrapper() });

    await screen.findByTestId('areas');
    expect(api.getManagementAreas).not.toHaveBeenCalled();
  });

  it('fetches the areas for the district it was given', async () => {
    api.getManagementAreas.mockResolvedValue([{ orgUnitNo: '26', orgUnitName: 'Robson Valley' }]);

    render(<AreaReader district="18" />, { wrapper: wrapper() });

    await waitFor(() => {
      expect(screen.getByTestId('areas')).toHaveTextContent('26');
    });
    expect(api.getManagementAreas).toHaveBeenCalledWith('18');
  });

  it('caches each district separately', async () => {
    // One shared key would serve the first district's areas to every other district — and it would
    // look right, because the list is plausible for any of them.
    const Wrapper = wrapper();
    api.getManagementAreas.mockResolvedValue([{ orgUnitNo: '26', orgUnitName: 'Robson Valley' }]);
    render(
      <Wrapper>
        <AreaReader district="18" />
      </Wrapper>,
    );
    await waitFor(() => {
      expect(api.getManagementAreas).toHaveBeenCalledTimes(1);
    });

    render(
      <Wrapper>
        <AreaReader district="21" />
      </Wrapper>,
    );

    await waitFor(() => {
      expect(api.getManagementAreas).toHaveBeenCalledTimes(2);
    });
    expect(api.getManagementAreas).toHaveBeenLastCalledWith('21');
  });
});
