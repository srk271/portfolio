// PWA helpers: service worker registration (+ update toast) and the install prompt.
import { emit } from './bus.js';
import { toast } from './ui/toast.js';

let deferredPrompt = null;

export function captureInstallPrompt() {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    emit('install:available', true);
  });
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    emit('install:available', false);
    toast({ message: 'WiSeCRiB installed', kind: 'success' });
  });
}

export const canInstall = () => Boolean(deferredPrompt);

export const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;

export async function promptInstall() {
  if (!deferredPrompt) return 'unavailable';
  const prompt = deferredPrompt;
  deferredPrompt = null;
  prompt.prompt();
  const { outcome } = await prompt.userChoice;
  emit('install:available', false);
  return outcome;
}

export function registerServiceWorker() {
  if (!('serviceWorker' in navigator) || !/^https?:$/.test(location.protocol)) return;
  const register = () => {
    navigator.serviceWorker
      .register('./sw.js')
      .then((registration) => {
        registration.addEventListener('updatefound', () => {
          const worker = registration.installing;
          if (!worker || !navigator.serviceWorker.controller) return; // first install, not an update
          worker.addEventListener('statechange', () => {
            if (worker.state === 'activated') {
              toast({ message: 'Update available', kind: 'info', iconName: 'sparkle', action: { label: 'Reload', onClick: () => location.reload() } });
            }
          });
        });
      })
      .catch((err) => console.warn('[pwa] service worker registration failed', err));
  };
  if (document.readyState === 'complete') register();
  else window.addEventListener('load', register, { once: true });
}
