import type { StructureDocument } from './documentsResponse';

/** The extensions the server sends to be shown — images and PDFs; everything else downloads. */
const SHOWN = new Set(['JPG', 'JPEG', 'PNG', 'GIF', 'PDF']);

/** The types a browser tab may show from this origin. */
const isShowable = (type: string) =>
  ['image/jpeg', 'image/png', 'image/gif', 'application/pdf'].includes(type);

const extensionOf = (document: StructureDocument): string =>
  (document.extension || document.filename?.split('.').pop() || '').toUpperCase();

/** Whether the file opens in a new tab rather than downloading. */
export const opensInTab = (document: StructureDocument): boolean =>
  SHOWN.has(extensionOf(document));

/** How long a file's object URL is kept, for the tab showing it to finish loading it. */
const URL_LIFETIME_MS = 60_000;

const download = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement('a');
  link.href = url;
  link.download = filename;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), URL_LIFETIME_MS);
};

/**
 * Opens a document: an image or a PDF in a new tab, anything else as a download under its own
 * name — as legacy's link downloads every file, with the browser's viewer for the ones it can
 * show.
 *
 * <p>The tab is opened at the click, before the file arrives: a tab opened after a wait is a
 * pop-up a browser may block. Should the server send something other than an image or a PDF, the
 * empty tab is closed and the file downloads instead — the type the server sends decides, not the
 * name.
 *
 * @throws whatever `fetchFile` throws, once any tab it opened is closed again
 */
export const openDocument = async (
  document: StructureDocument,
  fetchFile: () => Promise<Blob>,
): Promise<void> => {
  const filename = document.filename || `document-${document.id}`;
  const tab = opensInTab(document) ? window.open('', '_blank') : null;
  let blob: Blob;
  try {
    blob = await fetchFile();
  } catch (error) {
    tab?.close();
    throw error;
  }
  if (tab && isShowable(blob.type)) {
    const url = URL.createObjectURL(blob);
    tab.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), URL_LIFETIME_MS);
    return;
  }
  tab?.close();
  download(blob, filename);
};
