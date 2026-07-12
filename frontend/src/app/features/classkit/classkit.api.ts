import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ResourceKind = 'supplies' | 'books';

export interface ResourceItem {
  id: string;
  label: string;
  quantity: number | null;
  price: number | null;
  note: string | null;
  subjectCode: string | null;
  author: string | null;
  mandatory: boolean | null;
}
export interface ResourceItemUpsert {
  label: string;
  quantity?: number | null;
  price?: number | null;
  note?: string | null;
  subjectCode?: string | null;
  author?: string | null;
  mandatory?: boolean | null;
}
export interface ClassResourceView {
  classId: string | null;
  className: string;
  kind: ResourceKind;
  published: boolean;
  publishedAt: string | null;
  items: ResourceItem[];
}

/** Class resources — supplies (fournitures) & payable book lists (livres). */
@Injectable({ providedIn: 'root' })
export class ClassKitApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/classkit`;

  ofClass(kind: ResourceKind, classId: string): Observable<ClassResourceView> {
    return this.http.get<ClassResourceView>(`${this.base}/${kind}/classes/${classId}`);
  }
  addItem(kind: ResourceKind, classId: string, b: ResourceItemUpsert): Observable<ResourceItem> {
    return this.http.post<ResourceItem>(`${this.base}/${kind}/classes/${classId}/items`, b);
  }
  updateItem(kind: ResourceKind, itemId: string, b: ResourceItemUpsert): Observable<ResourceItem> {
    return this.http.put<ResourceItem>(`${this.base}/${kind}/items/${itemId}`, b);
  }
  deleteItem(kind: ResourceKind, itemId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${kind}/items/${itemId}`);
  }
  publish(kind: ResourceKind, classId: string, published: boolean): Observable<ClassResourceView> {
    return this.http.post<ClassResourceView>(`${this.base}/${kind}/classes/${classId}/publish`, { published });
  }
}
