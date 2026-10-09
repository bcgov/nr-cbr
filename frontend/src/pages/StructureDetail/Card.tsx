import type { CarbonIconType } from '@carbon/icons-react';
import type { FC, ReactNode } from 'react';

type Props = {
  title: string;
  icon: CarbonIconType;
  testId: string;
  /** The card's own action — "Edit …" or "Add …" — at the right of its title, as nr-fspts'. */
  action?: ReactNode;
  children: ReactNode;
};

/**
 * One section of a tab: a white card on the tab's grey pane, titled with an icon — nr-fspts' FSP
 * Information tab (`fsp-info__tile` with an icon `fsp-info__section-title`, its action in the
 * tile's header).
 */
const Card: FC<Props> = ({ title, icon: Icon, testId, action, children }) => (
  <section className="structure-detail__card" data-testid={testId}>
    <header className="structure-detail__card-header">
      <h2 className="structure-detail__card-title">
        <Icon size={20} aria-hidden="true" />
        <span>{title}</span>
      </h2>
      {action}
    </header>
    {children}
  </section>
);

export default Card;
