import {
  Component, ChangeDetectionStrategy, ElementRef, inject, input, model, signal, viewChild, OnDestroy,
} from '@angular/core';
import { I18nService } from '../i18n.service';
import { IconComponent } from './icon';

/** Côté de l'image enregistrée, en pixels — carré, suffisant pour une fiche ou un badge. */
const OUTPUT_PX = 512;
/** Qualité JPEG : au-delà, le poids double sans gain visible à cette taille. */
const QUALITY = 0.85;

/**
 * Photo de profil : selfie via la caméra, ou image importée depuis l'appareil.
 *
 * <p>Le recadrage carré et la compression se font ici, dans le navigateur : le
 * serveur ne reçoit qu'une data URL de quelques dizaines de kilo-octets et n'a
 * aucun traitement d'image à faire.
 *
 * <p>La caméra exige un contexte sécurisé (HTTPS ou localhost). Quand elle est
 * indisponible — refus de permission, poste sans webcam, navigateur ancien —
 * l'import de fichier reste proposé et un message explique pourquoi.
 */
@Component({
  selector: 'bbc-photo-capture',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="flex items-start gap-4 flex-wrap">
      <!-- Aperçu / caméra -->
      <div class="relative w-28 h-28 rounded-xl2 overflow-hidden bg-slate-100 border border-slate-200 shrink-0
                  flex items-center justify-center">
        @if (streaming()) {
          <video #video autoplay playsinline muted class="w-full h-full object-cover"></video>
        } @else if (photo()) {
          <img [src]="photo()" alt="" class="w-full h-full object-cover" />
        } @else {
          <span class="text-mute"><bbc-icon name="user" [s]="34" /></span>
        }
      </div>

      <div class="flex-1 min-w-[240px]">
        <div class="text-xs font-semibold text-ink mb-1.5">{{ label() || (fr() ? 'Photo' : 'Photo') }}</div>

        @if (streaming()) {
          <div class="flex items-center gap-2 flex-wrap">
            <button type="button" (click)="shoot()"
              class="inline-flex items-center gap-1.5 h-9 px-3.5 text-xs font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
              <bbc-icon name="check" [s]="14" /> {{ fr() ? 'Prendre la photo' : 'Take the photo' }}
            </button>
            <button type="button" (click)="stopCamera()"
              class="h-9 px-3.5 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              {{ fr() ? 'Annuler' : 'Cancel' }}
            </button>
          </div>
        } @else {
          <div class="flex items-center gap-2 flex-wrap">
            <button type="button" (click)="startCamera()"
              class="inline-flex items-center gap-1.5 h-9 px-3.5 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="camera" [s]="14" />
              {{ photo() ? (fr() ? 'Reprendre un selfie' : 'Retake selfie') : (fr() ? 'Prendre un selfie' : 'Take a selfie') }}
            </button>
            <label class="inline-flex items-center gap-1.5 h-9 px-3.5 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 cursor-pointer">
              <bbc-icon name="download" [s]="14" /> {{ fr() ? 'Importer une image' : 'Upload an image' }}
              <input type="file" accept="image/*" (change)="onFile($event)" class="hidden" />
            </label>
            @if (photo()) {
              <button type="button" (click)="clear()"
                class="inline-flex items-center gap-1.5 h-9 px-3 text-xs font-semibold rounded-lg bg-rose-50 text-rose-600 border border-rose-200 hover:bg-rose-100">
                <bbc-icon name="trash" [s]="14" /> {{ fr() ? 'Retirer' : 'Remove' }}
              </button>
            }
          </div>
        }

        <div class="text-[11px] text-mute mt-1.5">
          {{ fr()
            ? 'Image recadrée en carré et compressée automatiquement. Le selfie nécessite une connexion sécurisée (https) et l’autorisation de la caméra.'
            : 'The image is cropped square and compressed automatically. The selfie needs a secure connection (https) and camera permission.' }}
        </div>
        @if (error(); as e) {
          <div class="text-[11px] text-rose-600 mt-1">{{ e }}</div>
        }
      </div>
    </div>
    <canvas #canvas class="hidden"></canvas>
  `,
})
export class PhotoCaptureComponent implements OnDestroy {
  private i18n = inject(I18nService);

  /** Data URL de la photo (JPEG), ou null. Lecture ET écriture : `[(photo)]`. */
  readonly photo = model<string | null>(null);
  readonly label = input('');

  protected streaming = signal(false);
  protected error = signal<string | null>(null);
  protected fr = () => this.i18n.lang() === 'fr';

  private video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private canvas = viewChild<ElementRef<HTMLCanvasElement>>('canvas');
  private stream: MediaStream | null = null;

  async startCamera(): Promise<void> {
    this.error.set(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      this.error.set(this.fr()
        ? 'Caméra indisponible sur cet appareil ou ce navigateur — importez une image.'
        : 'No camera available on this device or browser — upload an image instead.');
      return;
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 1280 } },
        audio: false,
      });
      this.streaming.set(true);
      // La balise <video> n'existe qu'une fois streaming() vrai : on attend le rendu.
      setTimeout(() => {
        const el = this.video()?.nativeElement;
        if (el && this.stream) el.srcObject = this.stream;
      });
    } catch {
      this.error.set(this.fr()
        ? 'Accès à la caméra refusé. Autorisez-le dans le navigateur, ou importez une image.'
        : 'Camera access denied. Allow it in the browser, or upload an image instead.');
      this.stopCamera();
    }
  }

  stopCamera(): void {
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.streaming.set(false);
  }

  /** Fige l'image de la caméra dans un carré centré. */
  shoot(): void {
    const el = this.video()?.nativeElement;
    if (!el) return;
    const side = Math.min(el.videoWidth, el.videoHeight);
    const sx = (el.videoWidth - side) / 2;
    const sy = (el.videoHeight - side) / 2;
    this.photo.set(this.toSquareJpeg(el, sx, sy, side));
    this.stopCamera();
  }

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.error.set(null);
    if (!file.type.startsWith('image/')) {
      this.error.set(this.fr() ? 'Ce fichier n’est pas une image.' : 'That file is not an image.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const side = Math.min(img.width, img.height);
        this.photo.set(this.toSquareJpeg(img, (img.width - side) / 2, (img.height - side) / 2, side));
      };
      img.onerror = () => this.error.set(this.fr() ? 'Image illisible.' : 'Unreadable image.');
      img.src = String(reader.result ?? '');
    };
    reader.readAsDataURL(file);
  }

  clear(): void {
    this.photo.set(null);
    this.error.set(null);
  }

  /** Recadrage carré + réduction à OUTPUT_PX, encodé en JPEG. */
  private toSquareJpeg(src: CanvasImageSource, sx: number, sy: number, side: number): string {
    const cv = this.canvas()!.nativeElement;
    cv.width = OUTPUT_PX;
    cv.height = OUTPUT_PX;
    const ctx = cv.getContext('2d')!;
    ctx.fillStyle = '#ffffff';                       // JPEG n'a pas de transparence
    ctx.fillRect(0, 0, OUTPUT_PX, OUTPUT_PX);
    ctx.drawImage(src, sx, sy, side, side, 0, 0, OUTPUT_PX, OUTPUT_PX);
    return cv.toDataURL('image/jpeg', QUALITY);
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }
}
