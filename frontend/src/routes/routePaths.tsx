import { Location as LocationIcon } from '@carbon/icons-react';
import { Navigate, type RouteObject } from 'react-router-dom';

import Layout from '@/components/Layout';
import AuthCallbackPage from '@/pages/AuthCallback';
import GlobalErrorPage from '@/pages/GlobalError';
import LandingPage from '@/pages/Landing';
import NotFoundPage from '@/pages/NotFound';
import RoleErrorPage from '@/pages/RoleError';
import SiteSearchPage from '@/pages/SiteSearch';

import ProtectedRoute from './ProtectedRoute';

import type { ROLE_TYPE } from '@/context/auth/types';

import { LANDING_AFTER_LOGIN } from '@/constants/routes';
import { ROLE_CAPABILITIES } from '@/context/auth/types';

/**
 * Route table, ported from nr-frep. Three sets, selected by auth state in AppRoutes: public
 * (unauthenticated), no-role (authenticated but holding no recognised CBR group), and protected.
 *
 * <p>Site Search is the only protected destination so far. The inventory this grows into is in
 * cbr-overview.local.md §5: site, structure (7 tabs), inspection, documents, 12 report criteria
 * pages, 3 admin pages and the search pages; the sections they hang under are in
 * cbr-navigation.local.md §1.
 *
 * <p>nr-frep also carries an offline route set (getOfflineRoutes / getOfflineMenuEntries) served
 * when the device is offline. CBR needs the equivalent once the offline inspection flow lands
 * (§11, §16); {@link getOfflineMenuEntries} returns empty for now rather than pointing at routes
 * that do not exist.
 */
export type RouteDescription = {
  id: string;
  path: string;
  element: React.ReactNode;
  icon?: React.ComponentType;
  isSideMenu: boolean;
  children?: RouteDescription[];
  roles?: ROLE_TYPE[];
} & RouteObject;

export type MenuItem = Pick<RouteDescription, 'id' | 'path' | 'icon'> & {
  children?: MenuItem[];
};

// --- Route arrays --------------------------------------------------------

/** Unauthenticated routes — shown when the user is not logged in. */
export const PUBLIC_ROUTES: RouteDescription[] = [
  {
    path: '/',
    id: 'Landing',
    element: <LandingPage />,
    isSideMenu: false,
  },
  {
    path: '/unauthorized',
    id: 'Unauthorized',
    element: <RoleErrorPage />,
    isSideMenu: false,
  },
  {
    // OAuth redirect target. AuthCallbackPage performs the authorization-code exchange, which is
    // what creates the session — hence its place in the PUBLIC table rather than the protected one.
    //
    // ORDER IS LOAD-BEARING: it must sit above the '*' catch-all below, which would otherwise match
    // the callback URL (arriving as /authCallback?code=…&state=…) and render Not Found before the
    // code could be spent.
    path: '/authCallback',
    id: 'Auth callback',
    element: <AuthCallbackPage />,
    isSideMenu: false,
  },
  {
    path: '*',
    id: 'Not Found',
    element: <NotFoundPage />,
    isSideMenu: false,
    errorElement: <GlobalErrorPage />,
  },
];

/**
 * Routes for a user who authenticated through FAM but holds no recognised CBR group. They get the
 * role-error page rather than a 404, so the message says "ask for access" not "no such page".
 */
export const NO_ROLE_ROUTES: RouteDescription[] = [
  {
    path: '/unauthorized',
    id: 'Unauthorized',
    element: <RoleErrorPage />,
    isSideMenu: false,
  },
  {
    path: '*',
    id: 'Unauthorized',
    element: <Navigate to="/unauthorized" replace />,
    isSideMenu: false,
  },
];

