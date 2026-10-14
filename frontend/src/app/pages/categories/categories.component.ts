import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { describeError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { Category } from '../../core/models';
import { NotificationService } from '../../core/notification.service';
import { IconComponent } from '../../shared/icon.component';

const DEFAULT_COLOUR = '#2A9D8F';

@Component({
  selector: 'app-categories',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, IconComponent],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.css'
})
export class CategoriesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly categories = signal<Category[]>([]);
  readonly saving = signal(false);
  readonly editing = signal<Category | null>(null);

  /** One click for a colour that already sits well with the rest of the app. */
  readonly presets = [
    { name: 'Teal', value: '#2A9D8F' },
    { name: 'Coral', value: '#E76F51' },
    { name: 'Saffron', value: '#E9C46A' },
    { name: 'Sandy', value: '#F4A261' },
    { name: 'Deep sea', value: '#264653' },
    { name: 'Plum', value: '#8E5572' },
    { name: 'Sky', value: '#4A90C2' },
    { name: 'Slate', value: '#6C757D' }
  ];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    color: [DEFAULT_COLOUR]
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api
      .getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => this.categories.set(categories),
        error: (err: unknown) =>
          this.notifications.error(describeError(err, 'Could not load categories'))
      });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const editing = this.editing();
    const value = this.form.getRawValue();
    // The version travels with the update so an edit made against a copy someone
    // else has already replaced is refused rather than silently winning.
    const body = { ...value, version: editing?.version };

    this.saving.set(true);
    const request$ = editing
      ? this.api.updateCategory(editing.id, body)
      : this.api.createCategory({ name: value.name, color: value.color });

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success(editing ? 'Category updated' : 'Category added');
        this.cancelEdit();
        this.reload();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.notifications.error(describeError(err, 'Could not save the category'));
      }
    });
  }

  pickColour(value: string): void {
    this.form.patchValue({ color: value });
  }

  isPicked(value: string): boolean {
    return this.form.controls.color.value.toLowerCase() === value.toLowerCase();
  }

  edit(category: Category): void {
    this.editing.set(category);
    this.form.patchValue({ name: category.name, color: category.color ?? DEFAULT_COLOUR });
  }

  remove(category: Category): void {
    if (!confirm(`Delete category "${category.name}"?`)) {
      return;
    }
    this.api
      .deleteCategory(category.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(),
        error: (err: unknown) =>
          this.notifications.error(describeError(err, 'Could not delete the category'))
      });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.form.reset({ name: '', color: DEFAULT_COLOUR });
  }
}
