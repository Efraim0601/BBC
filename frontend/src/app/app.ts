import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { GlobalLoadingService } from './core/global-loading.service';
import { I18nService } from './core/i18n.service';

@Component({
  selector: 'bbc-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: `
    <div class="app-frame" [attr.aria-busy]="loading.blocking()">
      <router-outlet />

      @if (loading.blocking()) {
        <div class="global-loading-mask" role="status" aria-live="polite" aria-atomic="true">
          <div class="global-loading-card">
            <div class="global-loading-logo" aria-hidden="true">
              <img src="bbc-logo.png" alt="" />
              <span></span>
            </div>
            <div>
              <p>{{ fr() ? 'Chargement de la page…' : 'Loading page…' }}</p>
              <small>{{ fr() ? 'Préparation des données, veuillez patienter.' : 'Preparing your data, please wait.' }}</small>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host, .app-frame { display: block; height: 100%; }
    .global-loading-mask {
      position: fixed;
      inset: 0;
      z-index: 10000;
      display: grid;
      place-items: center;
      padding: 1.5rem;
      background: #f5f6fa;
    }
    .global-loading-card {
      display: flex;
      align-items: center;
      gap: 1rem;
      width: min(26rem, 100%);
      padding: 1.15rem 1.25rem;
      border: 1px solid #dbe3ed;
      border-radius: 1rem;
      background: rgba(255, 255, 255, .98);
      box-shadow: 0 18px 45px rgba(21, 47, 75, .12);
      animation: loader-enter .18s ease-out;
    }
    .global-loading-logo {
      position: relative;
      display: grid;
      place-items: center;
      width: 3.25rem;
      height: 3.25rem;
      flex: 0 0 auto;
    }
    .global-loading-logo img { width: 2.15rem; height: 2.15rem; object-fit: contain; }
    .global-loading-logo span {
      position: absolute;
      inset: 0;
      border: 3px solid #dbeafe;
      border-top-color: #1b3a5c;
      border-right-color: #d4a843;
      border-radius: 999px;
      animation: loader-spin .85s linear infinite;
    }
    .global-loading-card p {
      margin: 0 0 .2rem;
      color: #0f2238;
      font-size: .95rem;
      font-weight: 800;
    }
    .global-loading-card small { color: #64748b; font-size: .78rem; }
    @keyframes loader-spin { to { transform: rotate(360deg); } }
    @keyframes loader-enter {
      from { opacity: 0; transform: translateY(.35rem) scale(.985); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }
    @media (prefers-reduced-motion: reduce) {
      .global-loading-logo span, .global-loading-card { animation: none; }
    }
  `],
})
export class App {
  protected readonly loading = inject(GlobalLoadingService);
  private readonly i18n = inject(I18nService);
  protected readonly fr = () => this.i18n.lang() === 'fr';
}
