import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { Address, AddressCreateRequest, AddressUpdateRequest } from '@/types/member';

/** 페이징이 아니라 배열이다. 기본 배송지 우선, 그다음 id 오름차순으로 내려온다. */
export const getAddresses = () =>
  unwrap(client.get<ApiResponse<Address[]>>('/members/me/addresses'));

export const createAddress = (payload: AddressCreateRequest) =>
  unwrap(client.post<ApiResponse<Address>>('/members/me/addresses', payload));

export const updateAddress = (addressId: number, payload: AddressUpdateRequest) =>
  unwrap(client.patch<ApiResponse<Address>>(`/members/me/addresses/${addressId}`, payload));

export const deleteAddress = async (addressId: number) => {
  await client.delete<ApiResponse<void>>(`/members/me/addresses/${addressId}`);
};

export const setDefaultAddress = (addressId: number) =>
  unwrap(client.patch<ApiResponse<Address>>(`/members/me/addresses/${addressId}/default`));
