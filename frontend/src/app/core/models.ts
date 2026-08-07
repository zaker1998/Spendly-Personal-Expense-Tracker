export type Role = 'USER' | 'ADMIN';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  userId: number;
  email: string;
  role: Role;
}

export interface Category {
  id: number;
  name: string;
  color: string | null;
  createdAt: string;
}

export interface Expense {
  id: number;
  categoryId: number;
  categoryName: string;
  categoryColor: string | null;
  amount: number;
  currency: string;
  spentOn: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CategoryTotal {
  categoryId: number;
  categoryName: string;
  total: number;
}

export interface MonthlySummary {
  year: number;
  month: number;
  totalAmount: number;
  currency: string;
  byCategory: CategoryTotal[];
}

export interface Budget {
  id: number;
  categoryId: number | null;
  categoryName: string;
  limitAmount: number;
  spentAmount: number;
  remainingAmount: number;
  percentUsed: number;
  overBudget: boolean;
  year: number;
  month: number;
  currency: string;
}

export interface CategorySuggestion {
  categoryId: number | null;
  categoryName: string | null;
  source: 'AI' | 'HEURISTIC' | 'NONE';
}

export interface AppUser {
  id: number;
  email: string;
  role: Role;
  createdAt: string;
}

export interface AdminExpense {
  id: number;
  userId: number;
  userEmail: string;
  categoryId: number;
  categoryName: string;
  amount: number;
  currency: string;
  spentOn: string;
  description: string | null;
  createdAt: string;
}
