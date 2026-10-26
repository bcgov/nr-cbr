import { fileURLToPath } from 'node:url';
import { resolve } from 'path';

import react from '@vitejs/plugin-react';
import { playwright } from '@vitest/browser-playwright';
import { defineConfig, loadEnv } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';
import tsconfigPaths from 'vite-tsconfig-paths';
import { configDefaults } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const projectRootDir = fileURLToPath(new URL('.', import.meta.url));
  const define = {
    global: {},
  };
  const devHost = env.VITE_DEV_HOST ?? 'localhost';
  const devPort = Number(env.VITE_DEV_PORT ?? 3000);
  const backendTarget = env.VITE_DEV_BACKEND_TARGET ?? 'http://localhost:8080';
  const hmrPort = env.VITE_HMR_PORT ? Number(env.VITE_HMR_PORT) : devPort;
  const hmrHost = env.VITE_HMR_HOST ?? devHost;
  const hmrProtocolEnv = env.VITE_HMR_PROTOCOL ?? 'ws';
  const hmrProtocol = hmrProtocolEnv === 'wss' ? 'wss' : 'ws';
  // Carbon's own SCSS trips Dart Sass's `mixed-decls` / `global-builtin` deprecations (1200+ warnings
  // per build, none of them from our stylesheets and none actionable until Carbon updates upstream).
  // quietDeps silences deprecations raised inside node_modules while still reporting any we introduce
  // in src/styles.
  const css = {
    preprocessorOptions: {
      scss: { quietDeps: true },
    },
  };

  return {
    define,
    css,
    resolve: {
      alias: {
        '@': resolve(projectRootDir, 'src'),
      },
    },
    plugins: [
      react(),
      tsconfigPaths(),
      // CBR has no offline mode, so no service worker. selfDestroying ships a sw.js that unregisters
      // the one this app used to install and clears its caches, so browsers that already have it
      // stop serving a stale build. Drop the plugin once that has had time to reach every browser
      // (Caddy sends sw.js no-store, so the next visit fetches it).
      VitePWA({ selfDestroying: true, injectRegister: false, manifest: false }),
    ],
    base: env.VITE_BASE_PATH || '/',
    build: {
      chunkSizeWarningLimit: 1024,
      outDir: 'dist',
    },
    optimizeDeps: {
      include: ['@tanstack/react-query', 'axios', 'oidc-client-ts', 'react-dom/client'],
    },
    server: {
      host: devHost,
      port: devPort,
      hmr: {
        overlay: false,
        protocol: hmrProtocol,
        host: hmrHost,
        port: hmrPort,
      },
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
    preview: {
      port: devPort,
    },
    test: {
      env,
      globals: true,
      exclude: [...configDefaults.exclude, 'dist/**', 'build/**'],
      coverage: {
        provider: 'v8',
        reporter: ['lcov', 'cobertura', 'html', 'json', 'text'],
        reportsDirectory: './coverage',
        all: true,
        exclude: [
          '**/node_modules/**',
          '**/tests/**',
          '**/*.test.{ts,tsx}',
          '**/vite-env.d.ts',
          '**/types/**',
          '**/constants/**',
          '**/config/react-query/*',
          '**/config/tests/*',
          '**/*.env.ts',
          '**/*.scss',
          '**/*.css',
          '**/*.d.ts',
          '**/types.ts',
          '**/main.tsx',
          '**/App.tsx',
        ],
        include: ['src/**/*.ts', 'src/**/*.tsx'],
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
      },
      projects: [
        {
          resolve: {
            alias: {
              '@': resolve(projectRootDir, 'src'),
            },
          },
          css,
          plugins: [react(), tsconfigPaths()],
          test: {
            name: 'node',
            setupFiles: [
              './src/config/tests/setup-env.ts',
              './src/config/tests/custom-matchers.ts',
            ],
            environment: 'happy-dom',
            include: ['src/**/*.unit.test.{ts,tsx}'],
          },
        },
        {
          resolve: {
            alias: {
              '@': resolve(projectRootDir, 'src'),
            },
          },
          css,
          plugins: [react(), tsconfigPaths()],
          test: {
            name: 'browser',
            setupFiles: [
              './src/config/tests/setup-browser.ts',
              './src/config/tests/custom-matchers.ts',
            ],
            browser: {
              enabled: true,
              // A function from its own package since Vitest 4, rather than a name.
              provider: playwright(),
              instances: [{ browser: 'chromium' }],
            },
            include: ['src/**/*.browser.test.{ts,tsx}'],
            // Browser mode starts a real Chromium context per worker. Unbounded on a CI runner
            // that has a couple of cores, workers time out starting up and Vitest reports
            // "Failed to import test file … Vitest failed to find the runner" — the whole file
            // never loads, so no test in it even runs. The giveaway is a `prepare` time far
            // larger than the test time (1061s of prepare for 22s of tests).
            //
            // Capped rather than serialised: two workers keep most of the parallelism while
            // leaving the runner enough headroom to start them. Left uncapped locally, where
            // there are cores to spare.
            // (Vitest 4 dropped `minWorkers`; the floor is one worker regardless.)
            //
            // A group of its own when capped: Vitest 5 will not run two projects with different
            // `maxWorkers` side by side, and fails the run before any test starts ("have different
            // 'maxWorkers' but same 'sequence.groupOrder'"). The node project, in group 0, runs
            // first; this one after it.
            ...(process.env.CI ? { maxWorkers: 2, sequence: { groupOrder: 1 } } : {}),
          },
        },
      ],
    },
  };
});
