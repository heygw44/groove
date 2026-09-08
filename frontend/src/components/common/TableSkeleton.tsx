import { Skeleton } from '@/components/common/Skeleton';

interface TableSkeletonProps {
  columns: number;
  rows?: number;
}

// 관리자 목록 표가 로딩 중일 때 Spinner 대신 표 모양을 유지한다.
export function TableSkeleton({ columns, rows = 5 }: TableSkeletonProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <tbody>
          {Array.from({ length: rows }).map((_, rowIndex) => (
            <tr key={rowIndex} className="border-b border-line last:border-0">
              {Array.from({ length: columns }).map((_, columnIndex) => (
                <td key={columnIndex} className="py-2.5 pr-3">
                  <Skeleton className="h-4 w-full" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
