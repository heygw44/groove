// 첫 진입 경로인 홈과 3줄짜리 404 는 routes/index.tsx 에서 eager 로 두고,
// 나머지는 여기서 라우트 단위로 쪼갠다.
import { lazy } from 'react';

export const AdminAuditLogsPage = lazy(() => import('@/pages/admin/AdminAuditLogsPage'));
export const AdminCatalogImportJobsPage = lazy(() => import('@/pages/admin/AdminCatalogImportJobsPage'));
export const AdminCouponsPage = lazy(() => import('@/pages/admin/AdminCouponsPage'));
export const AdminDashboardPage = lazy(() => import('@/pages/admin/AdminDashboardPage'));
export const AdminLimitedDropsPage = lazy(() => import('@/pages/admin/AdminLimitedDropsPage'));
export const AdminMembersPage = lazy(() => import('@/pages/admin/AdminMembersPage'));
export const AdminOrdersPage = lazy(() => import('@/pages/admin/AdminOrdersPage'));
export const AdminProductCreatePage = lazy(() => import('@/pages/admin/AdminProductCreatePage'));
export const AdminProductEditPage = lazy(() => import('@/pages/admin/AdminProductEditPage'));
export const AdminProductListPage = lazy(() => import('@/pages/admin/AdminProductListPage'));
export const LoginPage = lazy(() => import('@/pages/auth/LoginPage'));
export const SignupPage = lazy(() => import('@/pages/auth/SignupPage'));
export const CartPage = lazy(() => import('@/pages/cart/CartPage'));
export const LimitedDropDetailPage = lazy(() => import('@/pages/limited/LimitedDropDetailPage'));
export const LimitedDropListPage = lazy(() => import('@/pages/limited/LimitedDropListPage'));
export const AddressListPage = lazy(() => import('@/pages/mypage/AddressListPage'));
export const AlbumWatchListPage = lazy(() => import('@/pages/mypage/AlbumWatchListPage'));
export const CouponBoxPage = lazy(() => import('@/pages/mypage/CouponBoxPage'));
export const MyPage = lazy(() => import('@/pages/mypage/MyPage'));
export const RecentViewsPage = lazy(() => import('@/pages/mypage/RecentViewsPage'));
export const TastePage = lazy(() => import('@/pages/mypage/TastePage'));
export const WishlistPage = lazy(() => import('@/pages/mypage/WishlistPage'));
export const NotificationListPage = lazy(() => import('@/pages/notification/NotificationListPage'));
export const OrderDetailPage = lazy(() => import('@/pages/order/OrderDetailPage'));
export const OrderFormPage = lazy(() => import('@/pages/order/OrderFormPage'));
export const OrderListPage = lazy(() => import('@/pages/order/OrderListPage'));
export const PaymentFailPage = lazy(() => import('@/pages/payment/PaymentFailPage'));
export const PaymentSuccessPage = lazy(() => import('@/pages/payment/PaymentSuccessPage'));
export const AlbumDetailPage = lazy(() => import('@/pages/product/AlbumDetailPage'));
export const ProductDetailPage = lazy(() => import('@/pages/product/ProductDetailPage'));
export const ProductListPage = lazy(() => import('@/pages/product/ProductListPage'));