/** Routes for an authenticated user holding at least one CBR role. */
export const PROTECTED_ROUTES: RouteDescription[] = [
  {
    path: '/',
    id: 'Home',
    element: <Navigate to={LANDING_AFTER_LOGIN} replace />,
    isSideMenu: false,
  },
  {
    // OAuth redirect target — once the session exists, bounce off the callback URL to home. (The
    // exchange itself happens on the public route above; this entry only catches a reload of the
    // callback URL by an already-signed-in user.)
    path: '/authCallback',
    id: 'Auth callback',
    element: <Navigate to={LANDING_AFTER_LOGIN} replace />,
    isSideMenu: false,
  },
  {
    // Inventory — the legacy menu's first section (cbr-navigation.local.md §1).
    //
    // `children` here is NAV structure, not router structure. getProtectedRoutes() flattens it
    // before the router ever sees it (see the note there): a route carrying both an `element` and
    // `children` is a react-router *layout* route, whose element must render an <Outlet/> for the
    // children to appear. This element is a redirect, so the children would never render — and
    // since the redirect targets a child, the two would bounce off each other forever and the
    // screen would stay blank.
    //
    // The element below is what /inventory itself resolves to, so the parent path lands on the
    // first child rather than on nothing.
    path: '/inventory',
    id: 'Inventory',
    icon: LocationIcon,
    element: <Navigate to="/inventory/site-search" replace />,
    isSideMenu: true,
    children: [
      {
        path: 'site-search',
        id: 'Site Search',
        element: (
          <ProtectedRoute>
            <Layout>
              <SiteSearchPage />
            </Layout>
          </ProtectedRoute>
        ),
        isSideMenu: true,
        // Legacy gate: /showSiteSearch, which every role that can read holds.
        roles: [...ROLE_CAPABILITIES.read],
      },
    ],
  },
  {
    path: '/unauthorized',
    id: 'Unauthorized',
    element: <RoleErrorPage />,
    isSideMenu: false,
  },
  {
    path: '*',
    id: 'Not Found',
    element: (
      <Layout>
        <NotFoundPage />
      </Layout>
    ),
    isSideMenu: false,
    errorElement: <GlobalErrorPage />,
  },
];

// --- Accessors -----------------------------------------------------------

/** Returns the public (unauthenticated) route array. */
export const getPublicRoutes = (): RouteDescription[] => PUBLIC_ROUTES;

/** Returns the route array for an authenticated user with no recognised role. */
export const getNoRoleRoutes = (): RouteDescription[] => NO_ROLE_ROUTES;

/**
 * The protected routes, flattened for react-router.
 *
 * <p>PROTECTED_ROUTES is a nav tree: a section like Inventory carries its pages as `children` so
 * {@link getMenuEntries} can render a nested side-nav menu. React-router reads `children`
 * differently — as a layout route whose element must render an `<Outlet/>` — so the tree is
 * flattened here rather than handed over as-is. Every child becomes a top-level route at its
 * absolute path, and the parent keeps its own path as an index redirect.
 *
 * <p>Flattening rather than nesting on purpose: each page already wraps itself in `<Layout>`, so a
 * real layout route would wrap them twice.
 */
export const getProtectedRoutes = (): RouteDescription[] =>
  PROTECTED_ROUTES.flatMap((route) => {
    if (!route.children?.length) return [route];

    const { children, ...parent } = route;
    return [
      parent,
      ...children.map((child) => ({
        ...child,
        // Children carry a path relative to their section, the same way the side nav joins them.
        path: `${route.path}/${child.path}`,
      })),
    ];
  });

/**
 * Side-nav entries for the given roles: the protected routes flagged `isSideMenu` whose `roles`
 * restriction (if any) the user satisfies.
 */
export const getMenuEntries = (roles: string[]): MenuItem[] => {
  const permitted = (route: RouteDescription) =>
    !route.roles || route.roles.some((r) => roles.includes(r));

  return (
    PROTECTED_ROUTES.filter((route) => route.isSideMenu)
      .filter(permitted)
      .map(({ id, path, icon, children }) => ({
        id,
        path,
        icon,
        children: children
          ?.filter((child) => child.isSideMenu)
          .filter(permitted)
          .map(({ id: childId, path: childPath, icon: childIcon }) => ({
            id: childId,
            path: childPath,
            icon: childIcon,
          })),
      }))
      // A parent whose children are all filtered out is dropped rather than rendered empty. The
      // legacy menu got this wrong — MenuTag writes the section header before testing whether any
      // item survived authorization, so an administrator saw four empty section headings above two
      // working links (cbr-navigation.local.md §3). Fixed by construction here.
      .filter((entry) => !entry.children || entry.children.length > 0)
  );
};

/**
 * Side-nav entries while the device is offline. Empty until the offline inspection flow exists —
 * an offline nav pointing at online-only screens would be worse than none.
 */
export const getOfflineMenuEntries = (): MenuItem[] => [];
