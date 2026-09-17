import { test, expect, Page } from '@playwright/test';

/**
 * The session is a short-lived access token plus an httpOnly refresh cookie.
 * These check the parts a unit test cannot: that the browser really stores and
 * sends the cookie, and that signing out ends the session on the server.
 */

const DEMO = { email: 'demo@spendly.app', password: 'Demo123!' };

async function signIn(page: Page) {
  await page.goto('/login');
  await page.fill('input[formcontrolname="email"]', DEMO.email);
  await page.fill('input[formcontrolname="password"]', DEMO.password);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.includes('/login')),
    page.click('button[type="submit"]')
  ]);
}

test('the refresh token is an httpOnly cookie, not something script can read', async ({
  page,
  context
}) => {
  await signIn(page);

  const cookie = (await context.cookies()).find((c) => c.name === 'spendly_refresh');
  expect(cookie, 'refresh cookie').toBeDefined();
  expect(cookie!.httpOnly).toBe(true);

  const visibleToScript = await page.evaluate(() => document.cookie);
  expect(visibleToScript).not.toContain('spendly_refresh');
});

/**
 * With a fifteen-minute access token, a session that only lived in
 * localStorage would end on the first reload after a short break.
 */
test('a lost access token is renewed from the cookie on reload', async ({ page }) => {
  await signIn(page);

  await page.evaluate(() => localStorage.removeItem('spendly_session'));
  await page.goto('/expenses');

  await expect(page).toHaveURL(/\/expenses$/);
  await expect(page.getByRole('heading', { name: 'Expenses' })).toBeVisible();
});

test('logging out ends the session on the server, not just in the tab', async ({
  page,
  context
}) => {
  await signIn(page);
  const cookieBefore = (await context.cookies()).find((c) => c.name === 'spendly_refresh')!;

  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login$/);

  // Put the old cookie back, as a stolen copy would be: the server must refuse it.
  await context.addCookies([cookieBefore]);
  const status = await page.evaluate(async () => {
    const response = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
    return response.status;
  });
  expect(status).toBe(401);

  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
});
