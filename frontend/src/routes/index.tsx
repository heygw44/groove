import { createBrowserRouter } from 'react-router-dom';

import { AdminLayout } from '@/components/layout/AdminLayout';
import { MyPageLayout } from '@/components/layout/MyPageLayout';
import { RootLayout } from '@/components/layout/RootLayout';
import HomePage from '@/pages/HomePage';
import NotFoundPage from '@/pages/NotFoundPage';
import { AdminRoute } from '@/routes/AdminRoute';
import {
  AddressListPage,
  AdminAuditLogsPage,
  AdminCatalogImportJobsPage,
  AdminCouponsPage,
  AdminDashboardPage,
  AdminLimitedDropsPage,
  AdminMembersPage,
  AdminOrdersPage,
  AdminProductCreatePage,
  AdminProductEditPage,
  AdminProductListPage,
  AlbumDetailPage,
  AlbumWatchListPage,
  CartPage,
  CouponBoxPage,
  LimitedDropDetailPage,
  LimitedDropListPage,
  LoginPage,
  MyPage,
  NotificationListPage,
  OrderDetailPage,
  OrderFormPage,
  OrderListPage,
  PaymentFailPage,
  PaymentSuccessPage,
  ProductDetailPage,
  ProductListPage,
  RecentViewsPage,
  SignupPage,
  TastePage,
  WishlistPage,
} from '@/routes/lazyPages';
import { PrivateRoute } from '@/routes/PrivateRoute';
import { RouteErrorBoundary } from '@/routes/RouteErrorBoundary';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    errorElement: <RouteErrorBoundary />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'products/:id', element: <ProductDetailPage /> },
      { path: 'limited-drops', element: <LimitedDropListPage /> },
      { path: 'limited-drops/:id', element: <LimitedDropDetailPage /> },
      {
        path: 'cart',
        element: (
          <PrivateRoute>
            <CartPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'orders/new',
        element: (
          <PrivateRoute>
            <OrderFormPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'payments/success',
        element: (
          <PrivateRoute>
            <PaymentSuccessPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'payments/fail',
        element: (
          <PrivateRoute>
            <PaymentFailPage />
          </PrivateRoute>
        ),
      },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
      { path: 'albums/:id', element: <AlbumDetailPage /> },
      {
        element: (
          <PrivateRoute>
            <MyPageLayout />
          </PrivateRoute>
        ),
        children: [
          {
            path: 'mypage',
            children: [
              { index: true, element: <MyPage /> },
              { path: 'addresses', element: <AddressListPage /> },
              { path: 'wishlist', element: <WishlistPage /> },
              { path: 'album-watches', element: <AlbumWatchListPage /> },
              { path: 'coupons', element: <CouponBoxPage /> },
              { path: 'taste', element: <TastePage /> },
              { path: 'recent', element: <RecentViewsPage /> },
            ],
          },
          { path: 'orders', element: <OrderListPage /> },
          { path: 'orders/:id', element: <OrderDetailPage /> },
          { path: 'notifications', element: <NotificationListPage /> },
        ],
      },
      {
        path: 'admin',
        element: (
          <AdminRoute>
            <AdminLayout />
          </AdminRoute>
        ),
        children: [
          { index: true, element: <AdminDashboardPage /> },
          { path: 'products', element: <AdminProductListPage /> },
          { path: 'products/new', element: <AdminProductCreatePage /> },
          { path: 'products/:id/edit', element: <AdminProductEditPage /> },
          { path: 'catalog/import-jobs', element: <AdminCatalogImportJobsPage /> },
          { path: 'orders', element: <AdminOrdersPage /> },
          { path: 'coupons', element: <AdminCouponsPage /> },
          { path: 'limited-drops', element: <AdminLimitedDropsPage /> },
          { path: 'members', element: <AdminMembersPage /> },
          { path: 'audit-logs', element: <AdminAuditLogsPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
