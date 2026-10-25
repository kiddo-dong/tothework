package com.tothework.workforce.payroll;

import com.tothework.workforce.config.WorkforceSchema;
import com.tothework.workforce.payroll.calc.MonthlyPay;
import com.tothework.workforce.payroll.calc.PayrollCalculator.PayrollResult;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 월 급여 기록 저장. 멤버·월마다 한 행이고, 조회할 때마다 다시 계산한 값으로 덮어쓴다.
 *
 * <p>예전에는 "찾아서 없으면 새로 만들어 저장"이라, 같은 달 급여를 처음 조회하는 요청 두 개가 겹치면 둘 다 새 행을
 * 넣으려다 유니크 키 위반으로 500이 났다. 한 문장짜리 upsert라 그런 경쟁이 없다. 엔티티로 다시 읽는 곳이 없어서
 * 저장소 대신 SQL로 둔다.
 */
@Component
@RequiredArgsConstructor
class PayrollRecords {

    private static final String TABLE = WorkforceSchema.NAME + ".payrolls";

    private final JdbcClient jdbcClient;

    void save(Long memberId, Long storeId, MonthlyPay pay) {
        PayrollResult result = pay.result();
        jdbcClient.sql("INSERT INTO " + TABLE + " (member_id, store_id, target_year, target_month, regular_pay,"
                        + " overtime_pay, night_pay, holiday_work_pay, weekly_holiday_pay, leave_pay, total_pay, deduction,"
                        + " net_pay, generated_at, created_at, updated_at)"
                        + " VALUES (:memberId, :storeId, :year, :month, :regularPay, :overtimePay, :nightPay,"
                        + " :holidayWorkPay, :weeklyHolidayPay, :leavePay, :totalPay, :deduction, :netPay,"
                        + " :now, :now, :now) AS incoming"
                        + " ON DUPLICATE KEY UPDATE regular_pay = incoming.regular_pay,"
                        + " overtime_pay = incoming.overtime_pay, night_pay = incoming.night_pay,"
                        + " holiday_work_pay = incoming.holiday_work_pay,"
                        + " weekly_holiday_pay = incoming.weekly_holiday_pay, leave_pay = incoming.leave_pay,"
                        + " total_pay = incoming.total_pay, deduction = incoming.deduction, net_pay = incoming.net_pay,"
                        + " generated_at = incoming.generated_at, updated_at = incoming.updated_at")
                .param("memberId", memberId)
                .param("storeId", storeId)
                .param("year", pay.month().getYear())
                .param("month", pay.month().getMonthValue())
                .param("regularPay", result.regularPay())
                .param("overtimePay", result.overtimePay())
                .param("nightPay", result.nightPay())
                .param("holidayWorkPay", result.holidayWorkPay())
                .param("weeklyHolidayPay", result.weeklyHolidayPay())
                .param("leavePay", pay.leavePay())
                .param("totalPay", pay.totalPay())
                .param("deduction", pay.deduction())
                .param("netPay", pay.netPay())
                // DB의 NOW()는 세션 시간대를 따르므로 앱의 시각을 넘긴다
                .param("now", LocalDateTime.now())
                .update();
    }

    void deleteAllOfStore(Long storeId) {
        jdbcClient.sql("DELETE FROM " + TABLE + " WHERE store_id = :storeId").param("storeId", storeId).update();
    }
}
