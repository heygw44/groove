import { describe, expect, it } from 'vitest';

import type { ProductSummary } from '@/types/product';
import { buildOtherPressingLabel, buildPressingMetaLine } from '@/utils/pressing';

const product = (overrides: Partial<ProductSummary> = {}): ProductSummary => ({
  id: 1,
  title: 'Kind of Blue',
  artistName: 'Miles Davis',
  price: 30000,
  status: 'ON_SALE',
  editionType: 'STANDARD',
  albumId: 1,
  otherPressingCount: 0,
  ...overrides,
});

describe('buildPressingMetaLine()', () => {
  it('국가·연도·에디션이 모두 있으면 가운뎃점으로 이어 붙인다', () => {
    // given
    const target = product({ country: 'US', pressingYear: 1959, editionType: 'ORIGINAL' });

    // when
    const line = buildPressingMetaLine(target);

    // then
    expect(line).toBe('미국 · 1959 · 오리지널반');
  });

  it('국가가 없으면 나머지 값만 이어 붙인다', () => {
    // given
    const target = product({ pressingYear: 2020, editionType: 'REISSUE' });

    // when
    const line = buildPressingMetaLine(target);

    // then
    expect(line).toBe('2020 · 재발매반');
  });

  it('연도가 없으면 나머지 값만 이어 붙인다', () => {
    // given
    const target = product({ country: 'Japan', editionType: 'REMASTER' });

    // when
    const line = buildPressingMetaLine(target);

    // then
    expect(line).toBe('일본 · 리마스터반');
  });
});

describe('buildOtherPressingLabel()', () => {
  it('개수를 그대로 문구에 넣는다', () => {
    // given & when
    const label = buildOtherPressingLabel(3);

    // then
    expect(label).toBe('다른 에디션 3종');
  });

  it('1개여도 단수 표현을 따로 두지 않는다', () => {
    // given & when
    const label = buildOtherPressingLabel(1);

    // then
    expect(label).toBe('다른 에디션 1종');
  });
});
