import { useMutation, useQueryClient } from '@tanstack/react-query';

import { createAddress, deleteAddress, setDefaultAddress, updateAddress } from '@/api/address';
import { addressKeys } from '@/hooks/queries/queryKeys';
import type { AddressCreateRequest, AddressUpdateRequest } from '@/types/member';

/*
 * 네 가지 모두 응답만 반영하지 않고 목록을 다시 불러온다. 삭제와 기본 지정은
 * 서버가 다른 항목의 isDefault 도 바꾸기 때문에(기본 승계, 기존 기본 해제)
 * 응답 하나만 캐시에 꽂으면 목록이 실제와 어긋난다. 배열 하나뿐이라 재조회
 * 비용도 무시할 만하다.
 */
const useInvalidateAddresses = () => {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: addressKeys.all });
};

export const useCreateAddress = () => {
  const invalidate = useInvalidateAddresses();

  return useMutation({
    mutationFn: (payload: AddressCreateRequest) => createAddress(payload),
    onSuccess: invalidate,
  });
};

export const useUpdateAddress = () => {
  const invalidate = useInvalidateAddresses();

  return useMutation({
    mutationFn: ({ addressId, payload }: { addressId: number; payload: AddressUpdateRequest }) =>
      updateAddress(addressId, payload),
    onSuccess: invalidate,
  });
};

export const useDeleteAddress = () => {
  const invalidate = useInvalidateAddresses();

  return useMutation({
    mutationFn: (addressId: number) => deleteAddress(addressId),
    onSuccess: invalidate,
  });
};

export const useSetDefaultAddress = () => {
  const invalidate = useInvalidateAddresses();

  return useMutation({
    mutationFn: (addressId: number) => setDefaultAddress(addressId),
    onSuccess: invalidate,
  });
};
