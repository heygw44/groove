import type { AdminAuditLogListParams } from '@/types/adminAuditLog';
import type { AdminMemberListParams } from '@/types/adminMember';
import type { PopularProductParams, StatsPeriodParams } from '@/types/adminStats';
import type { CatalogImportJobListParams, CatalogLookupParams } from '@/types/catalog';
import type { AdminCouponListParams, MemberCouponStatus } from '@/types/coupon';
import type { AdminLimitedDropListParams, LimitedDropStatus } from '@/types/limitedDrop';
import type { AdminOrderListParams, OrderListParams } from '@/types/order';
import type {
  AdminAlbumListParams,
  AdminProductListParams,
  ProductListParams,
} from '@/types/product';
import type { ReviewListParams } from '@/types/review';
import type { WishlistListParams } from '@/types/wishlist';

export const memberKeys = {
  me: ['member', 'me'] as const,
};

export const addressKeys = {
  all: ['addresses'] as const,
};

export const cartKeys = {
  all: ['cart'] as const,
};

export const productKeys = {
  all: ['products'] as const,
  list: (params: ProductListParams) => ['products', params] as const,
  detail: (id: number) => ['product', id] as const,
};

// 'products' 트리 밖에 둔다. useToggleWishlist 낙관적 갱신이 ['products'] 캐시를 전부 PageResponse 로 가정하고 훑기 때문이다.
export const albumKeys = {
  all: ['albums'] as const,
  detail: (id: number) => ['albums', id] as const,
};

export const adminProductKeys = {
  all: ['adminProducts'] as const,
  list: (params: AdminProductListParams) => ['adminProducts', params] as const,
  detail: (id: number) => ['adminProducts', 'detail', id] as const,
};

// detail 을 'order' 단수로 분리해 ['orders'] 무효화가 상세 캐시를 건드리지 않게 한다 (productKeys 와 같은 이유).
export const orderKeys = {
  all: ['orders'] as const,
  list: (params: OrderListParams) => ['orders', params] as const,
  detail: (id: number) => ['order', id] as const,
};

// list 를 'list' sub-prefix 로 분리해 상태 변경 후 상세는 건드리지 않고 목록만 무효화한다.
export const adminOrderKeys = {
  all: ['adminOrders'] as const,
  lists: ['adminOrders', 'list'] as const,
  list: (params: AdminOrderListParams) => ['adminOrders', 'list', params] as const,
  detail: (id: number) => ['adminOrders', 'detail', id] as const,
};

export const reviewKeys = {
  all: ['reviews'] as const,
  product: (productId: number) => ['reviews', productId] as const,
  list: (productId: number, params: ReviewListParams) =>
    ['reviews', productId, 'list', params] as const,
  eligibility: (productId: number) => ['reviews', productId, 'eligibility'] as const,
  stats: (productId: number) => ['reviews', productId, 'stats'] as const,
};

export const wishlistKeys = {
  all: ['wishlist'] as const,
  list: (params: WishlistListParams) => ['wishlist', params] as const,
};

export const couponKeys = {
  all: ['coupons'] as const,
  mine: (status?: MemberCouponStatus) => ['coupons', 'me', status ?? 'all'] as const,
  available: (orderAmount: number) => ['coupons', 'available', orderAmount] as const,
};

export const adminCouponKeys = {
  all: ['adminCoupons'] as const,
  list: (params: AdminCouponListParams) => ['adminCoupons', params] as const,
};

export const limitedDropKeys = {
  all: ['limitedDrops'] as const,
  list: (status?: LimitedDropStatus) => ['limitedDrops', status ?? 'all'] as const,
  detail: (id: number) => ['limitedDrop', id] as const,
  // detail 키 접두사가 단수 'limitedDrop' 이라 이 하나로 모든 상세 캐시를 한 번에 무효화한다.
  details: ['limitedDrop'] as const,
};

// list 를 'list' sub-prefix 로 분리해 오픈/마감 등 상태 변경 후 상세는 건드리지 않고 목록만 무효화한다.
export const adminLimitedDropKeys = {
  all: ['adminLimitedDrops'] as const,
  lists: ['adminLimitedDrops', 'list'] as const,
  list: (params: AdminLimitedDropListParams) => ['adminLimitedDrops', 'list', params] as const,
  detail: (id: number) => ['adminLimitedDrops', 'detail', id] as const,
};

export const adminStatsKeys = {
  all: ['adminStats'] as const,
  summary: ['adminStats', 'summary'] as const,
  dailySales: (params: StatsPeriodParams) => ['adminStats', 'dailySales', params] as const,
  popularProducts: (params: PopularProductParams) =>
    ['adminStats', 'popularProducts', params] as const,
  limitedDrops: ['adminStats', 'limitedDrops'] as const,
};

export const referenceKeys = {
  genres: ['genres'] as const,
  labels: ['labels'] as const,
  artists: (keyword?: string) => ['artists', keyword] as const,
  artist: (id: number) => ['artist', id] as const,
};

// list 를 'list' sub-prefix 로 분리해 상태 변경 후 상세는 건드리지 않고 목록만 무효화한다(adminOrderKeys 와 동일 이유).
export const adminMemberKeys = {
  all: ['adminMembers'] as const,
  lists: ['adminMembers', 'list'] as const,
  list: (params: AdminMemberListParams) => ['adminMembers', 'list', params] as const,
  detail: (id: number) => ['adminMembers', 'detail', id] as const,
};

export const tasteProfileKeys = {
  mine: ['tasteProfile', 'me'] as const,
};

export const recommendKeys = {
  all: ['recommend'] as const,
  home: ['recommend', 'home'] as const,
  related: (productId: number) => ['recommend', 'related', productId] as const,
};

export const recentViewKeys = {
  all: ['recentViews'] as const,
};

export const adminAuditLogKeys = {
  all: ['adminAuditLogs'] as const,
  list: (params: AdminAuditLogListParams) => ['adminAuditLogs', params] as const,
};

export const adminAlbumKeys = {
  all: ['adminAlbums'] as const,
  list: (params: AdminAlbumListParams) => ['adminAlbums', params] as const,
};

// list 를 'list' sub-prefix 로 분리해 잡 실행·재시작 후 목록만 무효화한다.
export const adminCatalogImportJobKeys = {
  all: ['adminCatalogImportJobs'] as const,
  lists: ['adminCatalogImportJobs', 'list'] as const,
  list: (params: CatalogImportJobListParams) =>
    ['adminCatalogImportJobs', 'list', params] as const,
  detail: (jobExecutionId: number) =>
    ['adminCatalogImportJobs', 'detail', jobExecutionId] as const,
};

export const adminCatalogLookupKeys = {
  all: ['adminCatalogLookup'] as const,
  list: (params: CatalogLookupParams) => ['adminCatalogLookup', params] as const,
  release: (discogsReleaseId: number) =>
    ['adminCatalogRelease', discogsReleaseId] as const,
};
