# Web demo

From the repository root: `npm run dev`, then open http://127.0.0.1:4178. Set `FOLDLIGHT_PORT` to use another local port. No npm runtime dependencies.

`npm test` checks angle limits, damping, device detection and screenshot layout. `npm run build` writes a static allowlisted output with content-versioned asset URLs to `build/WebRelease/duo-static` and a SHA-256 manifest alongside it.

Serve the output over HTTPS to enable phone motion. Use your own static host; no backend is needed. The optional analytics service is separate. Platform download links point to hosted binaries rather than requiring installers in the checkout.

The source ships an original vector home-screen illustration. Users can select their own photo. The live product may use different demo artwork. Language switching updates content, metadata and guide links. See the root [README](../README.md).
