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
    { term: '국가', value: pressing.country && getCountryLabel(pressing.country) },
    { term: '프레싱 연도', value: pressing.pressingYear?.toString() },
    { term: '레이블', value: label?.name },
    { term: '발매일', value: releaseDate && formatDate(releaseDate) },
    { term: '카탈로그 번호', value: pressing.catalogNo },
    { term: '바코드', value: pressing.barcode },
    { term: '에디션', value: EDITION_TYPE_LABELS[pressing.editionType] },
    { term: '컬러', value: colorVariant },
    { term: '프레싱 정보', value: pressingInfo },
  ];

  return (
    <div>
      <dl className="mt-4 flex flex-col gap-2 text-sm">
        {rows
          .filter((row) => row.value !== undefined)
          .map((row) => (
            <div key={row.term} className="flex gap-2">
              <dt className="w-24 shrink-0 text-content-muted">{row.term}</dt>
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
    </div>
  );
}
