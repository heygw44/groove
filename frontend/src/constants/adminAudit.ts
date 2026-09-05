import type { AdminAuditAction, AdminAuditTargetType } from '@/types/adminAuditLog';
import type { MemberRole, MemberStatus } from '@/types/member';

export const AUDIT_ACTION_LABELS: Record<AdminAuditAction, string> = {
  PRODUCT_CREATE: '상품 등록',
  PRODUCT_UPDATE: '상품 수정',
  PRODUCT_HIDE: '상품 숨김',
  PRODUCT_RESTORE: '상품 복구',
  ORDER_STATUS_CHANGE: '주문 상태 변경',
  COUPON_CREATE: '쿠폰 등록',
  COUPON_UPDATE: '쿠폰 수정',
  COUPON_DISABLE: '쿠폰 중지',
  LIMITED_DROP_CREATE: '한정반 등록',
  LIMITED_DROP_UPDATE: '한정반 수정',
  LIMITED_DROP_OPEN: '한정반 오픈',
  LIMITED_DROP_CLOSE: '한정반 마감',
  MEMBER_STATUS_CHANGE: '회원 상태 변경',
  PAYMENT_CANCEL: '결제 취소',
  STOCK_ADJUST: '재고 조정',
};

export const AUDIT_TARGET_TYPE_LABELS: Record<AdminAuditTargetType, string> = {
  PRODUCT: '상품',
  ORDER: '주문',
  COUPON: '쿠폰',
  LIMITED_DROP: '한정반',
  MEMBER: '회원',
  PAYMENT: '결제',
};

export const MEMBER_STATUS_LABELS: Record<MemberStatus, string> = {
  ACTIVE: '활성',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴',
};

export const MEMBER_ROLE_LABELS: Record<MemberRole, string> = {
  USER: '일반회원',
  ADMIN: '관리자',
};
