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
  // A new version installed and took control of this page (see "Update model" in sw.js). This page is
  // still the previous version as a whole, so offer a reload. The very first install stays silent.
  const hadController = Boolean(navigator.serviceWorker.controller);
  let offered = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadController || offered) return;
    offered = true;
    toast({ message: 'Update available', kind: 'info', iconName: 'sparkle', action: { label: 'Reload', onClick: () => location.reload() } });
  });
  const register = () => {
    navigator.serviceWorker.register('./sw.js').catch((err) => console.warn('[pwa] service worker registration failed', err));
  };
  if (document.readyState === 'complete') register();
  else window.addEventListener('load', register, { once: true });
}
