import { ProductForm } from '@/components/admin/ProductForm';

export default function AdminProductCreatePage() {
  return (
    <div>
      <h2 className="mb-5 text-[17px] font-bold tracking-tight">상품 등록</h2>
      <ProductForm />
    </div>
  );
}
