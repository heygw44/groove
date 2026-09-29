import { describe, expect, it } from 'vitest';

import { COURIERS } from '@/constants/couriers';
import type { CourierCode } from '@/types/order';

const ALL_COURIER_CODES: readonly CourierCode[] = [
  'CJ',
  'HANJIN',
  'LOTTE',
  'EPOST',
  'LOGEN',
  'KDEXP',
];

describe('COURIERS', () => {
  it.each(ALL_COURIER_CODES)('%s 는 이름과 조회 URL 빌더를 갖는다', (code) => {
    // given & when
    const courier = COURIERS[code];

    // then
    expect(courier.name).toBeTruthy();
    expect(courier.trackingUrl('123')).toContain('123');
  });

  it('송장번호는 encodeURIComponent 로 이스케이프한다', () => {
    // given & when
    const url = COURIERS.CJ.trackingUrl('123 456&789');

    // then
    expect(url).toContain(encodeURIComponent('123 456&789'));
    expect(url).not.toContain('123 456&789');
  });

  it('CJ 조회 URL 은 정해진 템플릿을 쓴다', () => {
    // given & when
    const url = COURIERS.CJ.trackingUrl('1234567890');

    // then
    expect(url).toBe('https://trace.cjlogistics.com/next/tracking.html?wblNo=1234567890');
  });

  it('LOGEN 조회 URL 은 경로 뒤에 송장번호를 붙인다', () => {
    // given & when
    const url = COURIERS.LOGEN.trackingUrl('1234567890');

    // then
    expect(url).toBe('https://www.ilogen.com/web/personal/trace/1234567890');
  });
});
