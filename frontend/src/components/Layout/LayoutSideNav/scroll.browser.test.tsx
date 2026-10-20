import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { page } from 'vitest/browser';

import { LayoutSideNav } from './index';

// The app's own stylesheet, Carbon included — the other side-nav tests load only the component's
// SCSS, and how the list sizes and scrolls is decided by the two together.
import '@/styles/index.scss';

vi.mock('@/env', () => ({ env: { VITE_SUPPORT_EMAIL: 'support@example.com' } }));
vi.mock('@/context/layout/useLayout', () => ({
  useLayout: () => ({ isSideNavExpanded: true, closeSideNav: () => {}, openSideNav: () => {} }),
}));
vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { roles: ['admin'] }, isLoggedIn: true }),
}));

/** A section longer than a short screen, as Reports is: eleven children, some wrapping. */
const LONG = Array.from({ length: 11 }, (_, i) => ({
  id: `Inspections Conducted – Report ${i + 1}`,
  path: `report-${i + 1}`,
}));
vi.mock('@/routes/routePaths', () => ({
  getOfflineMenuEntries: () => [],
  getMenuEntries: () => [
    { id: 'Inventory', path: '/inventory', children: [{ id: 'Site Search', path: 'site-search' }] },
    { id: 'Reports', path: '/reports', children: LONG },
  ],
}));

describe('LayoutSideNav, with the app styles', () => {
  /**
   * Regression. A section taller than the space left was squeezed to fit — the list is a flex
   * column, and Carbon's `__item` clips — so its last children were cut off and, the list never
   * outgrowing the nav, there was nothing to scroll to them.
   */
  it('scrolls a list longer than the nav to its last item, Support after it', async () => {
    await page.viewport(1280, 600);
    await act(async () =>
      render(
        <MemoryRouter initialEntries={['/reports/report-1']}>
          <LayoutSideNav />
        </MemoryRouter>,
      ),
    );

    const items = document.querySelector('.cds--side-nav__items') as HTMLElement;
    const last = screen.getByText(LONG[10].id).closest('li') as HTMLElement;
    const section = screen.getByTestId('side-nav-menu-Reports').closest('li') as HTMLElement;

    expect(items.scrollHeight).toBeGreaterThan(items.clientHeight);
    expect(section.scrollHeight).toBe(section.clientHeight);

    items.scrollTop = items.scrollHeight;
    const listBox = items.getBoundingClientRect();
    expect(last.getBoundingClientRect().bottom).toBeLessThanOrEqual(listBox.bottom);
    expect(screen.getByText('Report an issue').getBoundingClientRect().bottom).toBeLessThanOrEqual(
      listBox.bottom + 1,
    );
  });
});
