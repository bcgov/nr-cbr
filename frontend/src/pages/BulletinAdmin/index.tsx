import { Save as SaveIcon } from '@carbon/icons-react';
import { Button, Column, Grid, InlineNotification, Loading, TextArea } from '@carbon/react';
import { useEffect, useMemo, useRef, useState, type FC, type SubmitEventHandler } from 'react';

import { BulletinView } from '@/components/Bulletin';
import FieldWithCounter from '@/components/core/FieldWithCounter';
import PageTitle from '@/components/core/PageTitle';
import { requiredLabel } from '@/utils/requiredLabel';

import { useNotification } from '@/context/notification/useNotification';
import { useBulletin, useBulletinPreview, useSaveBulletin } from '@/hooks/useBulletin';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { formatDateTime } from '@/utils/date';
import { byteLength } from '@/utils/textLimits';

import './index.scss';

const ID = 'bulletin-admin';
const FORM_ID = `${ID}-form`;

/** `BULLETIN.DESCRIPTION`: `VARCHAR2(2000 BYTE)`. */
export const MESSAGE_MAX = 2000;
export const REQUIRED = 'Bulletin Message is required.';
export const TOO_LONG = `Bulletin Message cannot exceed ${MESSAGE_MAX} characters.`;

type Field = 'message';

/**
 * Administration → Bulletin Message: the notice shown on the Dashboard.
 *
 * <p>Legacy equivalent: `showBulletinAdmin.do` → `bulletin_admin.jsp`, gated on
 * `/showBulletinAdmin`, the `ADMINISTRATOR` role's alone — a text area and Save / Cancel.
 *
 * <p>The message is HTML, as legacy's was, so a line can be bold or a link — but kept to a safe set
 * of tags: the server strips anything else, scripts and event handlers included, and what it saved
 * comes back into the box. A preview beside it shows the draft as users will see it, made safe by
 * the same rules, once typing pauses. It is required, as the column
 * is; Cancel puts back what is saved.
 */
