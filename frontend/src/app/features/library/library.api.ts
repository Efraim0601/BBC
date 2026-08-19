import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Destinataires d'une ressource — la granularité demandée, en trois valeurs. */
export type Audience = 'all' | 'staff' | 'parents';

/** Rubrique, pour trier et colorer la liste. */
export type Category = 'circular' | 'pedagogy' | 'admin' | 'form' | 'other';

/** Cycle destinataire ; null = toute l'école. */
export type Section = 'maternelle' | 'primary' | 'secondary' | null;

export interface ResourceView {
  id: string;
  title: string;
  description: string | null;
  category: Category;
  audience: Audience;
  section: Section;
  fileName: string;
  contentType: string;
  byteSize: number;
  published: boolean;
  publishedAt: string | null;
  uploadedByName: string | null;
  createdAt: string;
  /** Posé par le serveur : un admin de section lit l'école entière sans y toucher. */
  canEdit: boolean;
}

export interface ResourceUpsert {
  title: string;
  description?: string | null;
  category: Category;
  audience: Audience;
  section?: Section;
  published: boolean;
}

@Injectable({ providedIn: 'root' })
export class LibraryApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/library`;

  list(): Observable<ResourceView[]> {
    return this.http.get<ResourceView[]>(this.base);
  }

  /**
   * Dépôt : le fichier et sa fiche dans une même requête multipart. La fiche
   * part en JSON (partie « meta ») — un champ de formulaire à plat ne saurait
   * pas dire « périmètre absent = toute l'école ».
   */
  create(file: File, meta: ResourceUpsert): Observable<ResourceView> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }));
    return this.http.post<ResourceView>(this.base, form);
  }

  update(id: string, meta: ResourceUpsert): Observable<ResourceView> {
    return this.http.put<ResourceView>(`${this.base}/${id}`, meta);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /**
   * Les octets du fichier.
   *
   * <p>Un `<a href>` ne conviendrait pas : l'appel porte le jeton dans l'en-tête
   * Authorization, que le navigateur n'ajoute pas de lui-même. On récupère donc
   * un blob et on déclenche l'ouverture depuis une URL d'objet locale.
   */
  file(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/file`, { responseType: 'blob' });
  }
}

/**
 * Ouvre un fichier téléchargé : dans un onglet quand le navigateur sait
 * l'afficher (PDF, image), en enregistrement sinon.
 *
 * <p>L'URL d'objet est révoquée après coup — sans quoi chaque consultation
 * retiendrait le fichier entier en mémoire jusqu'au rechargement de la page.
 */
export function openBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const inline = blob.type === 'application/pdf' || blob.type.startsWith('image/');
  if (inline) {
    window.open(url, '_blank', 'noopener');
  } else {
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Poids lisible — « 1,4 Mo » plutôt que 1468006. */
export function fmtBytes(bytes: number, fr: boolean): string {
  if (bytes < 1024) return `${bytes} o`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} ${fr ? 'Ko' : 'KB'}`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace('.', fr ? ',' : '.')} ${fr ? 'Mo' : 'MB'}`;
}
