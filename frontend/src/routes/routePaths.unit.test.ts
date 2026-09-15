import { describe, expect, it } from 'vitest';

import { getMenuEntries, getOfflineMenuEntries, getProtectedRoutes } from './routePaths';

/**
 * The side nav is derived from the same role list the API enforces against, so these cases are the
 * menu equivalent of `useAuthorization`'s: what a given role is offered. The legacy rule carried
 * forward is that an item you cannot perform is never rendered — not disabled, not hidden with CSS
 * (`cbr-navigation.local.md` §2).
 */
const idsOf = (roles: string[]) => getMenuEntries(roles).map((entry) => entry.id);
const entry = (roles: string[], id: string) => getMenuEntries(roles).find((e) => e.id === id);

describe('getMenuEntries', () => {
  it('offers Inventory with Site Search beneath it to a reader', () => {
    const inventory = entry(['CBR_GENERAL'], 'Inventory');

    expect(inventory).toBeDefined();
    expect(inventory?.path).toBe('/inventory');
    expect(inventory?.children?.map((child) => child.id)).toEqual(['Site Search']);
  });

  it('gives the child a relative path, which the nav joins onto its parent', () => {
    // LayoutSideNav builds `${parent.path}/${child.path}` — an absolute child path would produce
    // '/inventory//inventory/site-search'.
    const child = entry(['CBR_GENERAL'], 'Inventory')?.children?.[0];

    expect(child?.path).toBe('site-search');
  });

  it.each(['CBR_GENERAL', 'CBR_LEVEL_0', 'CBR_LEVEL_1', 'CBR_LEVEL_2', 'CBR_PENG', 'CBR_ADMIN'])(
    'shows Site Search to %s — every role that can read',
    (role) => {
      // /showSiteSearch sits at the GENERAL floor, and CBR_ADMIN gained read in the 2026-09-15
      // decision, so there is no CBR role that can sign in and not see this.
      expect(entry([role], 'Inventory')?.children?.map((c) => c.id)).toEqual(['Site Search']);
    },
  );

  it('drops a section entirely when the user can reach none of its children', () => {
    // The legacy defect this exists to avoid: MenuTag wrote the section header before testing
    // whether any item survived authorization, so an administrator saw four empty headings above
    // two working links (cbr-navigation.local.md §3).
    expect(idsOf([])).not.toContain('Inventory');
    expect(idsOf(['SOME_UNRELATED_ROLE'])).not.toContain('Inventory');
  });

  it('never returns a parent with an empty children array', () => {
    for (const roles of [[], ['CBR_GENERAL'], ['CBR_ADMIN'], ['CBR_PENG']]) {
      for (const menuEntry of getMenuEntries(roles)) {
        if (menuEntry.children) {
          expect(menuEntry.children.length).toBeGreaterThan(0);
        }
      }
    }
  });

  it('offers nothing to a user with no roles', () => {
    // Every side-nav entry is role-gated now that Dashboard is gone, so an unrecognised user gets
    // an empty nav rather than a lone ungated link.
    expect(idsOf([])).toEqual([]);
  });

  it('shows Site Search once, nested — never also at the top level', () => {
    expect(idsOf(['CBR_GENERAL']).filter((id) => id === 'Site Search')).toHaveLength(0);
  });
});

/**
 * The router needs a flat list; the nav needs a tree. These pin the conversion, because getting it
 * wrong renders nothing at all: a route carrying both an `element` and `children` is a react-router
 * layout route whose element must contain an `<Outlet/>`. Inventory's element is a redirect to its
 * own child, so nesting made the two bounce off each other and the screen stayed blank.
 */
describe('getProtectedRoutes', () => {
  const paths = () => getProtectedRoutes().map((route) => route.path);

  it('hands react-router no route with children', () => {
    for (const route of getProtectedRoutes()) {
      expect(route.children).toBeUndefined();
    }
  });

  it('registers each child at its absolute path', () => {
    expect(paths()).toContain('/inventory/site-search');
  });

  it('keeps the section path itself, so /inventory resolves', () => {
    expect(paths()).toContain('/inventory');
  });

  it('registers every path exactly once', () => {
    const seen = paths();
    expect(new Set(seen).size).toBe(seen.length);
  });

  it('leaves childless routes untouched', () => {
    expect(paths()).toContain('/unauthorized');
    expect(paths()).toContain('*');
  });
});

describe('getOfflineMenuEntries', () => {
  it('is empty until the offline flow exists', () => {
    // An offline nav pointing at online-only screens would be worse than none.
    expect(getOfflineMenuEntries()).toEqual([]);
  });
});
