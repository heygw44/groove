import { EDITION_TYPE_LABELS, getCountryLabel } from '@/constants/product';
import type { PressingSummary } from '@/types/catalog';
import type { Label } from '@/types/product';
import { formatDate } from '@/utils/formatDate';

interface PressingSpecTableProps {
  pressing: PressingSummary;
  label?: Label;
  releaseDate?: string;
  colorVariant?: string;
  pressingInfo?: string;
}

interface SpecRow {
  term: string;
  value?: string;
}

export function PressingSpecTable({
  pressing,
  label,
  releaseDate,
  colorVariant,
  pressingInfo,
}: PressingSpecTableProps) {
  const rows: SpecRow[] = [
    { term: '제작 국가', value: pressing.country && getCountryLabel(pressing.country) },
    { term: '제작 연도', value: pressing.pressingYear?.toString() },
    { term: '레이블', value: label?.name },
    { term: '발매일', value: releaseDate && formatDate(releaseDate) },
    { term: '카탈로그 번호', value: pressing.catalogNo },
    { term: '바코드', value: pressing.barcode },
    { term: '에디션', value: pressing.editionType && EDITION_TYPE_LABELS[pressing.editionType] },
    { term: '컬러반', value: colorVariant },
    { term: '사양', value: pressingInfo },
  ];

  return (
    <div>
      <dl className="mt-4 flex flex-col gap-2 text-sm">
        {rows
          .filter((row) => row.value !== undefined)
          .map((row) => (
            <div key={row.term} className="flex gap-2">
              <dt className="w-28 shrink-0 text-content-muted">{row.term}</dt>
              <dd className="m-0">{row.value}</dd>
            </div>
          ))}
      </dl>

      {pressing.discogsReleaseId !== undefined && (
        <p className="mt-2 text-xs text-content-subtle">
          Data provided by{' '}
          <a
            href={`https://www.discogs.com/release/${pressing.discogsReleaseId}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            Discogs
          </a>
          .
        </p>
      )}

      {pressing.stale && (
        <p className="mt-2 text-xs text-content-subtle">최신 정보를 확인하는 중입니다.</p>
      )}
    </div>
  );
}
