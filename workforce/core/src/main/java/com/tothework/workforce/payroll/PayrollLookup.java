package com.tothework.workforce.payroll;

import com.tothework.workforce.api.Payrolls;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 다른 서비스가 급여 숫자를 읽는 입구. 계산은 급여·대시보드 서비스의 것을 그대로 쓴다. */
@Service
@RequiredArgsConstructor
class PayrollLookup implements Payrolls {

    private final PayrollService payrollService;
    private final StoreDashboardService storeDashboardService;

    @Override
    public long estimateNetPay(Long storeId, Long memberId, YearMonth month) {
        return payrollService.estimateNetPay(storeId, memberId, month);
    }

    @Override
    public LaborCost laborCostOf(Long storeId, YearMonth month) {
        return storeDashboardService.laborCostOf(storeId, month);
    }
}
