import { Launch } from '@carbon/icons-react';
import { Link } from 'react-router-dom';

import type { FC, ReactNode } from 'react';

import './index.scss';

type Props = {
  children: ReactNode;
  className?: string;
} & (
  | {
      /** An address outside CBR. */
      href: string;
      to?: never;
    }
  | {
      /** A route inside CBR — resolved against the app's base path, as any in-app link is. */
      to: string;
      href?: never;
    }
);

/**
 * A link that opens in a new tab, with the trailing launch icon that says so — nr-frep's
 * `ExternalLink`, which CBR follows.
 *
 * <p>The icon is the convention users read as "this goes somewhere else"; without it a link that
 * takes a new tab looks identical to one that navigates in place. It is `aria-hidden` because a
 * glyph announced as "launch" tells a screen-reader user nothing; the visually-hidden suffix carries
 * the same information instead.
 *
 * <p>`rel="noopener noreferrer"` is not optional: without `noopener` the opened page gets a handle
 * on this window through `window.opener` and can navigate it.
 *
 * <p>Takes `to` as well as nr-frep's `href`, for a CBR page opened in its own tab — a site from the
 * structure it stands on — so the route still picks up the app's base path.
 */
export const ExternalLink: FC<Props> = ({ href, to, children, className }) => {
  const classes = ['external-link', className].filter(Boolean).join(' ');
  const content = (
    <>
      {children}
      <Launch size={16} className="external-link__icon" aria-hidden="true" />
      <span className="cds--visually-hidden"> (opens in a new tab)</span>
    </>
  );
  return to === undefined ? (
    <a href={href} target="_blank" rel="noopener noreferrer" className={classes}>
      {content}
    </a>
  ) : (
    <Link to={to} target="_blank" rel="noopener noreferrer" className={classes}>
      {content}
    </Link>
  );
};

export default ExternalLink;
