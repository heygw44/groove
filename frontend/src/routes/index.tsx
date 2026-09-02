import { createBrowserRouter } from 'react-router-dom';

import { MyPageLayout } from '@/components/layout/MyPageLayout';
import { RootLayout } from '@/components/layout/RootLayout';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';
import LoginPage from '@/pages/auth/LoginPage';
import SignupPage from '@/pages/auth/SignupPage';
import CartPage from '@/pages/cart/CartPage';
import HomePage from '@/pages/HomePage';
import LimitedDropDetailPage from '@/pages/limited/LimitedDropDetailPage';
import LimitedDropListPage from '@/pages/limited/LimitedDropListPage';
import AddressListPage from '@/pages/mypage/AddressListPage';
import MyPage from '@/pages/mypage/MyPage';
import NotFoundPage from '@/pages/NotFoundPage';
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
        ],
      },
      {
        path: 'admin',
        element: (
          <AdminRoute>
            <AdminDashboardPage />
          </AdminRoute>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
