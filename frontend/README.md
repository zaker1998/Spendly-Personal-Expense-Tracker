# Spendly — frontend

Angular 19 SPA: standalone components, signals, `OnPush` change detection and
reactive forms. No UI library — shared building blocks live in
`src/styles.css`, icons are inline SVG (`src/app/shared/icon.component.ts`).

```bash
npm install
npm start            # http://localhost:4200, expects the API on http://localhost:8080/api
```

| Script                 | What it does                                                                           |
| ---------------------- | -------------------------------------------------------------------------------------- |
| `npm run build`        | Production build into `dist/`                                                          |
| `npm run test:ci`      | Unit tests in headless Chrome, with coverage                                           |
| `npm run lint`         | angular-eslint, including OnPush and control-flow rules                                |
| `npm run format:check` | Prettier                                                                               |
| `npm run test:e2e`     | Playwright + axe-core against a running stack (`docker compose up` from the repo root) |

```
src/app/
  core/      API client, auth (session, guard, interceptor), models
  layout/    app shell, sign-in frame, toasts
  pages/     one folder per route
  shared/    brand mark, icons
```

See the [main README](../README.md) for the architecture and how it is deployed.
