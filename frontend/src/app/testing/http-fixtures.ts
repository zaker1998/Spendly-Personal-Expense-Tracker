import { Category, Expense, PageResponse } from '../core/models';

/** Small builders so each spec states only the fields it cares about. */

export function category(overrides: Partial<Category> = {}): Category {
  return {
    id: 1,
    name: 'Food',
    color: '#E76F51',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides
  };
}

export function expense(overrides: Partial<Expense> = {}): Expense {
  return {
    id: 10,
    categoryId: 1,
    categoryName: 'Food',
    categoryColor: '#E76F51',
    amount: 12.5,
    currency: 'EUR',
    spentOn: '2026-03-14',
    description: 'Lunch',
    version: 0,
    createdAt: '2026-03-14T12:00:00Z',
    updatedAt: '2026-03-14T12:00:00Z',
    ...overrides
  };
}

export function page<T>(content: T[], totalElements = content.length, number = 0): PageResponse<T> {
  return { content, totalElements, totalPages: Math.ceil(totalElements / 10), number, size: 10 };
}
