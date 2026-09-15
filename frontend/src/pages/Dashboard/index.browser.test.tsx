import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const authorization = vi.hoisted(() => ({ current: {} as Partial<AuthorizationInfo> }));

vi.mock('@/hooks/useAuthorization', () => ({
  useAuthorization: () => authorization.current,
}));

import DashboardPage from './index';

import type { AuthorizationInfo } from '@/hooks/useAuthorization';

import { APP_FULL_NAME, APP_NAME } from '@/constants/appName';

const withCapabilities = (capabilities: Partial<AuthorizationInfo>) => {
  authorization.current = {
    isSysAdmin: false,
    isPeng: false,
    canRead: false,
    canWriteInspection: false,
    canEdit: false,
    canDelete: false,
    ...capabilities,
  };
};

describe('DashboardPage', () => {
  it('names the application', () => {
    withCapabilities({ canRead: true });

    render(<DashboardPage />);

    expect(screen.getByRole('heading', { name: APP_NAME })).toBeTruthy();
    expect(screen.getByText(APP_FULL_NAME)).toBeTruthy();
  });

  it('says plainly that the screens are not built yet', () => {
    // The tile grid comes back as the screens land; until then saying so beats tiles that navigate
    // nowhere.
    withCapabilities({ canRead: true });

    render(<DashboardPage />);

    expect(screen.getByText(/Application shell/)).toBeTruthy();
  });

  it('reports a read-only user as able to read and nothing else', () => {
    withCapabilities({ canRead: true });

    render(<DashboardPage />);

    const values = screen.getAllByRole('definition').map((node) => node.textContent);
    // Administrator, Can read, Can record an inspection, Can edit, Can delete, Can sign off
    expect(values).toEqual(['no', 'yes', 'no', 'no', 'no', 'no']);
  });

  it('reports a P.Eng as holding every capability on the ladder', () => {
    withCapabilities({
      canRead: true,
      canWriteInspection: true,
      canEdit: true,
      canDelete: true,
      isPeng: true,
    });

    render(<DashboardPage />);

    const values = screen.getAllByRole('definition').map((node) => node.textContent);
    expect(values).toEqual(['no', 'yes', 'yes', 'yes', 'yes', 'yes']);
  });

  it('reports an administrator as reading but writing nothing', () => {
    // The shape that would be wrong if CBR_ADMIN were ever folded onto the ladder.
    withCapabilities({ isSysAdmin: true, canRead: true });

    render(<DashboardPage />);

    const values = screen.getAllByRole('definition').map((node) => node.textContent);
    expect(values).toEqual(['yes', 'yes', 'no', 'no', 'no', 'no']);
  });
});
