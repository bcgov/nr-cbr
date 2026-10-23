import type { CancelablePromise } from '@/config/api/CancelablePromise';

import { HttpClient } from '@/config/api/types';

/** The bulletin as `/api/v1/bulletin` returns it. */
export type BulletinResponse = {
  /** HTML the server has kept to a safe set of tags. Empty when there is no bulletin. */
  message: string;
  /** Who last saved it; null when there is no bulletin. */
  updatedBy: string | null;
  /** When, ISO local date-time; null when there is no bulletin. */
  updatedAt: string | null;
};

/** A draft as Save would keep it. */
export type BulletinPreview = {
  /** HTML kept to the safe set of tags. */
  message: string;
};

/**
 * The bulletin — the message shown on the Dashboard, which an administrator
 * maintains (legacy's Administration → Bulletin Message).
 */
export class BulletinService extends HttpClient {
  /** The bulletin. Anyone who can read. */
  bulletin(): CancelablePromise<BulletinResponse> {
    return this.doRequest<BulletinResponse>(this.config, {
      method: 'GET',
      url: '/v1/bulletin',
    });
  }

  /**
   * Replaces the message. An administrator's; 400 with `fieldErrors.message` when it is empty or
   * too long.
   */
  saveBulletin(message: string): CancelablePromise<BulletinResponse> {
    return this.doRequest<BulletinResponse>(this.config, {
      method: 'PUT',
      url: '/v1/bulletin',
      body: { message },
    });
  }

  /** A draft as Save would keep it, made safe; stores nothing. An administrator's. */
  previewBulletin(message: string): CancelablePromise<BulletinPreview> {
    return this.doRequest<BulletinPreview>(this.config, {
      method: 'POST',
      url: '/v1/bulletin/preview',
      body: { message },
    });
  }
}
