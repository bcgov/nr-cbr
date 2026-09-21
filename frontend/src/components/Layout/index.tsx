import { Content, HeaderContainer } from '@carbon/react';

import UnsavedChangesGuard from '@/components/UnsavedChangesGuard';
import { LayoutProvider } from '@/context/layout/LayoutProvider';

import { LayoutHeader } from './LayoutHeader';

import type { FC, ReactNode } from 'react';

import { useLayout } from '@/context/layout/useLayout';

import './index.scss';

const LayoutContent: FC<{ children: ReactNode }> = ({ children }) => {
  const { isSideNavExpanded } = useLayout();

  return (
    <Content className={isSideNavExpanded ? 'layout-content--side-nav-expanded' : undefined}>
      {children}
    </Content>
  );
};

const Layout: FC<{ children: ReactNode }> = ({ children }) => {
  return (
    <LayoutProvider>
      {/* Every screen wraps itself in a Layout, so mounting the guard here covers all of them with
          one dialog — and a page that has no unsaved work is never blocked, so it costs nothing to
          the screens that do not opt in. */}
      <UnsavedChangesGuard />
      <HeaderContainer render={LayoutHeader} />
      <LayoutContent>{children}</LayoutContent>
    </LayoutProvider>
  );
};

export default Layout;
