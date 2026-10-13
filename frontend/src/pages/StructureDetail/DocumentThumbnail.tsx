import { useEffect, useState } from 'react';

import { isImage } from './openDocument';

import type { StructureDocument } from './documentsResponse';
import type { FC } from 'react';

import API from '@/services/APIs';

type Props = {
  structureId: string;
  document: StructureDocument;
};

/**
 * A 120px square picture of a document, so the user can see which file they are changing — nr-frep's
 * photo thumbnail (`chr-checklist__thumb`). An image is fetched and drawn, cropped to fill the
 * square; anything else, or an image that will not load, is a box of the same size naming the file.
 *
 * <p>Only an image is fetched: a PDF or a Word file cannot be drawn, and fetching it would be
 * waste. The object URL is released when the thumbnail goes.
 */
const DocumentThumbnail: FC<Props> = ({ structureId, document }) => {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    if (!isImage(document)) return undefined;
    let url: string | null = null;
    let gone = false;
    // From a resolved promise, so even a fetch that throws as it starts ends in the catch below
    // rather than in the dialog.
    Promise.resolve()
      .then(() => API.structureSearch.getDocumentFile(structureId, document.id))
      .then((blob) => {
        // The type the server sent decides, not the name: only an image is drawn.
        if (gone || !blob?.type.startsWith('image/')) return;
        url = URL.createObjectURL(blob);
        setSrc(url);
      })
      .catch(() => {
        // No picture, then: the box names the file instead.
      });
    return () => {
      gone = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [structureId, document]);

  const name = document.filename || 'Saved file';
  return src ? (
    <img
      className="structure-detail__thumb"
      src={src}
      alt={document.description || name}
      data-testid="document-thumbnail"
    />
  ) : (
    <span
      className="structure-detail__thumb structure-detail__thumb--placeholder"
      title={name}
      data-testid="document-thumbnail-placeholder"
    >
      {name}
    </span>
  );
};

export default DocumentThumbnail;
