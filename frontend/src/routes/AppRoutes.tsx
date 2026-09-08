import { Loading } from '@carbon/react';
import { Suspense, useEffect, useMemo, type FC } from 'react';
import { RouterProvider, createBrowserRouter } from 'react-router-dom';

import { LayoutProvider } from '@/context/layout/LayoutProvider';
import { getNoRoleRoutes, getProtectedRoutes, getPublicRoutes } from '@/routes/routePaths';

import { useAuth } from '@/context/auth/useAuth';
import { usePageTitle } from '@/context/pageTitle/usePageTitle';
import { env } from '@/env';
import { useAuthorization } from '@/hooks/useAuthorization';

/**
 * Top-level router. Switches between three route sets based on auth state: public, no-role, and
 * protected. Ported from nr-frep's AppRoutes.
 *
 * <p>nr-frep also serves an offline route set when the browser is offline and the user is not
 * signed in. CBR will need the same once the offline inspection flow lands (see
 * cbr-offline-client.local.md) — it is deliberately not stubbed here, because a route
 * set with nothing behind it is worse than none.
 */
const AppRoutes: FC = () => {
  const { isLoggedIn, isLoading } = useAuth();
  const { hasAnyRole } = useAuthorization();
  const { setPageTitle } = usePageTitle();

  const displayLoading = () => <Loading data-testid="loading" withOverlay={true} />;

  const routesToUse = useMemo(() => {
    if (!isLoggedIn) return getPublicRoutes();
    // Region-only users hold no global role; useAuthorization#hasAnyRole accounts for that, which
    // is why this uses the hook rather than checking user.roles.length directly.
    if (!hasAnyRole) return getNoRoleRoutes();
    return getProtectedRoutes();
  }, [isLoggedIn, hasAnyRole]);

  const basename = env.VITE_BASE_PATH || '/';
  const browserRouter = useMemo(
    () => createBrowserRouter(routesToUse, { basename }),
    [routesToUse, basename],
  );

  useEffect(() => {
    const currentRoute = routesToUse.find((route) => route.path === window.location.pathname);
    if (currentRoute) {
      setPageTitle(currentRoute.id || '', 1);
    }
  }, [routesToUse, setPageTitle]);

  if (isLoading) {
    return displayLoading();
  }

  return (
    // Above the router on purpose: each route mounts its own <Layout>, so layout state held inside
    // one would reset on every navigation (the side nav springing back open mid-task).
    <LayoutProvider>
      <Suspense fallback={displayLoading()}>
        <RouterProvider router={browserRouter} />
      </Suspense>
    </LayoutProvider>
  );
};

export default AppRoutes;
