package com.groove.notification.service;

/**
 * 앨범에 새 프레싱이 등록된 이벤트. 배치 적재는 프레싱마다가 아니라 잡 종료 시 앨범당 한 번만 발행한다.
 *
 * @param albumId 새 프레싱이 붙은 앨범 ID
 * @param albumTitle 알림 문구에 쓸 발행 시점의 앨범명
 */
public record NewPressingEvent(Long albumId, String albumTitle) {
}
