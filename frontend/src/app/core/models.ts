export type Role = 'USER' | 'ADMIN';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  /** Lifetime of the access token, so the client can renew before a call fails. */
  expiresInSeconds: number;
  userId: number;
  email: string;
  role: Role;
}

/** The shape every failed API call comes back in. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fields: Record<string, string> | null;
  /** Matches the X-Request-Id header and the server log line. */
  requestId: string | null;
}

export interface Category {
  id: number;
  name: string;
  color: string | null;
  /** Send back on update to have a concurrent edit rejected rather than lost. */
  version: number;
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
  version: number;
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
  version: number;
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
