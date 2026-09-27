import { describe, expect, it } from 'vitest';

import { buildLoginUrl } from '@/utils/loginUrl';

describe('buildLoginUrl()', () => {
  it('옵션이 없으면 /login 을 반환한다', () => {
    // when
    const url = buildLoginUrl();

    // then
    expect(url).toBe('/login');
  });

  it('reason 과 redirect 를 쿼리로 인코딩한다', () => {
    // when
    const url = buildLoginUrl({ reason: 'idle', redirect: '/mypage?tab=orders' });

    // then
    expect(url).toBe('/login?reason=idle&redirect=%2Fmypage%3Ftab%3Dorders');
  });
});
