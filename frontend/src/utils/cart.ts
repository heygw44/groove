import { CART_MAX_QUANTITY } from '@/constants/cart';
import type { CartItem } from '@/types/cart';

export const isCartItemSoldOut = (item: CartItem) =>
  item.productStatus === 'SOLD_OUT' || item.stockQuantity <= 0;

export const sumSelectedSubtotal = (items: CartItem[], selectedIds: ReadonlySet<number>) =>
  items.filter((item) => selectedIds.has(item.id)).reduce((sum, item) => sum + item.subtotal, 0);

export const maxSelectableQuantity = (item: CartItem) =>
  Math.min(CART_MAX_QUANTITY, item.stockQuantity);
