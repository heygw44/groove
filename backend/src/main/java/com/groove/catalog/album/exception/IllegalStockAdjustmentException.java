package com.groove.catalog.album.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 재고 조정 결과가 음수가 되는 경우. 400 응답으로 매핑된다.
 */
public class IllegalStockAdjustmentException extends DomainException {

    public IllegalStockAdjustmentException() {
        super(ErrorCode.ALBUM_INVALID_STOCK);
    }
}
