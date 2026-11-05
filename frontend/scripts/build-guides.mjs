/**
 * Builds the how-to guides: one tagged Markdown file in, 10 PDFs out.
 *
 *   npm run guides:build            rebuild only the guides whose inputs changed
 *   npm run guides:build -- --force rebuild all 10
 *
 * The source is docs/guides/cbr-guide.md. Each section that only some users need is wrapped in
 *
 *   <!-- guide: approve -->
 *   ...
 *   <!-- /guide -->
 *
 * naming one or more capabilities (comma-separated; the section is kept when the reader has any of
 * them). `!name` means the reader lacks it - `<!-- guide: !admin -->` is for everyone but
 * administrators - and `+` joins names that must all hold: `write+!destructive` is Level 1 alone. Untagged text goes in every guide. The capabilities and the 10 combinations come from
 * src/routes/howToGuides.json, which the app reads too, so the menu always links a PDF that exists.
 *
 * Why the hash skip: Chromium stamps every PDF with a creation date and a document id, so a PDF
 * rebuilt from unchanged text is still a "changed" binary to git, and committing it adds another
 * copy to the history. Each guide's inputs - its share of the Markdown, the stylesheet, the
 * screenshots it shows, and RENDER_VERSION - are hashed into docs/guides/build-manifest.json, and a
 * guide is only printed when its hash moves. A fix to the admin chapter rewrites the 5 admin PDFs
 * and leaves the other 5 byte-for-byte alone.
 *
 * The PDFs are committed to frontend/public/, so deploys need no build step and no Chromium.
 */
