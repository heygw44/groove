import { createBrowserRouter } from 'react-router-dom';

import { AdminLayout } from '@/components/layout/AdminLayout';
import { MyPageLayout } from '@/components/layout/MyPageLayout';
import { RootLayout } from '@/components/layout/RootLayout';
import AdminCouponsPage from '@/pages/admin/AdminCouponsPage';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';
import AdminLimitedDropsPage from '@/pages/admin/AdminLimitedDropsPage';
import AdminMembersPage from '@/pages/admin/AdminMembersPage';
import AdminOrdersPage from '@/pages/admin/AdminOrdersPage';
import AdminProductCreatePage from '@/pages/admin/AdminProductCreatePage';
import AdminProductEditPage from '@/pages/admin/AdminProductEditPage';
import AdminProductListPage from '@/pages/admin/AdminProductListPage';
import LoginPage from '@/pages/auth/LoginPage';
import SignupPage from '@/pages/auth/SignupPage';
import CartPage from '@/pages/cart/CartPage';
import HomePage from '@/pages/HomePage';
import LimitedDropDetailPage from '@/pages/limited/LimitedDropDetailPage';
import LimitedDropListPage from '@/pages/limited/LimitedDropListPage';
import AddressListPage from '@/pages/mypage/AddressListPage';
import CouponBoxPage from '@/pages/mypage/CouponBoxPage';
import MyPage from '@/pages/mypage/MyPage';
import WishlistPage from '@/pages/mypage/WishlistPage';
import NotFoundPage from '@/pages/NotFoundPage';
import OrderDetailPage from '@/pages/order/OrderDetailPage';
import OrderFormPage from '@/pages/order/OrderFormPage';
import OrderListPage from '@/pages/order/OrderListPage';
import ProductDetailPage from '@/pages/product/ProductDetailPage';
import ProductListPage from '@/pages/product/ProductListPage';
import { AdminRoute } from '@/routes/AdminRoute';
import { PrivateRoute } from '@/routes/PrivateRoute';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
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
        path: 'orders',
        element: (
          <PrivateRoute>
            <OrderListPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'orders/:id',
        element: (
          <PrivateRoute>
            <OrderDetailPage />
          </PrivateRoute>
        ),
      },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
      {
        path: 'mypage',
        element: (
          <PrivateRoute>
            <MyPageLayout />
          </PrivateRoute>
        ),
        children: [
          { index: true, element: <MyPage /> },
          { path: 'addresses', element: <AddressListPage /> },
          { path: 'wishlist', element: <WishlistPage /> },
          { path: 'coupons', element: <CouponBoxPage /> },
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
          { path: 'orders', element: <AdminOrdersPage /> },
          { path: 'coupons', element: <AdminCouponsPage /> },
          { path: 'limited-drops', element: <AdminLimitedDropsPage /> },
          { path: 'members', element: <AdminMembersPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
