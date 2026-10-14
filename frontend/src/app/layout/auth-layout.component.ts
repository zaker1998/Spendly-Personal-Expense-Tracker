import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BrandComponent } from '../shared/brand.component';
import { IconComponent, IconName } from '../shared/icon.component';

interface Feature {
  icon: IconName;
  title: string;
  text: string;
}

/**
 * The frame around sign-in and registration: what the app is on one side, the
 * form on the other. For most visitors this is the first screen they see, so it
 * says what they are about to look at before asking them to sign in.
 */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandComponent, IconComponent],
  template: `
    <div class="auth">
      <section class="intro" aria-label="About Spendly">
        <app-brand class="brand" />
        <div class="pitch">
          <p class="headline">Know where every euro goes.</p>
          <p>
            A personal expense tracker with a monthly dashboard, budgets that warn you early and
            categories suggested for you.
          </p>
        </div>
        <ul class="features">
          @for (f of features; track f.title) {
            <li>
              <span class="feature-icon"><app-icon [name]="f.icon" /></span>
              <span>
                <strong>{{ f.title }}</strong>
                <span>{{ f.text }}</span>
              </span>
            </li>
          }
        </ul>
        <div class="stack">
          <span class="visually-hidden">Built with</span>
          <ul>
            @for (t of stack; track t) {
              <li>{{ t }}</li>
            }
          </ul>
          <a href="https://github.com/zaker1998/Spendly-Personal-Expense-Tracker" rel="noopener"
            >Source code on GitHub <app-icon name="arrowRight" [size]="14"
          /></a>
        </div>
      </section>
      <main class="form-side">
        <ng-content />
      </main>
    </div>
  `,
  styleUrl: './auth-layout.component.css'
})
export class AuthLayoutComponent {
  readonly features: Feature[] = [
    {
      icon: 'chart',
      title: 'Monthly overview',
      text: 'Category breakdown and the trend against last month.'
    },
    {
      icon: 'target',
      title: 'Budgets',
      text: 'Overall or per category, flagged before you go over.'
    },
    {
      icon: 'sparkles',
      title: 'AI categories',
      text: 'An LLM picks from your own categories, checked on the server.'
    },
    {
      icon: 'lock',
      title: 'Secure sessions',
      text: 'Short-lived JWT with a rotating, httpOnly refresh token.'
    }
  ];

  readonly stack = ['Java 21', 'Spring Boot 3', 'Angular 19', 'PostgreSQL', 'Docker', 'AWS'];
}
