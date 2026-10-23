import type { FC } from 'react';

import { useBulletin } from '@/hooks/useBulletin';

import './index.scss';

/**
 * A bulletin message as users see it: the heading and the message beneath it, or a word that there
 * is none. The Dashboard's, and the editor's preview.
 *
 * <p>The message is HTML, rendered as such: the server keeps it to a safe set of tags — paragraphs,
 * line breaks, bold and italic, lists, links — on save, on read and in a preview, so a script never
 * reaches the page (BulletinService).
 */
export const BulletinView: FC<{ id: string; message: string }> = ({ id, message }) => (
  <section className="cbr-bulletin" data-testid={`${id}-bulletin`}>
    <h2 className="cbr-bulletin__heading">Bulletin</h2>
    {message ? (
      <div
        className="cbr-bulletin__message"
        data-testid={`${id}-bulletin-message`}
        // Sanitized by the server: see above.
        dangerouslySetInnerHTML={{ __html: message }}
      />
    ) : (
      <p className="cbr-bulletin__none" data-testid={`${id}-no-bulletin`}>
        There is no bulletin at the moment.
      </p>
    )}
  </section>
);

/**
 * The bulletin an administrator has set — legacy's welcome-page "Bulletin". Nothing while it loads
 * or if it could not be read: it is a notice, and its absence stops nothing.
 */
const Bulletin: FC<{ id: string }> = ({ id }) => {
  const { data } = useBulletin();
  return data ? <BulletinView id={id} message={data.message} /> : null;
};

export default Bulletin;
