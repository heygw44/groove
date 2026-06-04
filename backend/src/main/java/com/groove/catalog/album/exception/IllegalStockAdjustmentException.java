package com.groove.catalog.album.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 재고 조정 결과가 음수가 되는 경우. 400 응답으로 매핑된다.
 *
 * <p>Album.adjustStock(int) 안에서만 발생한다. DB CHECK (ck_album_stock_non_negative) 는
 * 최종 방어선이며 본 예외가 정상 경로의 거절 시점이다.
 */
public class IllegalStockAdjustmentException extends DomainException {

    public IllegalStockAdjustmentException() {
        super(ErrorCode.ALBUM_INVALID_STOCK);
    }
}
