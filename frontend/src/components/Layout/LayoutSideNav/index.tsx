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
  const { isSideNavExpanded } = useLayout();
  const location = useLocation();
  const { user, isLoggedIn } = useAuth();
  const online = useOnlineStatus();

  // Offline (or logged out): the server-backed screens can't load, so the side nav shows only the
  // offline-capable routes.
  const menuEntries =
    online && isLoggedIn ? getMenuEntries(user?.roles || []) : getOfflineMenuEntries();

  /**
   * An entry as a link.
   *
   * <p>`isActive` is an exact match for a leaf and a prefix match for a section, because a
   * section's own path is never the address the user is on — `/inventory` redirects straight to
   * `/inventory/site-search` — so an exact test would leave the rail with nothing lit.
   */
  const renderMenuLink = (route: MenuItem, isSection = false) => (
    <SideNavLink
      data-testid={`side-nav-link-${route.id}`}
      key={route.id}
      as={Link}
      to={route.path}
      isActive={
        isSection ? location.pathname.startsWith(route.path) : route.path === location.pathname
      }
      renderIcon={route.icon}
    >
      {route.id}
    </SideNavLink>
  );

  const renderMenuItem = (route: MenuItem) => {
    const childPath = (parentPath: string, route: MenuItem) =>
      `${parentPath}${route.path ? `/${route.path}` : ''}`;
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
        {/* A section is a disclosure in the panel and a plain link in the rail.
            `SideNavMenu` is a <button> that toggles a nested list, and at 48px wide there is
            nowhere for that list to go — the rail stylesheet hides both the list and its chevron —
            so pressing the icon would expand something that can never appear and the user would
            get no navigation at all. As a link it goes to the section's own path, which redirects
            to its first child (see routePaths), so the icon lands where it looks like it should. */}
        {menuEntries.map((route) =>
          route.children && isSideNavExpanded
            ? renderMenuItem(route)
            : renderMenuLink(route, Boolean(route.children)),
        )}
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
