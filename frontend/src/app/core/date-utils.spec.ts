import { localToday } from './date-utils';

describe('localToday', () => {
  it('formats as YYYY-MM-DD', () => {
    expect(localToday()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('uses the local calendar date, not the UTC one', () => {
    const now = new Date();
    const expected = [
      now.getFullYear(),
      String(now.getMonth() + 1).padStart(2, '0'),
      String(now.getDate()).padStart(2, '0')
    ].join('-');

    expect(localToday()).toBe(expected);
  });
});
