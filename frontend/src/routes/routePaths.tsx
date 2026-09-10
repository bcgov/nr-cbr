import { Dashboard as DashboardReference } from '@carbon/icons-react';
import { Navigate, type RouteObject } from 'react-router-dom';

import Layout from '@/components/Layout';
import AuthCallbackPage from '@/pages/AuthCallback';
import DashboardPage from '@/pages/Dashboard';
import GlobalErrorPage from '@/pages/GlobalError';
import LandingPage from '@/pages/Landing';
import NotFoundPage from '@/pages/NotFound';
import RoleErrorPage from '@/pages/RoleError';

import ProtectedRoute from './ProtectedRoute';

import type { ROLE_TYPE } from '@/context/auth/types';

/**
 * Route table, ported from nr-frep. Three sets, selected by auth state in AppRoutes: public
 * (unauthenticated), no-role (authenticated but holding no recognised CBR group), and protected.
 *
 * <p>The CBR screens are not built yet — Dashboard is the only protected destination. The inventory
 * this grows into is in cbr-overview.local.md §5: site, structure (7 tabs), inspection,
 * documents, 12 report criteria pages, 3 admin pages and the search pages.
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
    element: <Navigate to="/dashboard" replace />,
    isSideMenu: false,
  },
  {
    // OAuth redirect target — once the session exists, bounce off the callback URL to home. (The
    // exchange itself happens on the public route above; this entry only catches a reload of the
    // callback URL by an already-signed-in user.)
    path: '/authCallback',
    id: 'Auth callback',
    element: <Navigate to="/dashboard" replace />,
    isSideMenu: false,
  },
  {
    path: '/dashboard',
    id: 'Dashboard',
    icon: DashboardReference,
    element: (
      <ProtectedRoute>
        <Layout>
          <DashboardPage />
        </Layout>
      </ProtectedRoute>
    ),
    isSideMenu: true,
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

/** Returns the protected route array. */
export const getProtectedRoutes = (): RouteDescription[] => PROTECTED_ROUTES;

/**
 * Side-nav entries for the given roles: the protected routes flagged `isSideMenu` whose `roles`
 * restriction (if any) the user satisfies.
 */
export const getMenuEntries = (roles: string[]): MenuItem[] => {
  return PROTECTED_ROUTES.filter((route) => route.isSideMenu)
    .filter((route) => !route.roles || route.roles.some((r) => roles.includes(r)))
    .map(({ id, path, icon }) => ({ id, path, icon }));
};

/**
 * Side-nav entries while the device is offline. Empty until the offline inspection flow exists —
 * an offline nav pointing at online-only screens would be worse than none.
 */
export const getOfflineMenuEntries = (): MenuItem[] => [];
