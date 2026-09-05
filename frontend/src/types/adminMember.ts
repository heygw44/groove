import type { OrderSummary } from '@/types/order';

import type { MemberRole, MemberStatus } from './member';

export interface AdminMemberSummary {
  id: number;
  email: string;
  nickname: string;
  role: MemberRole;
  status: MemberStatus;
  orderCount: number;
  totalPaymentAmount: number;
  createdAt: string;
}

export interface AdminMemberDetail extends AdminMemberSummary {
  usableCouponCount: number;
  recentOrders: OrderSummary[];
}

export interface AdminMemberListParams {
  keyword?: string;
  status?: MemberStatus;
  role?: MemberRole;
  page: number;
  size?: number;
}

/** 관리자가 회원 상태를 직접 바꿀 수 있는 값. WITHDRAWN 은 회원 본인만 전이할 수 있다. */
export type AdminMemberChangeableStatus = 'ACTIVE' | 'SUSPENDED';

export interface AdminMemberStatusChangeRequest {
  status: AdminMemberChangeableStatus;
  reason?: string;
}
