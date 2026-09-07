import type { CapacitorConfig } from '@capacitor/cli';

const liveReloadUrl = process.env['CAP_SERVER_URL'];
const localDevelopment = Boolean(liveReloadUrl);

const config: CapacitorConfig = {
  appId: 'com.bbcomplex.sms',
  appName: 'BBC SMS',
  webDir: 'dist/bbc-sms/browser',
  backgroundColor: '#F5F6FA',
  appendUserAgent: ' BBCSMSAndroid/1.0',
  loggingBehavior: localDevelopment ? 'debug' : 'none',
  server: liveReloadUrl
    ? {
        url: liveReloadUrl,
        cleartext: liveReloadUrl.startsWith('http://'),
        allowNavigation: ['10.0.2.2', 'localhost'],
      }
    : {
        androidScheme: 'https',
      },
  android: {
    allowMixedContent: false,
    backgroundColor: '#F5F6FA',
    webContentsDebuggingEnabled: localDevelopment,
  },
  plugins: {
    CapacitorHttp: {
      // Live reload uses the Angular proxy and relative /api URLs. The
      // distributable bundle uses the native bridge with its absolute HTTPS API.
      enabled: !localDevelopment,
    },
    SplashScreen: {
      launchShowDuration: 5000,
      launchAutoHide: false,
      backgroundColor: '#173552',
      androidScaleType: 'CENTER_CROP',
      showSpinner: false,
      splashFullScreen: true,
      splashImmersive: true,
    },
    StatusBar: {
      style: 'LIGHT',
      backgroundColor: '#173552',
      overlaysWebView: false,
    },
    Keyboard: {
      resize: 'native',
      resizeOnFullScreen: true,
    },
  },
};

export default config;
