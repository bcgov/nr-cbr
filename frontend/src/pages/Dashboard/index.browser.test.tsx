import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import DashboardPage from './index';

import { LANDING_AFTER_LOGIN } from '@/constants/routes';

const bulletinApi = vi.hoisted(() => ({ bulletin: vi.fn() }));

vi.mock('@/services/APIs', () => ({ default: { bulletin: bulletinApi } }));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const renderPage = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  bulletinApi.bulletin.mockReset();
});

describe('DashboardPage', () => {
  it('is where a signed-in user lands', () => {
    expect(LANDING_AFTER_LOGIN).toBe('/dashboard');
  });

  it("shows the administrator's bulletin, as HTML", async () => {
    bulletinApi.bulletin.mockResolvedValue({
      message: 'Road <b>closed</b>',
      updatedBy: 'IDIR\\ADMIN',
      updatedAt: '2026-10-20T09:30:00',
    });
    renderPage();

    expect((await screen.findByTestId('dashboard-bulletin-message')).innerHTML).toBe(
      'Road <b>closed</b>',
    );
    expect(screen.getByRole('heading', { name: 'Bulletin' })).toBeInTheDocument();
    expect(screen.queryByTestId('dashboard-no-bulletin')).not.toBeInTheDocument();
  });

  it('says there is no bulletin, under the heading, when none is set', async () => {
    bulletinApi.bulletin.mockResolvedValue({ message: '', updatedBy: null, updatedAt: null });
    renderPage();

    expect(await screen.findByTestId('dashboard-no-bulletin')).toHaveTextContent(
      'There is no bulletin at the moment.',
    );
    expect(screen.getByRole('heading', { name: 'Bulletin' })).toBeInTheDocument();
    expect(screen.queryByTestId('dashboard-bulletin-message')).not.toBeInTheDocument();
  });

  it('shows neither when the bulletin could not be read: it is a notice, and stops nothing', async () => {
    bulletinApi.bulletin.mockRejectedValue(new Error('down'));
    renderPage();

    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByTestId('dashboard-bulletin')).not.toBeInTheDocument();
    expect(screen.queryByTestId('dashboard-no-bulletin')).not.toBeInTheDocument();
  });
});
