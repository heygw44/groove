import { describe, expect, it } from 'vitest';

import { buildLoginUrl, getSafeRedirect } from '@/utils/loginUrl';

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

describe('getSafeRedirect()', () => {
  it.each(['/limited-drops/118', '/mypage?tab=orders', '/'])(
    '앱 내부 경로 %s 는 그대로 둔다',
    (raw) => {
      // when
      const redirect = getSafeRedirect(raw);

      // then
      expect(redirect).toBe(raw);
    },
  );

  it.each([null, '', 'orders', '//evil.com', '/\\evil.com', 'https://evil.com'])(
    '%s 는 / 로 바꾼다',
    (raw) => {
      // when
      const redirect = getSafeRedirect(raw);

      // then
      expect(redirect).toBe('/');
    },
  );
});
