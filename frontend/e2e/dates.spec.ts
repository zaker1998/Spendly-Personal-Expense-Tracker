import { test, expect, Page } from '@playwright/test';

/**
 * `spentOn` is a LocalDate — a calendar date with no instant attached. It has to
 * render as the day it says, in every timezone.
 *
 * The regression this guards: the templates used to pass `: 'UTC'` to Angular's
 * date pipe. The pipe parses a date-only ISO string as *local* midnight, so
 * formatting it in a different zone shifts it — every expense showed a day early
 * for anyone east of UTC, which is to say everyone the app is aimed at. CI runs
 * in UTC, where the bug is invisible, so the timezone here is pinned east of it
 * on purpose.
 */
test.use({ timezoneId: 'Europe/Vienna' });

const DEMO = { email: 'demo@spendly.app', password: 'Demo123!' };

async function signIn(page: Page) {
  await page.goto('/login');
  await page.fill('input[formcontrolname="email"]', DEMO.email);
  await page.fill('input[formcontrolname="password"]', DEMO.password);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.includes('/login')),
    page.click('button[type="submit"]'),
  ]);
  await page.waitForLoadState('networkidle');
}

/** "2026-08-27" -> "Aug 27, 2026", matching Angular's 'mediumDate'. */
function expectedLabel(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${months[month - 1]} ${day}, ${year}`;
}

test('the expense list shows the date the API stored, not one shifted by a timezone', async ({ page }) => {
  await signIn(page);

  // Read the payload the app itself fetches — no token juggling, and it compares
  // exactly what was rendered against exactly what produced it.
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/expenses?') && r.ok()),
    page.goto('/expenses'),
  ]);

  const body = await response.json();
  const rendered = (await page.locator('tbody tr td:first-child').allInnerTexts()).map((t) => t.trim());

  expect(rendered.length).toBeGreaterThan(0);
  expect(rendered).toEqual(
    body.content.slice(0, rendered.length).map((e: { spentOn: string }) => expectedLabel(e.spentOn))
  );
});
