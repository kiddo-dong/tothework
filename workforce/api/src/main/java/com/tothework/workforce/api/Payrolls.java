package com.tothework.workforce.api;

import java.time.YearMonth;

/** 급여 숫자 조회. 권한 확인은 부르는 쪽이 먼저 한다. 금액이라 관리자 화면에서만 laborCostOf를 부른다. */
public interface Payrolls {

    /** 멤버의 그 달 예상 실수령액 (공제 후). 끝난 근태에 남은 스케줄을 모두 근무한다고 가정해 더한다. */
    long estimateNetPay(Long storeId, Long memberId, YearMonth month);

    /** 매장의 그 달 인건비 합계. 월간 대시보드와 같은 계산이다. */
    LaborCost laborCostOf(Long storeId, YearMonth month);

    /** @param totalNetPay 공제 후 실지급액 합계 */
    record LaborCost(long totalLaborCost, long totalNetPay) {
    }
}
