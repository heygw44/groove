package com.groove.auth.repository;

/** refresh_rotate.lua 반환 코드. */
public enum RotationResult {
	NOT_FOUND, ROTATED, GRACE, REUSED
}