const BulletinAdminPage: FC = () => {
  const bulletin = useBulletin();
  const save = useSaveBulletin();
  const { display } = useNotification();
  const [message, setMessage] = useState('');
  const [serverErrors, setServerErrors] = useState<Partial<Record<Field, string>>>({});

  const saved = bulletin.data?.message ?? '';
  // The box starts from what is saved, once it has loaded, and again after each save.
  useEffect(() => setMessage(saved), [saved]);

  // The preview box is at least as tall as the message box — followed as it is dragged taller or
  // shorter — and grows past it with a longer message.
  const box = useRef<HTMLTextAreaElement>(null);
  const [boxHeight, setBoxHeight] = useState<number>();
  const loaded = bulletin.isSuccess;
  useEffect(() => {
    const element = box.current;
    if (!element || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(() => setBoxHeight(element.offsetHeight));
    observer.observe(element);
    return () => observer.disconnect();
  }, [loaded]);

  const errors = useMemo(() => {
    if (message.trim() === '') return { message: REQUIRED };
    if (byteLength(message) > MESSAGE_MAX) return { message: TOO_LONG };
    return {};
  }, [message]);
  const { shown, leave, change, submit } = useFieldErrors<Field>(errors, serverErrors);

  const onSubmit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    if (!submit()) return;
    save.mutate(message, {
      onSuccess: () => {
        setServerErrors({});
        display({ kind: 'success', title: 'Bulletin saved', timeout: 4000 });
      },
      onError: (error) => {
        const fieldErrors = apiFieldErrors(error);
        setServerErrors(fieldErrors);
        if (!fieldErrors.message) {
          display({
            kind: 'error',
            title: 'The bulletin could not be saved',
            subtitle: apiErrorMessage(error, 'Try again. Contact support if this continues.'),
            timeout: 0,
          });
        }
      },
    });
  };

  const draft = useBulletinPreview(message);
  let preview;
  if (message.trim() === '') {
    preview = <p className="bulletin-admin__preview-note">Nothing to preview yet.</p>;
  } else if (draft.isError) {
    preview = (
      <p className="bulletin-admin__preview-note" data-testid={`${ID}-preview-error`}>
        The preview is unavailable. Saving still works.
      </p>
    );
  } else if (draft.data) {
    preview = <BulletinView id={`${ID}-preview`} message={draft.data.message} />;
  } else {
    preview = <Loading withOverlay={false} small description="Loading preview" />;
  }

  const cancel = () => {
    setMessage(saved);
    setServerErrors({});
  };

  return (
    <Grid fullWidth className="default-grid bulletin-admin">
      <PageTitle
        title="Bulletin Message"
        subtitle="The notice users see on the Dashboard, where they land after login."
        breadCrumbs={[{ name: 'Administration', path: '/admin' }]}
      >
        {/* Beside the heading, as Add Site's: on screen the whole time. Save submits the form
            through `form`, though it sits outside it. */}
        {bulletin.isSuccess && (
          <div className="bulletin-admin__header-actions">
            <Button
              kind="secondary"
              size="md"
              data-testid={`${ID}-cancel`}
              disabled={save.isPending}
              onMouseDown={holdFocus}
              onClick={cancel}
            >
              Cancel
            </Button>
            <Button
              kind="primary"
              size="md"
              type="submit"
              form={FORM_ID}
              renderIcon={SaveIcon}
              disabled={save.isPending}
              onMouseDown={holdFocus}
              data-testid={`${ID}-save`}
            >
              {save.isPending ? 'Saving…' : 'Save'}
            </Button>
          </div>
        )}
      </PageTitle>

      <Column sm={4} md={8} lg={16}>
        {bulletin.isLoading && <Loading withOverlay={false} small description="Loading" />}
        {bulletin.isError && (
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="The bulletin could not be loaded"
            subtitle="Saving now would replace it. Reload the page to try again."
            data-testid={`${ID}-load-error`}
          />
        )}
        {bulletin.isSuccess && (
          <form
            id={FORM_ID}
            noValidate
            onSubmit={onSubmit}
            data-testid={`${ID}-form`}
            className="bulletin-admin__editor"
          >
            <div>
              <FieldWithCounter used={byteLength(message)} limit={MESSAGE_MAX}>
                <TextArea
                  ref={box}
                  id={`${ID}-message`}
                  data-testid={`${ID}-message`}
                  labelText={requiredLabel('Bulletin Message', true)}
                  helperText="Basic HTML is kept: paragraphs, line breaks, bold, italic, lists and links."
                  rows={12}
                  value={message}
                  invalid={Boolean(shown.message)}
                  invalidText={shown.message}
                  onChange={(event) => {
                    setMessage(event.target.value);
                    change('message');
                  }}
                  onBlur={() => leave('message')}
                />
              </FieldWithCounter>

              {bulletin.data.updatedBy && (
                <p className="bulletin-admin__updated" data-testid={`${ID}-updated`}>
                  Last saved by {bulletin.data.updatedBy} on{' '}
                  {formatDateTime(bulletin.data.updatedAt)}
                </p>
              )}
            </div>

            {/* As users will see it, made safe by the server's own rules once typing pauses —
                beside the box on a wide screen, below it on a narrow one. */}
            <div>
              {/* A label as the message box's is, so the two boxes start level. */}
              <span className="cds--label" id={`${ID}-preview-label`}>
                Preview
              </span>
              <div
                className="bulletin-admin__preview"
                aria-live="polite"
                aria-labelledby={`${ID}-preview-label`}
                data-testid={`${ID}-preview`}
                style={{ minBlockSize: boxHeight }}
              >
                {preview}
              </div>
            </div>
          </form>
        )}
      </Column>
    </Grid>
  );
};

export default BulletinAdminPage;
