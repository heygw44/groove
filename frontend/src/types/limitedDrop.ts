export type LimitedDropStatus = 'SCHEDULED' | 'OPEN' | 'SOLD_OUT' | 'CLOSED';

export interface LimitedDropProduct {
  id: number;
  title: string;
  artistName: string;
  price: number;
  thumbnailUrl?: string;
}

export interface LimitedDropSummary {
  id: number;
  product: LimitedDropProduct;
  totalQuantity: number;
  remainingQuantity: number;
  perMemberLimit: number;
  openAt: string;
  closeAt: string;
  status: LimitedDropStatus;
}

export interface LimitedDropListResponse {
  drops: LimitedDropSummary[];
  serverTime: string;
}

export interface LimitedDropDetail extends LimitedDropSummary {
  /** 비로그인이면 서버가 키 자체를 내려주지 않는다. */
  purchased?: boolean;
  serverTime: string;
}

export interface LimitedPurchaseRequest {
  addressId: number;
}

export interface LimitedPurchaseResponse {
  orderId: number;
  orderNumber: string;
  finalAmount: number;
  /** offset 없는 LocalDateTime(Asia/Seoul 암묵) - toServerMs() 로 변환해서 쓴다. */
  expiresAt: string;
}

export interface AdminLimitedDropListParams {
  status?: LimitedDropStatus;
  page?: number;
  size?: number;
}

export interface AdminLimitedDropSummary {
  id: number;
  productId: number;
  productTitle: string;
  totalQuantity: number;
  soldCount: number;
  perMemberLimit: number;
  openAt: string;
  closeAt: string;
  status: LimitedDropStatus;
  createdAt: string;
}

export interface AdminLimitedDrop {
  id: number;
  productId: number;
  productTitle: string;
  totalQuantity: number;
  soldCount: number;
  remainingQuantity: number;
  perMemberLimit: number;
  openAt: string;
  closeAt: string;
  status: LimitedDropStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AdminLimitedPurchase {
  id: number;
  memberId: number;
  memberNickname: string;
  orderId?: number;
  orderNumber?: string;
  orderStatus?: string;
  quantity: number;
  purchasedAt: string;
}

export interface AdminLimitedDropDetail {
  id: number;
  productId: number;
  productTitle: string;
  totalQuantity: number;
  soldCount: number;
  dbRemaining: number;
  /** OPEN 이 아니거나 Redis 카운터가 없으면 undefined. */
  redisRemaining?: number;
  perMemberLimit: number;
  openAt: string;
  closeAt: string;
  status: LimitedDropStatus;
  createdAt: string;
  updatedAt: string;
  purchases: AdminLimitedPurchase[];
}

export interface AdminLimitedDropCreateRequest {
  productId: number;
  totalQuantity: number;
  perMemberLimit?: number;
  openAt: string;
  closeAt: string;
}

export type AdminLimitedDropUpdateRequest = Partial<AdminLimitedDropCreateRequest>;
