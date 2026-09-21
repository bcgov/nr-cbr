import { Email } from '@carbon/icons-react';
import {
  SideNav,
  SideNavDivider,
  SideNavItems,
  SideNavLink,
  SideNavMenu,
  SideNavMenuItem,
} from '@carbon/react';
import { type FC } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { getMenuEntries, getOfflineMenuEntries, type MenuItem } from '@/routes/routePaths';

import { useAuth } from '@/context/auth/useAuth';
import { useLayout } from '@/context/layout/useLayout';
import { env } from '@/env';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';

import './index.scss';

export const LayoutSideNav: FC = () => {
  /**
   * Shared mailbox behind "Report an issue". Configuration, not code: set from the SUPPORT_EMAIL
   * GitHub variable via VITE_SUPPORT_EMAIL, so the address can change without a code change — and
   * because there is no CBR support address in the codebase to hard-code. The link is hidden
   * entirely when it is unset, rather than shipping a mailto: that goes nowhere.
   *
   * Read per render, not once at module load: `env` merges values injected into window.config at
   * container start, and reading it here keeps the component testable without module resets.
   */
  const supportEmail = env.VITE_SUPPORT_EMAIL?.trim() ?? '';
  const { isSideNavExpanded, openSideNav } = useLayout();
  const location = useLocation();
  const { user, isLoggedIn } = useAuth();
  const online = useOnlineStatus();

  // Offline (or logged out): the server-backed screens can't load, so the side nav shows only the
  // offline-capable routes.
  const menuEntries =
    online && isLoggedIn ? getMenuEntries(user?.roles || []) : getOfflineMenuEntries();

  const childPath = (parentPath: string, child: MenuItem) => {
    // The suffix on its own line rather than a template nested inside a template. A child with no
    // path of its own is the section's own path — it is the section's index route.
    const suffix = child.path ? `/${child.path}` : '';
    return `${parentPath}${suffix}`;
  };

  /** The child of this section the user is on, if they are on one. */
  const activeChild = (route: MenuItem) =>
    route.children?.find((child) => childPath(route.path, child) === location.pathname);

  /** A leaf entry: a link straight to its page. */
  const renderMenuLink = (route: MenuItem) => (
    <SideNavLink
      data-testid={`side-nav-link-${route.id}`}
      key={route.id}
      as={Link}
      to={route.path}
      isActive={route.path === location.pathname}
      renderIcon={route.icon}
    >
      {route.id}
    </SideNavLink>
  );

  /**
   * A section in the collapsed rail: a button that expands the nav, and nothing else.
   *
   * <p><b>It deliberately does not navigate.</b> A section has several pages under it and the icon
   * names none of them, so going anywhere would be picking one on the user's behalf — and the page
   * they wanted would be one they had to leave again. Expanding is the only move that answers what
   * the press actually asked, which is "show me what is in here".
   *
   * <p><b>The label carries both levels</b> — "Inventory: Add Site" — because the rail shows one
   * icon for a section and the user otherwise has no way to tell which of its pages they are on.
   * The label is also the tooltip: the rail stylesheet takes this same element out of flow and
   * floats it beside the icon on hover, so what is written here is what is read there.
   *
   * <p>Rendered through `SideNavLink` as a `button` rather than as a bare element of its own, so it
   * inherits every rail rule already written for a link — the icon centring, the tooltip, the
   * active highlight — instead of a second copy of them that could drift.
   */
  const renderRailSection = (route: MenuItem) => {
    const current = activeChild(route);
    return (
      <SideNavLink
        data-testid={`side-nav-section-${route.id}`}
        key={route.id}
        as="button"
        type="button"
        // Prefix, not an exact match: a section's own path is never the address the user is on —
        // `/inventory` redirects to a child — so an exact test would leave the rail unlit.
        isActive={location.pathname.startsWith(route.path)}
        renderIcon={route.icon}
        // Accurate as written: this only ever renders while the nav is collapsed.
        aria-expanded={false}
        onClick={openSideNav}
      >
        {current ? `${route.id}: ${current.id}` : route.id}
      </SideNavLink>
    );
  };

  const renderMenuItem = (route: MenuItem) => {
    return (
      <SideNavMenu
        data-testid={`side-nav-menu-${route.id}`}
        key={route.id}
        title={route.id}
        isActive={location.pathname.startsWith(route.path)}
        defaultExpanded={location.pathname.startsWith(route.path)}
        renderIcon={route.icon}
      >
        {route.children?.map((childRoute) => (
          <SideNavMenuItem
            data-testid={`side-nav-menu-item-${childRoute.id}`}
            key={childRoute.id}
            as={Link}
            to={childPath(route.path, childRoute)}
            isActive={childPath(route.path, childRoute) === location.pathname}
          >
            {/* The bare label, not a wrapped one. SideNavMenuItem already puts its children inside
                a `__link-text` span, so anything wrapped here lands nested inside that span — and
                a `__side-nav__icon` wrapper in particular is `flex: 0 0 1rem`, which squeezed every
                child label down to a 16px column of ellipsis. */}
            {childRoute.id}
          </SideNavMenuItem>
        ))}
      </SideNavMenu>
    );
  };

  return (
    <SideNav
      expanded
      isPersistent={false}
      isChildOfHeader
      className={`side-nav-drawer${isSideNavExpanded ? ' side-nav-drawer--open' : ''}`}
    >
      <SideNavItems>
        {/* A section is a disclosure in the panel and a button in the rail.
            `SideNavMenu` is a <button> that toggles a nested list, and at 48px wide there is
            nowhere for that list to go — the rail stylesheet hides both the list and its chevron —
            so pressing the icon would expand something that can never appear. In the rail it
            becomes a control that opens the nav instead, which is the only thing a section icon
            can honestly promise when it stands for several pages. */}
        {menuEntries.map((route) => {
          if (!route.children) return renderMenuLink(route);
          return isSideNavExpanded ? renderMenuItem(route) : renderRailSection(route);
        })}
        {/* Support — pinned to the bottom of the nav regardless of how many role-dependent entries
            render above it (see the flex rules in index.scss). A plain mailto: rather than a route:
            it opens the user's own mail client with the shared mailbox pre-addressed. The app tells
            users to "contact the CBR help desk" when something fails; this is the how. */}
        {supportEmail && (
          <>
            {/* The rule above the block, and the thing that pins it down: the divider carries the
                `margin-block-start: auto`. It is the one element of the three that is present in
                both the panel and the rail, so the pinning survives the collapse — the heading it
                used to sit on is squeezed to zero height at rail width. */}
            <SideNavDivider className="side-nav-support-divider" />
            <li className="side-nav-support-heading" aria-hidden="true">
              Support
            </li>
            <SideNavLink
              data-testid="side-nav-link-email-support"
              href={`mailto:${supportEmail}`}
              renderIcon={Email}
            >
              Report an issue
            </SideNavLink>
          </>
        )}
      </SideNavItems>
    </SideNav>
  );
};
