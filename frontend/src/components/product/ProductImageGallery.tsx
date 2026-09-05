import { useState } from 'react';

import type { ProductImage } from '@/types/product';

interface ProductImageGalleryProps {
  images: ProductImage[];
}

function EmptyThumbnail() {
  return (
    <div className="flex h-full w-full items-center justify-center text-content-subtle">
      <svg
        width="64"
        height="64"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.4"
        aria-hidden
      >
        <circle cx="12" cy="12" r="9" />
        <circle cx="12" cy="12" r="3" />
      </svg>
    </div>
  );
}

export function ProductImageGallery({ images }: ProductImageGalleryProps) {
  const sorted = [...images].sort((a, b) => a.sortOrder - b.sortOrder);
  const [activeIndex, setActiveIndex] = useState(0);
  const active = sorted[activeIndex];

  return (
    <div>
      <div className="aspect-square overflow-hidden rounded-lg bg-surface-muted">
        {active ? (
          <img src={active.url} alt="" className="h-full w-full object-cover" />
        ) : (
          <EmptyThumbnail />
        )}
      </div>

      {sorted.length > 1 && (
        <div className="mt-3 flex gap-2 overflow-x-auto">
          {sorted.map((image, index) => (
            <button
              key={image.url}
              type="button"
              aria-current={index === activeIndex}
              onClick={() => setActiveIndex(index)}
              className={`h-16 w-16 shrink-0 overflow-hidden rounded-md border ${
                index === activeIndex ? 'border-content' : 'border-line-strong'
              }`}
            >
              <img src={image.url} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