import { createHash } from 'node:crypto';
import { access, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import MarkdownIt from 'markdown-it';

import guides from '../src/routes/howToGuides.json' with { type: 'json' };

const here = dirname(fileURLToPath(import.meta.url));
const guidesDir = resolve(here, '../../docs/guides');
const publicDir = resolve(here, '../public');
const sourceFile = resolve(guidesDir, 'cbr-guide.md');
const manifestFile = resolve(guidesDir, 'build-manifest.json');

/**
 * Bump when something that shapes the PDF changes outside the hashed inputs - the page size, the
 * margins, the footer below, markdown-it options - so every guide is reprinted once.
 */
const RENDER_VERSION = 2;

/**
 * The number of guides. Derived from the table (5 levels x with/without admin), and checked here so
 * that a change to the role list is a deliberate edit, not a quiet new set of files.
 */
const EXPECTED_COUNT = 10;

const force = process.argv.includes('--force');

/** The 10 guides: every level, without and with the administration chapter. */
const variants = guides.levels.flatMap((level) =>
  [false, true].map((admin) => ({
    file: `cbr-guide-${level.id}${admin ? guides.admin.suffix : ''}.pdf`,
    title: `How to use CBR - ${level.title}${admin ? ` and ${guides.admin.title}` : ''}`,
    capabilities: new Set(
      admin ? [...level.capabilities, guides.admin.capability] : level.capabilities,
    ),
  })),
);

if (variants.length !== EXPECTED_COUNT) {
  throw new Error(
    `howToGuides.json describes ${variants.length} guides, expected ${EXPECTED_COUNT}. ` +
      'If the role list changed on purpose, update EXPECTED_COUNT and the tests with it.',
  );
}

const known = new Set([
  ...guides.levels.flatMap((level) => level.capabilities),
  guides.admin.capability,
]);

/**
 * The Markdown a reader with these capabilities sees: untagged text, plus every tagged section
 * naming one of their capabilities. Tags do not nest - a guide that needs nesting is a guide that
 * needs restructuring - and an unknown capability is a typo, so both fail the build.
 */
const select = (source, capabilities) => {
  const kept = [];
  let open = null;
  source.split('\n').forEach((line, index) => {
    const start = line.match(/^<!--\s*guide:\s*([a-z!+, -]+?)\s*-->\s*$/);
    const end = /^<!--\s*\/guide\s*-->\s*$/.test(line);
    const where = `cbr-guide.md:${index + 1}`;
    if (start) {
      if (open)
        throw new Error(`${where}: guide sections do not nest (one opened at line ${open.line})`);
      const tags = start[1]
        .split(',')
        .map((tag) => tag.trim())
        .filter(Boolean);
      // Each comma-separated tag is one or more names joined by + (all must hold); ! negates a name.
      const terms = tags.map((tag) => tag.split('+').map((name) => name.trim()));
      for (const name of terms.flat()) {
        if (!known.has(name.replace(/^!/, '')))
          throw new Error(`${where}: unknown capability "${name}"`);
      }
      const holds = (name) =>
        name.startsWith('!') ? !capabilities.has(name.slice(1)) : capabilities.has(name);
      open = { line: index + 1, shown: terms.some((names) => names.every(holds)) };
    } else if (end) {
      if (!open) throw new Error(`${where}: <!-- /guide --> with no section open`);
      open = null;
    } else if (!open || open.shown) {
      kept.push(line);
    }
  });
  if (open) throw new Error(`cbr-guide.md:${open.line}: guide section never closed`);
  return kept.join('\n');
};

const markdown = new MarkdownIt({ html: true, linkify: true, typographer: true });

/** The screenshots a rendered guide shows, relative to docs/guides. */
const imagesIn = (body) => [...body.matchAll(/<img[^>]+src="([^"]+)"/g)].map((match) => match[1]);

const exists = async (path) =>
  access(path).then(
    () => true,
    () => false,
  );

/** Everything that decides what this guide's PDF looks like. */
const hashOf = async (variant, selected, css, images) => {
  const hash = createHash('sha256');
  hash.update(`render ${RENDER_VERSION}\n${variant.title}\n${css}\n${selected}\n`);
  for (const image of images) {
    hash.update(`image ${image}\n`);
    const path = resolve(guidesDir, image);
    hash.update((await exists(path)) ? await readFile(path) : 'missing');
  }
  return hash.digest('hex');
};

const page = (title, css, body) => `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>${title}</title>
<style>${css}</style>
</head>
<body>${body}</body>
</html>`;

const build = async () => {
  const source = await readFile(sourceFile, 'utf8');
  const css = await readFile(resolve(guidesDir, 'guide.css'), 'utf8');
  const manifest = (await exists(manifestFile))
    ? JSON.parse(await readFile(manifestFile, 'utf8'))
    : {};
  const next = {};

  // Work out what changed before starting a browser, so an all-unchanged run takes no time.
  const pending = [];
  for (const variant of variants) {
    const selected = select(source, variant.capabilities);
    const body = markdown.render(selected);
    const images = imagesIn(body);
    for (const image of images) {
      if (!(await exists(resolve(guidesDir, image)))) {
        console.warn(`  missing screenshot: ${image} (in ${variant.file})`);
      }
    }
    const hash = await hashOf(variant, selected, css, images);
    next[variant.file] = hash;
    const unchanged =
      manifest[variant.file] === hash && (await exists(resolve(publicDir, variant.file)));
    if (force || !unchanged) pending.push({ variant, body });
  }

  if (pending.length > 0) {
    const { chromium } = await import('@playwright/test');
    const browser = await chromium.launch();
    try {
      for (const { variant, body } of pending) {
        // Written beside the screenshots so their relative paths resolve: Chromium will not load
        // file:// images into a page set with setContent.
        const scratch = resolve(guidesDir, `.${variant.file}.html`);
        await writeFile(scratch, page(variant.title, css, body), 'utf8');
        const tab = await browser.newPage();
        try {
          await tab.goto(`file://${scratch}`, { waitUntil: 'networkidle' });
          const pdf = await tab.pdf({
            format: 'Letter',
            printBackground: true,
            margin: { top: '18mm', bottom: '18mm', left: '16mm', right: '16mm' },
            displayHeaderFooter: true,
            // The BC Gov yellow rule across the top of every page. Styled inline, like the footer;
            // print-color-adjust, because Chromium leaves header backgrounds out otherwise.
            headerTemplate: `<div style="width:100%;padding:0 16mm;margin-top:8mm;">
                <div style="height:3px;background:#fcba19;-webkit-print-color-adjust:exact;
                print-color-adjust:exact;"></div></div>`,
            // Styled inline: the footer is rendered outside the document and gets none of its CSS.
            footerTemplate: `<div style="width:100%;font-size:8pt;color:#525252;
                font-family:sans-serif;padding:0 16mm;display:flex;justify-content:space-between;">
                <span>${variant.title}</span><span class="pageNumber"></span></div>`,
          });
          await writeFile(resolve(publicDir, variant.file), pdf);
          console.log(`  built     ${variant.file} (${(pdf.length / 1024).toFixed(0)} KB)`);
        } finally {
          await tab.close();
          await rm(scratch, { force: true });
        }
      }
    } finally {
      await browser.close();
    }
  }

  // Anything named like a guide that is not one of the 10 is left over from an older table.
  const expected = new Set(variants.map((variant) => variant.file));
  for (const file of await readdir(publicDir)) {
    if (/^cbr-guide-.*\.pdf$/.test(file) && !expected.has(file)) {
      await rm(resolve(publicDir, file));
      console.log(`  removed   ${file} (no longer a guide)`);
    }
  }

  await writeFile(manifestFile, `${JSON.stringify(next, null, 2)}\n`, 'utf8');

  const onDisk = (await readdir(publicDir)).filter((file) => /^cbr-guide-.*\.pdf$/.test(file));
  if (onDisk.length !== EXPECTED_COUNT) {
    throw new Error(
      `public/ holds ${onDisk.length} guides after the build, expected ${EXPECTED_COUNT}`,
    );
  }
  console.log(
    `Guides: ${pending.length} built, ${variants.length - pending.length} unchanged, ${onDisk.length} in public/.`,
  );
};

await build();
