package com.tothework.operations.api;

import java.time.DayOfWeek;
import java.util.List;

/** 거래처·발주 정보. 권한 확인은 부르는 쪽이 먼저 한다. */
public interface Suppliers {

    /** 그 요일에 발주 수량이 정해진 품목. 거래처·품목의 표시 순서를 따른다. */
    List<OrderLine> findOrderLinesFor(Long storeId, DayOfWeek day);
}
