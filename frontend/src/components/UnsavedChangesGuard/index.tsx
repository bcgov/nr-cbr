import { useEffect, type FC } from 'react';
import { useBlocker } from 'react-router-dom';

import DestructiveModal from '@/components/core/DestructiveModal';

import { useUnsavedChanges } from '@/context/unsavedChanges/useUnsavedChanges';

/**
 * Stops a navigation that would throw away unsaved work, and asks first.
 *
 * <p>Rendered once by `Layout`, so every screen in the application is covered by the same dialog
 * saying the same thing. A screen opts in by calling `useUnsavedChangesPrompt`; one that does not
 * is never blocked.
 *
 * <p><b>This is what a per-button confirmation cannot do.</b> A prompt wired to Cancel catches the
 * one exit the author thought of; the side nav, a breadcrumb, the browser's back button and a
 * redirect from somewhere else all still discard the form without a word. Legacy has exactly that
 * gap — `site.jsp` guards two of its three exits and leaves Cancel bare.
 *
 * <p>Two mechanisms, because no single one covers every way off a page:
 *
 * <ul>
 *   <li><b>{@link useBlocker}</b> for navigation inside the application. It needs a data router,
 *       which `AppRoutes` creates with `createBrowserRouter`.
 *   <li><b>`beforeunload`</b> for leaving the application entirely — closing the tab, reloading,
 *       following a link away. The router cannot see any of those. The browser shows its own
 *       wording here and there is no changing it; what matters is that it asks at all.
 * </ul>
 */
const UnsavedChangesGuard: FC = () => {
  const { isDirty } = useUnsavedChanges();

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      // Only a real change of screen. A search parameter or a hash moving on the page the user is
      // already editing is not them leaving it, and blocking that would be unexplainable.
      isDirty && currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    if (!isDirty) {
      return;
    }
    // `preventDefault` alone. Assigning `returnValue` is the older way of saying the same thing
    // and is deprecated; every browser this application supports acts on the cancellation. Neither
    // form can set the message — the browser supplies its own — so nothing here tries to.
    const confirmUnload = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', confirmUnload);
    return () => window.removeEventListener('beforeunload', confirmUnload);
  }, [isDirty]);

  return (
    <DestructiveModal
      open={blocker.state === 'blocked'}
      title="Leave without saving?"
      message="The changes on this page have not been saved, and there is no way to get them back."
      confirmButtonText="Leave"
      // Not "Cancel": on a form whose own button is called Cancel, two of them that undo each
      // other is a coin toss. This one says what staying does.
      cancelButtonText="Stay on this page"
      onConfirm={() => blocker.proceed?.()}
      onCancel={() => blocker.reset?.()}
    />
  );
};

export default UnsavedChangesGuard;
