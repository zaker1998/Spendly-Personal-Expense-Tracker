import { test, expect, Page } from '@playwright/test';
import { source as AXE_SOURCE } from 'axe-core';

/**
 * Automated accessibility gate.
 *
 * axe-core catches somewhere around a third to a half of WCAG problems — it can
 * prove a control has no accessible name, it cannot tell whether the name makes
 * sense. So this is a regression guard on the mechanical failures (unlabelled
 * inputs, insufficient contrast, missing table semantics), not a claim that the
 * app is accessible. The judgement calls still need a human with a screen reader.
 */

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const DEMO = { email: 'demo@spendly.app', password: 'Demo123!' };
const ADMIN = { email: 'admin@spendly.app', password: 'Admin123!' };

async function signIn(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.fill('input[formcontrolname="email"]', user.email);
  await page.fill('input[formcontrolname="password"]', user.password);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.includes('/login')),
    page.click('button[type="submit"]'),
  ]);
  await page.waitForLoadState('networkidle');
}

async function violations(page: Page) {
  // page.evaluate, not addScriptTag: the deployed app ships a
  // `script-src 'self'` CSP, which blocks an injected inline <script>. Evaluating
  // through the debugger protocol instead means the suite works against the real
  // CloudFront deployment without weakening the policy for the test.
  await page.evaluate(AXE_SOURCE);
  const result = await page.evaluate(
    async (tags) => (await (window as any).axe.run(document, { runOnly: { type: 'tag', values: tags } })).violations,
    WCAG
  );
  // Fail with the rule and the element, not just a count.
  return (result as any[]).flatMap((v) => v.nodes.map((n: any) => `${v.id} [${v.impact}] ${n.target.join(' ')}`));
}

test.describe('accessibility', () => {
  test('sign-in page has no automatically detectable violations', async ({ page }) => {
    await page.goto('/login');
    expect(await violations(page)).toEqual([]);
  });

  for (const path of ['/', '/expenses', '/categories', '/budgets']) {
    test(`${path} has no automatically detectable violations`, async ({ page }) => {
      await signIn(page, DEMO);
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      expect(await violations(page)).toEqual([]);
    });
  }

  test('both admin tabs have no automatically detectable violations', async ({ page }) => {
    await signIn(page, ADMIN);
    await page.goto('/admin');
    await page.waitForLoadState('networkidle');
    expect(await violations(page)).toEqual([]);

    await page.getByRole('button', { name: 'All expenses' }).click();
    await expect(page.getByRole('table')).toBeVisible();
    expect(await violations(page)).toEqual([]);
  });

  test('the skip link is the first stop and moves focus into main', async ({ page }) => {
    await signIn(page, DEMO);
    await page.goto('/expenses');

    await page.keyboard.press('Tab');
    const skipLink = page.locator('a.skip-link');
    await expect(skipLink).toBeFocused();
    await expect(skipLink).toBeVisible(); // it only leaves the off-screen position on focus

    await page.keyboard.press('Enter');
    await expect(page.locator('main#main-content')).toBeFocused();
  });

  test('every form control on the expenses page has an accessible name', async ({ page }) => {
    await signIn(page, DEMO);
    await page.goto('/expenses');

    const unnamed = await page.evaluate(() =>
      Array.from(document.querySelectorAll('input, select, textarea'))
        .filter((el) => {
          const id = el.getAttribute('id');
          return !(
            el.getAttribute('aria-label') ||
            el.getAttribute('aria-labelledby') ||
            el.closest('label') ||
            (id && document.querySelector(`label[for="${id}"]`))
          );
        })
        .map((el) => el.outerHTML.slice(0, 80))
    );

    expect(unnamed).toEqual([]);
  });
});
