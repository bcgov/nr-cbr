import { Button, Column, Grid } from '@carbon/react';
import { useEffect, type FC } from 'react';
import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import './globalError.scss';

/**
 * What a user sees when a route throws — the `errorElement` every route carries (see
 * `routePaths.tsx`).
 *
 * <p>This replaces react-router's built-in screen, which opens "Unexpected Application Error!",
 * prints the exception message as a heading, dumps the stack trace, and closes with a note
 * beginning "Hey developer 👋". None of that is addressed to the person using the application, and
 * the stack trace names internal paths and module versions.
 *
 * <p>Three things this has to do, in order of how much they matter:
 *
 * <ol>
 *   <li><b>Say what happened without frightening or blaming.</b> A render error means the screen
 *       failed, not that the user's data is gone — CBR's read screens write nothing.</li>
 *   <li><b>Offer a way out.</b> The previous version was a dead end: the router has already
 *       unmounted the page, so the side nav is gone and there is nothing to click. Retrying the
 *       same route is the usual fix for a transient failure, and the landing page is the reliable
 *       one.</li>
 *   <li><b>Keep the detail where a developer will find it.</b> The stack goes to the console every
 *       time, and on screen only in a development build.</li>
 * </ol>
 */
const GlobalErrorPage: FC = () => {
  const error = useRouteError();
  const navigate = useNavigate();

  // Unconditional, and not behind the DEV check below: this is the only record of the failure in a
  // deployed environment, where the user's next move is a reload that discards it. In an effect
  // rather than in the render body so it is logged once per error instead of once per render.
  useEffect(() => {
    // eslint-disable-next-line no-console -- the only trace of a render failure in a deployed build
    console.error('Unhandled route error', error);
  }, [error]);

  return (
    <Grid fullWidth className="default-grid">
      <Column lg={16} md={8} sm={4}>
        <PageTitle
          title="Something went wrong"
          subtitle={
            'This screen could not be displayed. Nothing you were looking at has been changed. ' +
            'Try again, and if it keeps happening let the CBR team know what you were doing.'
          }
        />
      </Column>

      <Column lg={16} md={8} sm={4}>
        <div className="global-error__actions">
          {/* -1 rather than a fixed path: the router has unmounted the page that threw, but its
              history entry is still there, so this returns the user to wherever they actually
              were. */}
          <Button kind="tertiary" onClick={() => void navigate(-1)} data-testid="global-error-back">
            Go back
          </Button>
          <Button onClick={() => void navigate('/')} data-testid="global-error-home">
            Go to the home page
          </Button>
        </div>
      </Column>

      {/* Development only. In a deployed build the detail is in the console, where it is available
          to anyone debugging and invisible to everyone else. */}
      {import.meta.env.DEV && (
        <Column lg={16} md={8} sm={4}>
          <pre className="global-error__detail" data-testid="global-error-detail">
            {detailOf(error)}
          </pre>
        </Column>
      )}
    </Grid>
  );
};

/**
 * The most useful description of the error available, for the development-only panel.
 *
 * <p>`isRouteErrorResponse` covers the errors react-router raises itself — a failed loader, a 404
 * from a data route — which are plain objects rather than `Error`s and carry their message in
 * `statusText`.
 */
const detailOf = (error: unknown): string => {
  if (isRouteErrorResponse(error)) {
    return `${error.status} ${error.statusText}`;
  }
  if (error instanceof Error) {
    return error.stack ?? `${error.name}: ${error.message}`;
  }
  return String(error);
};

export default GlobalErrorPage;
