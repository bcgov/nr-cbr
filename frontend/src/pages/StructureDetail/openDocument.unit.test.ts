import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { openDocument, opensInTab } from './openDocument';

import type { StructureDocument } from './documentsResponse';

const document = (overrides: Partial<StructureDocument> = {}): StructureDocument => ({
  id: '12',
  inspectionId: null,
  inspectionDate: null,
  attachmentType: { code: 'PHOTO', description: 'Photograph' },
  created: '2020-01-01',
  extension: 'JPG',
  description: null,
  filename: 'deck.jpg',
  ...overrides,
});

const tab = () => ({ close: vi.fn(), location: { href: '' } });

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:file');
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('opensInTab', () => {
  it('opens images and PDFs in a tab, and downloads anything else', () => {
    expect(opensInTab(document({ extension: 'JPG' }))).toBe(true);
    expect(opensInTab(document({ extension: 'pdf' }))).toBe(true);
    expect(opensInTab(document({ extension: 'DOCX', filename: 'r.docx' }))).toBe(false);
    expect(opensInTab(document({ extension: null, filename: 'plan.PDF' }))).toBe(true);
  });
});

describe('openDocument', () => {
  it('shows a photo in the tab it opened at the click', async () => {
    const opened = tab();
    const open = vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);

    await openDocument(document(), () => Promise.resolve(new Blob(['x'], { type: 'image/jpeg' })));

    expect(open).toHaveBeenCalledWith('', '_blank');
    expect(opened.location.href).toBe('blob:file');
    expect(opened.close).not.toHaveBeenCalled();
  });

  it('downloads under the file name, with no tab, what a browser should not show', async () => {
    const open = vi.spyOn(window, 'open');
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await openDocument(document({ extension: 'DOCX', filename: 'report.docx' }), () =>
      Promise.resolve(new Blob(['x'], { type: 'application/octet-stream' })),
    );

    expect(open).not.toHaveBeenCalled();
    expect(click).toHaveBeenCalledTimes(1);
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('report.docx');
  });

  it('closes the tab and downloads when the server sends something it should not show', async () => {
    // The server's type decides, not the name: an "image" that arrives as a download stays one.
    const opened = tab();
    vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await openDocument(document(), () =>
      Promise.resolve(new Blob(['x'], { type: 'application/octet-stream' })),
    );

    expect(opened.close).toHaveBeenCalled();
    expect(opened.location.href).toBe('');
    expect(click).toHaveBeenCalledTimes(1);
  });

  it('closes the tab it opened when the file cannot be fetched', async () => {
    const opened = tab();
    vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window);

    await expect(openDocument(document(), () => Promise.reject(new Error('gone')))).rejects.toThrow(
      'gone',
    );
    expect(opened.close).toHaveBeenCalled();
  });
});
