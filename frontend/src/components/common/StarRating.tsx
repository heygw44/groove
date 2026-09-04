type StarRatingSize = 'sm' | 'md';

const STAR_PATH =
  'M12 2.5l2.9 6.02 6.6.86-4.86 4.53 1.28 6.58L12 17.77l-5.92 2.72 1.28-6.58L2.5 9.38l6.6-.86z';

const SIZE_CLASS: Record<StarRatingSize, string> = {
  sm: 'h-3.5 w-3.5',
  md: 'h-5 w-5',
};

interface StarRatingDisplayProps {
  value: number;
  size?: StarRatingSize;
}

/** 평점 표시 전용. 회색 별 5개 위에 같은 별 5개를 액센트 색으로 겹쳐 소수점만큼만 보이게 자른다. */
export function StarRatingDisplay({ value, size = 'md' }: StarRatingDisplayProps) {
  const starClass = SIZE_CLASS[size];
  const stars = (colorClass: string) => (
    <span className={`flex gap-0.5 ${colorClass}`} aria-hidden>
      {Array.from({ length: 5 }).map((_, index) => (
        <svg key={index} viewBox="0 0 24 24" fill="currentColor" className={starClass}>
          <path d={STAR_PATH} />
        </svg>
      ))}
    </span>
  );

  return (
    <span role="img" aria-label={`별점 ${value.toFixed(1)}점`} className="relative inline-flex">
      {stars('text-line-strong')}
      <span
        className="absolute inset-0 overflow-hidden"
        style={{ width: `${(value / 5) * 100}%` }}
      >
        {stars('text-accent')}
      </span>
    </span>
  );
}

interface StarRatingInputProps {
  value: number;
  onChange: (value: number) => void;
  name: string;
  invalid?: boolean;
  describedBy?: string;
}

/** 네이티브 라디오 5개라 방향키·Space 로 별점을 고르는 동작이 별도 구현 없이 따라온다. */
export function StarRatingInput({
  value,
  onChange,
  name,
  invalid = false,
  describedBy,
}: StarRatingInputProps) {
  return (
    <fieldset aria-invalid={invalid || undefined} aria-describedby={describedBy}>
      <legend className="sr-only">별점</legend>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <label
            key={star}
            className="cursor-pointer rounded-sm has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-content/40"
          >
            <input
              type="radio"
              name={name}
              value={star}
              checked={value === star}
              onChange={() => onChange(star)}
              className="sr-only"
            />
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              className={`h-6 w-6 ${star <= value ? 'text-accent' : 'text-line-strong'}`}
              aria-hidden
            >
              <path d={STAR_PATH} />
            </svg>
            <span className="sr-only">{star}점</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
