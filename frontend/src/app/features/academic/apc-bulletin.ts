import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { BulletinView } from './academic.api';
import { SecondaryBulletinComponent } from './secondary-bulletin';

/**
 * Primary and nursery report card.
 *
 * The school uses the same official presentation as secondary.  The one intentional
 * difference is academic weighting: every primary subject is equally weighted, so the
 * report never exposes coefficients or a weighted-product column.
 */
@Component({
  selector: 'bbc-apc-bulletin',
  standalone: true,
  imports: [SecondaryBulletinComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <bbc-secondary-bulletin [view]="view()" [showCoefficients]="false" />
  `,
})
export class ApcBulletinComponent {
  readonly view = input.required<BulletinView>();
}
