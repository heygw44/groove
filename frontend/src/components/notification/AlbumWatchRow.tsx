import { Link } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import type { AlbumWatch } from '@/types/albumWatch';
import { formatServerDateTime } from '@/utils/formatDate';

interface AlbumWatchRowProps {
  item: AlbumWatch;
  onRemove: () => void;
  removing?: boolean;
}

export function AlbumWatchRow({ item, onRemove, removing = false }: AlbumWatchRowProps) {
  return (
    <div className="flex items-center justify-between gap-3 px-1 py-3">
      <div className="min-w-0">
        <Link
          to={`/albums/${item.albumId}`}
          className="line-clamp-1 text-sm font-medium text-content hover:text-accent-hover"
        >
          {item.albumTitle}
        </Link>
        <p className="mt-1 text-xs text-content-subtle">{formatServerDateTime(item.createdAt)}</p>
      </div>
      <Button variant="secondary" size="sm" loading={removing} onClick={onRemove}>
        구독 해지
      </Button>
    </div>
  );
}
