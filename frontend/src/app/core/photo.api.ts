import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../environments/environment';

/** Personne portant une photo : le segment d'API est le même que le module. */
export type PhotoOwner = 'students' | 'staff';

/**
 * Photos de profil.
 *
 * <p>Les images ne peuvent pas être posées dans un `<img src>` : les appels
 * portent un jeton dans l'en-tête Authorization. On les récupère donc en blob
 * via HttpClient, et on en fait une URL d'objet locale — pensez à la révoquer
 * (`URL.revokeObjectURL`) quand le composant disparaît.
 */
@Injectable({ providedIn: 'root' })
export class PhotoApi {
  private http = inject(HttpClient);
  private base = environment.apiUrl;

  /** URL d'objet de la photo, ou null quand la personne n'en a pas (404). */
  load(owner: PhotoOwner, id: string): Observable<string | null> {
    return this.http.get(`${this.base}/${owner}/${id}/photo`, { responseType: 'blob' }).pipe(
      map((blob) => URL.createObjectURL(blob)),
      catchError(() => of(null)),
    );
  }

  save(owner: PhotoOwner, id: string, dataUrl: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${owner}/${id}/photo`, { dataUrl });
  }

  remove(owner: PhotoOwner, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${owner}/${id}/photo`);
  }
}
