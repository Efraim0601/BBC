import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Capacitor, type PluginListenerHandle } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import { Keyboard } from '@capacitor/keyboard';
import { Network } from '@capacitor/network';
import { SplashScreen } from '@capacitor/splash-screen';
import { StatusBar, Style } from '@capacitor/status-bar';

type BackHandler = (canGoBack: boolean) => boolean;

/**
 * Small native bridge shared by the Angular shell.
 *
 * The web application remains the source of truth for every permission and
 * workflow. This service only adapts operating-system affordances such as the
 * Android back button, connectivity, keyboard and launch chrome.
 */
@Injectable({ providedIn: 'root' })
export class NativePlatformService {
  private readonly document = inject(DOCUMENT);
  private readonly listenerHandles: PluginListenerHandle[] = [];
  private initialized = false;
  private backHandler: BackHandler | null = null;

  private readonly hasNativeBridge = Capacitor.isNativePlatform();
  /** `nativePreview=1` gives local developers a faithful browser preview. */
  readonly isNative = this.hasNativeBridge || (
    typeof location !== 'undefined'
    && ['localhost', '127.0.0.1'].includes(location.hostname)
    && new URLSearchParams(location.search).get('nativePreview') === '1'
  );
  readonly online = signal(typeof navigator === 'undefined' ? true : navigator.onLine);
  readonly keyboardVisible = signal(false);

  initialize(): void {
    if (this.initialized) return;
    this.initialized = true;

    this.document.body.classList.toggle('native-app', this.isNative);
    window.addEventListener('online', this.onBrowserOnline);
    window.addEventListener('offline', this.onBrowserOffline);

    if (!this.hasNativeBridge) return;
    window.setTimeout(() => void this.finishLaunch(), 6000);
    void this.initializeNativePlugins();
  }

  async finishLaunch(): Promise<void> {
    if (!this.hasNativeBridge) return;
    try {
      await SplashScreen.hide({ fadeOutDuration: 280 });
    } catch {
      // The web content is already usable; a plugin failure must not block it.
    }
  }

  registerBackHandler(handler: BackHandler): () => void {
    this.backHandler = handler;
    return () => {
      if (this.backHandler === handler) this.backHandler = null;
    };
  }

  private readonly onBrowserOnline = (): void => this.online.set(true);
  private readonly onBrowserOffline = (): void => this.online.set(false);

  private async initializeNativePlugins(): Promise<void> {
    try {
      await StatusBar.setOverlaysWebView({ overlay: false });
      await StatusBar.setBackgroundColor({ color: '#173552' });
      await StatusBar.setStyle({ style: Style.Light });
    } catch {
      // Older devices can reject individual status-bar operations.
    }

    try {
      this.online.set((await Network.getStatus()).connected);
      this.listenerHandles.push(
        await Network.addListener('networkStatusChange', ({ connected }) => this.online.set(connected)),
      );
    } catch {
      // Browser online/offline events remain available as a fallback.
    }

    try {
      this.listenerHandles.push(
        await Keyboard.addListener('keyboardWillShow', () => {
          this.keyboardVisible.set(true);
          this.document.body.classList.add('native-keyboard-open');
        }),
        await Keyboard.addListener('keyboardWillHide', () => {
          this.keyboardVisible.set(false);
          this.document.body.classList.remove('native-keyboard-open');
        }),
      );
    } catch {
      // The layout still follows normal viewport resizing if this is unavailable.
    }

    try {
      this.listenerHandles.push(
        await CapacitorApp.addListener('backButton', ({ canGoBack }) => {
          if (this.backHandler?.(canGoBack)) return;
          if (canGoBack && window.history.length > 1) {
            window.history.back();
          } else {
            void CapacitorApp.minimizeApp();
          }
        }),
      );
    } catch {
      // Android's default behavior is retained if registration fails.
    }
  }
}
