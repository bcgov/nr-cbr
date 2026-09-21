import { createContext } from 'react';

export type LayoutContextType = {
  isSideNavExpanded: boolean;
  toggleSideNav: () => void;
  closeSideNav: () => void;
  /**
   * Expand the side nav, whatever state it is in.
   *
   * <p>Distinct from {@link toggleSideNav} because the rail's section buttons must only ever open:
   * a toggle would collapse the nav again if one were ever pressed while it was already expanded,
   * which is the opposite of what the control promises.
   */
  openSideNav: () => void;
  isHeaderPanelOpen: boolean;
  toggleHeaderPanel: () => void;
  closeHeaderPanel: () => void;
};

export const LayoutContext = createContext<LayoutContextType | undefined>(undefined);
