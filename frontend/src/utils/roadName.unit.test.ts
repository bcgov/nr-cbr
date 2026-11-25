import { describe, expect, it } from 'vitest';

import { roadNameCase } from './roadName';

describe('roadNameCase', () => {
  it.each([
    ['CHILCOTIN SOUTH', 'Chilcotin South'],
    ['BIG CREEK FSR', 'Big Creek FSR'],
    ['BIG CROW RD', 'Big Crow RD'],
    ['AM 4', 'AM 4'],
    ['SPUR 4A', 'Spur 4A'],
    ['LAKE OF THE WOODS', 'Lake of the Woods'],
    ['CARIBOO-CHILCOTIN MAIN', 'Cariboo-Chilcotin Main'],
    ['BONAPARTE/FROSTY', 'Bonaparte/Frosty'],
    ["O'NEIL CREEK", "O'Neil Creek"],
    ["MAC'S ROAD", "Mac's Road"],
    ['100% DOWN_HILL', '100% Down_Hill'],
    ['Already Mixed', 'Already Mixed'],
  ])('%s reads %s', (stored, shown) => {
    expect(roadNameCase(stored)).toBe(shown);
  });

  it('is blank for no name', () => {
    expect(roadNameCase(null)).toBe('');
    expect(roadNameCase('')).toBe('');
  });
});
