import type { CourierCode } from '@/types/order';

interface CourierInfo {
  name: string;
  /** 송장번호를 받아 그 택배사 공식 조회 페이지 URL 을 만든다. 번호는 encodeURIComponent 로 이스케이프한다. */
  trackingUrl: (trackingNumber: string) => string;
}

/** 서버는 코드만 검증하고 조회 URL 은 모른다(D8) — 템플릿은 여기 한 곳에 둔다. */
export const COURIERS: Record<CourierCode, CourierInfo> = {
  CJ: {
    name: 'CJ대한통운',
    trackingUrl: (trackingNumber) =>
      `https://trace.cjlogistics.com/next/tracking.html?wblNo=${encodeURIComponent(trackingNumber)}`,
  },
  HANJIN: {
    name: '한진택배',
    trackingUrl: (trackingNumber) =>
      `https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=${encodeURIComponent(trackingNumber)}`,
  },
  LOTTE: {
    name: '롯데택배',
    trackingUrl: (trackingNumber) =>
      `https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=${encodeURIComponent(trackingNumber)}`,
  },
  EPOST: {
    name: '우체국택배',
    trackingUrl: (trackingNumber) =>
      `https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=${encodeURIComponent(trackingNumber)}`,
  },
  LOGEN: {
    name: '로젠택배',
    trackingUrl: (trackingNumber) =>
      `https://www.ilogen.com/web/personal/trace/${encodeURIComponent(trackingNumber)}`,
  },
  KDEXP: {
    name: '경동택배',
    trackingUrl: (trackingNumber) =>
      `https://kdexp.com/service/delivery/etc/delivery.do?barcode=${encodeURIComponent(trackingNumber)}`,
  },
};
