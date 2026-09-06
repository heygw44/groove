import { useMutation, useQueryClient } from '@tanstack/react-query';

import { updateTasteProfile } from '@/api/recommend';
import { limitedDropKeys, recommendKeys, tasteProfileKeys } from '@/hooks/queries/queryKeys';
import type { TasteProfileUpdateRequest } from '@/types/recommend';

export const useUpdateTasteProfile = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: TasteProfileUpdateRequest) => updateTasteProfile(payload),
    onSuccess: (data) => {
      queryClient.setQueryData(tasteProfileKeys.mine, data);
      // 홈 추천은 서버가 취향 프로필로 계산하므로 다시 받아야 한다.
      queryClient.invalidateQueries({ queryKey: recommendKeys.all });
      // 한정반 목록의 tasteMatch 도 프로필 기준으로 계산되어 함께 갱신한다.
      queryClient.invalidateQueries({ queryKey: limitedDropKeys.all });
      // 상세도 같은 이유로 tasteMatch 가 바뀔 수 있어 무효화한다.
      queryClient.invalidateQueries({ queryKey: limitedDropKeys.details });
    },
  });
};
