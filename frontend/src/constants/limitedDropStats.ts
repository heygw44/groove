/** 대시보드 시도 통계표(LimitedDropStatsTable)와 막대 그래프(LimitedDropAttemptChart)가 함께 쓰는 라벨. */
export type LimitedDropAttemptCountKey =
  'successCount' | 'soldOutCount' | 'alreadyPurchasedCount' | 'notOpenCount' | 'closedCount';

export const LIMITED_DROP_ATTEMPT_LABELS: Record<LimitedDropAttemptCountKey, string> = {
  successCount: '성공',
  soldOutCount: '매진',
  alreadyPurchasedCount: '중복 구매',
  notOpenCount: '오픈 전',
  closedCount: '마감 후',
};
