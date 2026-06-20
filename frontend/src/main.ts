// SockJS (used by the STOMP client) expects a Node-style `global` symbol,
// which the esbuild browser bundle does not define — shim it before anything loads.
(globalThis as unknown as { global: typeof globalThis }).global ||= globalThis;

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig).catch((err) => console.error(err));
