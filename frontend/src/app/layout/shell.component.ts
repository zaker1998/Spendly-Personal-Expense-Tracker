import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { BrandComponent } from '../shared/brand.component';
import { IconComponent, IconName } from '../shared/icon.component';
import { ToastComponent } from './toast.component';

interface NavLink {
  path: string;
  label: string;
  icon: IconName;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ToastComponent,
    IconComponent,
    BrandComponent
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  readonly auth = inject(AuthService);

  readonly links: NavLink[] = [
    { path: '/', label: 'Dashboard', icon: 'dashboard' },
    { path: '/expenses', label: 'Expenses', icon: 'receipt' },
    { path: '/categories', label: 'Categories', icon: 'tag' },
    { path: '/budgets', label: 'Budgets', icon: 'target' },
    { path: '/account', label: 'Account', icon: 'user' }
  ];

  readonly initial = computed(() => (this.auth.session()?.email ?? '?').charAt(0).toUpperCase());

  skipToContent(event: Event, main: HTMLElement): void {
    event.preventDefault();
    main.focus();
  }
}
