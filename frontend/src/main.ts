// Polyfill for dependencies (e.g. @stomp/stompjs) that expect Node-style `global` in the browser
(window as unknown as { global?: unknown }).global = typeof window !== 'undefined' ? window : typeof self !== 'undefined' ? self : (globalThis as unknown);

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
