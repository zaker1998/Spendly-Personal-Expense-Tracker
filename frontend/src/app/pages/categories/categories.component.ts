import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { Category } from '../../core/models';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [ReactiveFormsModule, NgFor, NgIf],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.css'
})
export class CategoriesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  categories: Category[] = [];
  error = '';
  editingId: number | null = null;

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    color: ['#2A9D8F']
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.getCategories().subscribe({
      next: (cats) => (this.categories = cats),
      error: () => (this.error = 'Failed to load categories')
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const body = this.form.getRawValue();
    const req$ =
      this.editingId == null
        ? this.api.createCategory(body)
        : this.api.updateCategory(this.editingId, body);

    req$.subscribe({
      next: () => {
        this.editingId = null;
        this.form.reset({ name: '', color: '#2A9D8F' });
        this.reload();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Save failed')
    });
  }

  edit(category: Category): void {
    this.editingId = category.id;
    this.form.patchValue({
      name: category.name,
      color: category.color || '#2A9D8F'
    });
  }

  remove(id: number): void {
    this.api.deleteCategory(id).subscribe({
      next: () => this.reload(),
      error: (err) => (this.error = err?.error?.message ?? 'Delete failed')
    });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form.reset({ name: '', color: '#2A9D8F' });
  }
}
