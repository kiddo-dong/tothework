package com.tothework.store.api;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

/**
 * 스케줄 검증과 급여 계산이 쓰는 매장 설정.
 *
 * @param smallBusiness 상시 근로자 5인 미만. 연장·야간·휴일 가산수당과 주 52시간 상한이 적용되지 않는다
 * @param businessHours 요일별 영업시간. 정하지 않은 요일은 없다
 * @param payday        급여 지급일(매월 며칠). 미지정이면 null
 */
public record StorePolicy(Long storeId, boolean smallBusiness, BreakPolicy breakPolicy,
        Map<DayOfWeek, Hours> businessHours, Integer payday) {

    public record Hours(LocalTime openTime, LocalTime closeTime, boolean closed) {
    }
}
