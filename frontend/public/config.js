// Safety net: ensure window.config exists before the runtime-injected values load.
//
// Replaced wholesale at container start by docker-entrypoint.sh. This copy is what serves during
// local development and what index.html falls back to if the entrypoint has not run.
window.config = window.config || {};
