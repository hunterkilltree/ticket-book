import { describe, it, expect } from 'vitest';
import { formatCurrency } from './currency';

describe('formatCurrency', () => {
  it('formats USD', () => {
    expect(formatCurrency(42)).toBe('$42.00');
  });
});
