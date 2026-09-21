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

  it('marks the SECTION active too, not just the child inside it', async () => {
    // Carbon puts `--active` on the <li> when `isActive` is passed, which LayoutSideNav derives
    // from the path prefix. Asserted on the class rather than the paint because the styling is
    // CSS-only.
    await renderWithProviders('/settings/profile');

    const section = screen.getByText('Settings').closest('li');
    expect(section).toHaveClass('cds--side-nav__item--active');
  });

  /**
   * The rail is 48px wide and hides both the submenu and its chevron, so a section rendered as
   * Carbon's `SideNavMenu` would be a button toggling a list that can never appear — the user
   * presses the icon and nothing happens, which is exactly what was reported. In the rail the
   * section is a link instead, to its own path, which redirects to its first child.
   */
  it('makes a collapsed section navigate rather than toggle a submenu that cannot open', async () => {
    layoutMock.isSideNavExpanded = false;
    await renderWithProviders('/dashboard');

    const section = screen.getByTestId('side-nav-link-Settings');
    expect(section.tagName).toBe('A');
    expect(section).toHaveAttribute('href', '/settings');
    // The disclosure button is gone, so there is nothing left that swallows the click.
    expect(document.querySelector('.cds--side-nav__submenu')).toBeNull();
  });

  it('lights the collapsed section for a child route, whose path it never exactly matches', async () => {
    // `/settings` is never the address bar's value — it redirects to `/settings/profile` — so the
    // rail link has to match on the prefix or the user loses every trace of where they are.
    layoutMock.isSideNavExpanded = false;
    await renderWithProviders('/settings/profile');

    expect(screen.getByTestId('side-nav-link-Settings')).toHaveClass(
      'cds--side-nav__link--current',
    );
  });

  it('gives the section a title element for the rail tooltip to reuse', async () => {
    // The collapsed rail has no room for a label, so the tooltip is the real title repurposed —
    // absolutely positioned and revealed on hover. Reusing the element rather than a data-attribute
    // guarantees the tooltip says exactly what the expanded nav says.
    await renderWithProviders('/settings/profile');

    expect(screen.getByText('Settings')).toHaveClass('cds--side-nav__submenu-title');
  });

  /**
   * A child link has to fill its 3rem row, or a selected child paints two thirds of its row and
   * stops. Carbon holds the <a> at 2rem through a selector more specific than the app-wide rule in
   * `styles/_overrides.scss`, so the nav's own stylesheet finishes the job.
   *
   * <p>Asserted on the declaration rather than the rendered height: only the component's own SCSS
   * is loaded here — nothing in the browser setup pulls in Carbon's ui-shell CSS — so the link is
   * still `display: inline` in this environment and reports a height of `auto` however tall the
   * rule makes it. Reading the declaration back still catches the rule ceasing to match, which is
   * the failure worth guarding. The rest of the nav's metrics are verified visually.
   */
  it('makes a child link fill its 48px row', async () => {
    await renderWithProviders('/settings/profile');

    const child = screen.getByText('Profile').closest('a') as Element;

    expect(getComputedStyle(child).blockSize).toBe('48px');
    expect(getComputedStyle(child).minBlockSize).toBe('48px');
  });

  /**
   * Regression. Child labels used to be passed through the icon renderer, which wrapped them in a
   * `cds--side-nav__icon` — `flex: 0 0 1rem`. SideNavMenuItem then nested that inside its own
   * `__link-text`, and the label was squeezed into a 16px column of ellipsis.
   *
   * Asserted as "not truncated" rather than as an absolute width, because the failure being guarded
   * against is the text not fitting its own box, whatever the panel is sized at.
   */
  it('renders a child label at full width rather than clipping it to the icon column', async () => {
    await renderWithProviders('/settings/profile');

    const label = screen.getByText('Profile');
    expect(label.querySelector('.cds--side-nav__icon')).toBeNull();
    expect(label.scrollWidth).toBeLessThanOrEqual(label.clientWidth);
  });

  /**
   * The rail has no room for a label, so the hover tooltip is the only thing naming an icon. It is
   * drawn with Carbon's own tooltip tokens and a caret pointing back at the icon; the caret is a
   * pseudo-element, so it is asserted through getComputedStyle rather than the DOM.
   */
  it('draws the rail tooltip in the inverse theme with a caret pointing at the icon', async () => {
    await page.viewport(DESKTOP[0], DESKTOP[1]);
    layoutMock.isSideNavExpanded = false;
    await renderWithProviders();

    const label = screen.getByText('Dashboard');
    const tooltip = getComputedStyle(label);
    const caret = getComputedStyle(label, '::before');

    // $background-inverse / $text-inverse in the white theme.
    expect(tooltip.backgroundColor).toBe('rgb(57, 57, 57)');
    expect(tooltip.color).toBe('rgb(255, 255, 255)');
    // Hidden until hover — the rule that reveals it is :hover, which this does not simulate.
    expect(tooltip.opacity).toBe('0');
    // A right border alone on a zero-sized box renders as a triangle pointing left.
    expect(caret.borderInlineEndWidth).toBe('4px');
    expect(caret.borderInlineEndColor).toBe('rgb(57, 57, 57)');
  });

  it('rules off the support block and keeps it pinned to the bottom', async () => {
    // The divider carries the `margin-block-start: auto` that pins the block down, and it is the
    // one part of the block that survives the collapse to a rail — the heading is squeezed to zero
    // height there, so pinning from the heading would have let the support icon ride up under the
    // last nav entry.
    envMock.VITE_SUPPORT_EMAIL = 'cbr@gov.bc.ca';
    await renderWithProviders();

    const divider = document.querySelector('.side-nav-support-divider') as HTMLElement;
    expect(divider).toBeTruthy();
    // Above the heading, not below it.
    expect(divider.nextElementSibling).toHaveClass('side-nav-support-heading');

    // The pinning itself. The nav has no height of its own in this environment (Carbon's ui-shell
    // CSS, which gives it one, is not loaded here), so give it one: with free space to distribute,
    // the auto margin should drive the divider to the foot rather than leave it under Settings.
    const nav = document.querySelector('.side-nav-drawer') as HTMLElement;
    nav.style.height = '600px';
    const lastEntry = screen.getByText('Settings').closest('li') as HTMLElement;

    expect(divider.getBoundingClientRect().top).toBeGreaterThan(
      lastEntry.getBoundingClientRect().bottom + 100,
    );
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
