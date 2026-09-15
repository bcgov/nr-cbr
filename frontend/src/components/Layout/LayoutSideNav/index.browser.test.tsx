import { act, render, screen } from '@testing-library/react';
import { page } from '@vitest/browser/context';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, it, expect, vi } from 'vitest';

import { AuthProvider } from '@/context/auth/AuthProvider';

import { LayoutSideNav } from './index';

// Mutable so each case can set (or clear) the support address; the component reads it per render.
const { envMock } = vi.hoisted(() => ({ envMock: {} as Record<string, string> }));
vi.mock('@/env', () => ({ env: envMock }));

// Mutable so a case can collapse the nav; defaults to expanded like every existing case assumes.
const { layoutMock } = vi.hoisted(() => ({ layoutMock: { isSideNavExpanded: true } }));
vi.mock('@/context/layout/useLayout', () => ({
  useLayout: () => ({
    isSideNavExpanded: layoutMock.isSideNavExpanded,
    closeSideNav: () => {},
  }),
}));

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { roles: ['admin'] }, isLoggedIn: true }),
}));

vi.mock('@/routes/routePaths', () => ({
  getOfflineMenuEntries: () => [{ id: 'Offline Inspections', path: '/inspections/offline' }],
  getMenuEntries: () => [
    {
      id: 'Dashboard',
      path: '/dashboard',
      isMenuItem: true,
    },
    {
      id: 'Settings',
      path: '/settings',
      isMenuItem: true,
      children: [
        {
          id: 'Profile',
          path: 'profile',
          isMenuItem: true,
        },
      ],
    },
  ],
}));

const renderWithProviders = async (pathname = '/dashboard') => {
  window.history.pushState({}, '', pathname);
  await act(async () =>
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={[pathname]}>
          <LayoutSideNav />
        </MemoryRouter>
      </AuthProvider>,
    ),
  );
};

/** Carbon's lg breakpoint is 66rem (1056px); these sit either side of it. */
const DESKTOP: [number, number] = [1280, 800];
const MOBILE: [number, number] = [390, 844];

describe('LayoutSideNav', () => {
  afterEach(async () => {
    layoutMock.isSideNavExpanded = true;
    await page.viewport(DESKTOP[0], DESKTOP[1]);
  });

  /**
   * The nav's two shapes are entirely CSS, and which one applies depends on the viewport — so these
   * assert the computed width rather than a class name. A rule that silently stops matching (a
   * renamed Carbon class, a lost variable) would leave the markup looking right while the rail
   * collapsed to nothing.
   */
  it('collapses to an icon rail from lg, keeping the icons reachable', async () => {
    await page.viewport(DESKTOP[0], DESKTOP[1]);
    layoutMock.isSideNavExpanded = false;
    await renderWithProviders();

    const nav = document.querySelector('.side-nav-drawer');
    expect(nav).toBeTruthy();
    expect(getComputedStyle(nav as Element).width).toBe('48px');
    // The icons stay reachable: every link is still rendered and on screen.
    expect(screen.getByTestId('side-nav-link-Dashboard')).toBeInTheDocument();
  });

  it('expands to the full panel from lg', async () => {
    await page.viewport(DESKTOP[0], DESKTOP[1]);
    await renderWithProviders();

    const nav = document.querySelector('.side-nav-drawer');
    expect(getComputedStyle(nav as Element).width).toBe('256px');
  });

  /**
   * Below lg the rail would cost a phone 48px of width permanently, so the nav stays a drawer that
   * slides off-screen and overlays the content when open.
   */
  it('slides off-screen below lg rather than holding a rail', async () => {
    await page.viewport(MOBILE[0], MOBILE[1]);
    layoutMock.isSideNavExpanded = false;
    await renderWithProviders();

    const nav = document.querySelector('.side-nav-drawer') as Element;
    const style = getComputedStyle(nav);
    expect(style.width).toBe('256px');
    // translateX(-100%) of a 256px panel — matrix form, since that is what the browser computes.
    expect(style.transform).toBe('matrix(1, 0, 0, 1, -256, 0)');
  });

  it('renders menu links and menu items', async () => {
    await renderWithProviders('/dashboard');
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Settings')).toBeInTheDocument();
    expect(screen.getByText('Profile')).toBeInTheDocument();
    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
    expect(screen.queryByText('Hidden')).not.toBeInTheDocument();
  });

  it('marks the correct link as active', async () => {
    await renderWithProviders('/settings/profile');
    const profileLink = screen.getByText('Profile').closest('a');
    expect(profileLink).toHaveClass('cds--side-nav__link--current');
  });

  it('marks the SECTION active too, which is all the collapsed rail can show', async () => {
    // With the nav collapsed the children are hidden, so the section itself has to carry the state
    // or the user loses every trace of where they are. Carbon puts `--active` on the <li> when
    // `isActive` is passed, which LayoutSideNav derives from the path prefix; the rail stylesheet
    // paints that. Asserted on the class rather than the paint because the styling is CSS-only.
    await renderWithProviders('/settings/profile');

    const section = screen.getByText('Settings').closest('li');
    expect(section).toHaveClass('cds--side-nav__item--active');
  });

  it('gives the section a title element for the rail tooltip to reuse', async () => {
    // The collapsed rail has no room for a label, so the tooltip is the real title repurposed —
    // absolutely positioned and revealed on hover. Reusing the element rather than a data-attribute
    // guarantees the tooltip says exactly what the expanded nav says.
    await renderWithProviders('/settings/profile');

    expect(screen.getByText('Settings')).toHaveClass('cds--side-nav__submenu-title');
  });

  it('offers "Report an issue" when a support mailbox is configured', async () => {
    // The app tells users to contact the CBR help desk when something fails; this is the how.
    envMock.VITE_SUPPORT_EMAIL = 'cbr@gov.bc.ca';
    await renderWithProviders();

    const link = screen.getByTestId('side-nav-link-email-support');
    expect(link.getAttribute('href')).toBe('mailto:cbr@gov.bc.ca');
    expect(screen.getByText('Support')).toBeTruthy();
  });

  it('hides the link when no mailbox is configured', async () => {
    // Better no link than a mailto: that goes nowhere — there is no in-code default address.
    delete envMock.VITE_SUPPORT_EMAIL;
    await renderWithProviders();

    expect(screen.queryByTestId('side-nav-link-email-support')).toBeNull();
    expect(screen.queryByText('Support')).toBeNull();
  });

  it('ignores a whitespace-only value', async () => {
    envMock.VITE_SUPPORT_EMAIL = '   ';
    await renderWithProviders();

    expect(screen.queryByTestId('side-nav-link-email-support')).toBeNull();
  });
});
