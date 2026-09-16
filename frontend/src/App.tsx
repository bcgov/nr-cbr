import { type FC } from 'react';

import SessionTimeout from '@/components/SessionTimeout';
import AppRoutes from '@/routes/AppRoutes';

import { useAuth } from '@/context/auth/useAuth';
import { usePrefetchConfiguration } from '@/hooks/useConfiguration';

const App: FC = () => {
  const { isLoggedIn } = useAuth();

  // Warm the dropdown lookups in the background, so the first screen that needs one renders with it
  // already filled. Gated on the session: these endpoints require authentication, and an
  // unauthenticated call signs the user out and redirects — see usePrefetchConfiguration.
  usePrefetchConfiguration(isLoggedIn);

  return (
    <>
      {/* Inactivity auto-logout (warns first) — only runs while authenticated. */}
      {isLoggedIn && <SessionTimeout />}
      <AppRoutes />
    </>
  );
};

export default App;
