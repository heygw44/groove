export type MemberRole = 'USER' | 'ADMIN';

export type MemberStatus = 'ACTIVE' | 'WITHDRAWN';

export interface Member {
  id: number;
  email: string;
  nickname: string;
  role: MemberRole;
  status: MemberStatus;
  createdAt: string;
}

export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

export interface SignupResponse {
  id: number;
  email: string;
  nickname: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface NicknameUpdateRequest {
  nickname: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export interface Address {
  id: number;
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
  isDefault: boolean;
}

export interface AddressCreateRequest {
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
  isDefault: boolean;
}

export type AddressUpdateRequest = Omit<AddressCreateRequest, 'isDefault'>;
