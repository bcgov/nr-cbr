/**
 * One suggestion from `/api/v1/clients` — a Forest Client *location*, not a client.
 *
 * <p>A site records its Designated Maintainer as the pair `(clientNumber, clientLocnCode)`, so a
 * client with offices in two towns is two suggestions. That is why `clientLocnName` and `city` are
 * here: they are the only things that tell those two apart on screen.
 */
export type ClientSuggestion = {
  clientNumber: string;
  clientLocnCode: string;
  clientName: string;
  /** A division or joint-venture name. Usually null — most clients have one location. */
  clientLocnName: string | null;
  city: string | null;
};
