import { Skeleton } from '@/components/common/Skeleton';

const SPEC_ROW_COUNT = 7;

export function ProductDetailSkeleton() {
  return (
    <div>
      <Skeleton className="h-5 w-24" />

      <div className="mt-4 grid gap-8 md:grid-cols-2">
        <Skeleton className="aspect-square w-full" />
        <div>
          <Skeleton className="h-8 w-3/4" />
          <Skeleton className="mt-2 h-4 w-1/3" />

          <div className="mt-4 flex flex-col gap-2">
            {Array.from({ length: SPEC_ROW_COUNT }, (_, index) => (
              <Skeleton key={index} className="h-5 w-2/3" />
            ))}
          </div>

          <div className="mt-3 flex flex-wrap gap-1.5">
            <Skeleton className="h-6 w-16 rounded-full" />
            <Skeleton className="h-6 w-16 rounded-full" />
            <Skeleton className="h-6 w-16 rounded-full" />
          </div>

          <Skeleton className="mt-3 h-4 w-1/3" />

          <div className="mt-6 flex flex-col gap-4 border-t border-line pt-6">
            <Skeleton className="h-7 w-24" />
            <Skeleton className="h-10 w-40" />
            <Skeleton className="h-10 w-full" />
          </div>
        </div>
      </div>
    </div>
  );
}
