/** How long a file's object URL is kept, for the browser to finish saving it. */
const URL_LIFETIME_MS = 60_000;

/** Saves a file the app was sent — a report, say — under this name, as a download. */
export const saveFile = (blob: Blob, filename: string): void => {
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement('a');
  link.href = url;
  link.download = filename;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), URL_LIFETIME_MS);
};
