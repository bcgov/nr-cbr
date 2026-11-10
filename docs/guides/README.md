# The how-to guides

The PDFs behind **How-to guide** under **Support** in CBR's left-hand menu. Each user is given the
one written for the roles they hold. The pattern follows `bcgov/nr-fam`'s guides.

## How it fits together

| Path | What it is |
| --- | --- |
| `docs/guides/cbr-guide.md` | The one source for every guide, with sections tagged by capability |
| `docs/guides/guide.css` | Print styles |
| `docs/guides/screenshots/` | PNGs the guide shows - see below |
| `docs/guides/build-manifest.json` | A hash of each guide's inputs, written by the build. Commit it |
| `frontend/src/routes/howToGuides.json` | The levels and the admin add-on: which guides exist |
| `frontend/src/routes/howToGuides.ts` | `howToGuideFor(roles)`: which guide a user gets |
| `frontend/scripts/build-guides.mjs` | Markdown in, PDFs out |
| `frontend/public/cbr-guide-*.pdf` | The 10 built guides. Committed, so deploys need no build step |

### Why 10 PDFs

FAM issues six roles. Five of them nest - View All, Inspector Level 1, Inspector Level 2, PENG,
Inspector Reviewer, each including the one before - so a user's working roles come down to one of
five levels. Administrator stands apart from that ladder, so each level comes with or without it.

| Level | Without Administrator | With Administrator |
| --- | --- | --- |
| View All (or Administrator alone) | `cbr-guide-view.pdf` | `cbr-guide-view-admin.pdf` |
| Inspector Level 1 | `cbr-guide-level-1.pdf` | `cbr-guide-level-1-admin.pdf` |
| Inspector Level 2 | `cbr-guide-level-2.pdf` | `cbr-guide-level-2-admin.pdf` |
| PENG | `cbr-guide-peng.pdf` | `cbr-guide-peng-admin.pdf` |
| Inspector Reviewer | `cbr-guide-reviewer.pdf` | `cbr-guide-reviewer-admin.pdf` |

The count changes only if the role list changes. The build refuses to produce any other number
until `EXPECTED_COUNT` in the script is changed on purpose, deletes any stray `cbr-guide-*.pdf`, and
a unit test checks that every possible mix of roles lands on one of the 10.

## Changing the words

Edit `cbr-guide.md`, then:

```sh
cd frontend
npm run guides:build
```

Commit the Markdown, the rebuilt PDFs and `build-manifest.json` together.

Wrap anything only some users need in a tag naming the capability it belongs to:

```markdown
<!-- guide: approve -->
## Signing off inspections
...
<!-- /guide -->
```

| Tag | Who sees it |
| --- | --- |
| `write` | Inspector Level 1 and up |
| `destructive` | Inspector Level 2 and up |
| `approve` | PENG and Inspector Reviewer |
| `review` | Inspector Reviewer |
| `admin` | Administrator |

A tag may name several, comma-separated (`<!-- guide: approve, admin -->`); the section shows when
the reader has any of them. `!` turns a name around: `<!-- guide: !admin -->` is for everyone who
is *not* an administrator - and `+` joins names that must all hold: `write+!destructive` is Inspector
Level 1 alone. The menu screenshots use these so each reader sees only the menu items they have. Tags do not nest, and an unknown name fails the build. Untagged text is
in every guide.

The capabilities are the ones `ROLE_CAPABILITIES` defines for the app, so the guide cannot promise
something the screens will not let the user do.

### Only what changed is rebuilt

Chromium stamps every PDF with a date and an id, so rebuilding an unchanged guide still produces a
"changed" file, and every commit of one adds another copy to the git history. The build therefore
hashes each guide's inputs - its share of the Markdown, the stylesheet, the screenshots it shows -
into `build-manifest.json`, and only prints a guide whose hash has moved. Editing the admin section
rewrites the 5 admin PDFs and leaves the other 5 untouched.

`npm run guides:build -- --force` reprints all 10. Bump `RENDER_VERSION` in the script when you
change something that shapes every PDF outside those inputs (page size, margins, footer).

## Screenshots

So far they are taken by hand. nr-fam captures its screenshots with a Playwright test that walks the running
app (`guides:capture`); CBR's end-to-end suite cannot sign in yet (see `playwright.config.ts`), so
that is not possible here until it can.

Until then, screenshots are added by hand: save it under `screenshots/`, reference it from the
Markdown (`![What it shows](screenshots/structure-tabs.png)`), and rebuild. Rules for any screenshot:

- **Test data and test accounts only.** The PDFs are downloaded and passed around; no real names.
- **A fixed window size**, so a re-shoot is comparable and the sizes stay consistent.
- **Small files.** Every guide that shows a screenshot carries its own copy of it, so a 1 MB image
  shown to everyone adds 10 MB across the guides. Crop to what the step needs and compress the PNG.
- **Named for what it shows**, not for its order, so sections can move without renaming files.
- **Tall, narrow images** (a menu, a dialog) take `class="narrow"` on an `<img>` tag, so they print
  at about screen width instead of filling the page.

## When a screen changes

The guide is in the same repository as the screens. A change that alters something the guide
describes should update the guide, and rebuild it, in the same pull request.
