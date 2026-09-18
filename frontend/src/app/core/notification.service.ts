import { Injectable, signal } from '@angular/core';

export type NoticeKind = 'error' | 'success';

export interface Notice {
  id: number;
  kind: NoticeKind;
  text: string;
}

/**
 * One place for the messages that used to live in an `error` string on every
 * component, shown once by the shell.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private static readonly DISMISS_AFTER_MS = 6000;

  private nextId = 1;
  private readonly items = signal<Notice[]>([]);

  readonly notices = this.items.asReadonly();

  error(text: string): void {
    this.push('error', text);
  }

  success(text: string): void {
    this.push('success', text);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((n) => n.id !== id));
  }

  private push(kind: NoticeKind, text: string): void {
    const notice: Notice = { id: this.nextId++, kind, text };
    this.items.update((list) => [...list, notice]);
    // Errors stay until dismissed; a success message has nothing to act on.
    if (kind === 'success') {
      setTimeout(() => this.dismiss(notice.id), NotificationService.DISMISS_AFTER_MS);
    }
  }
}
