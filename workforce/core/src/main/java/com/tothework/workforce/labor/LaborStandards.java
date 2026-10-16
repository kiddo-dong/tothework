package com.tothework.workforce.labor;

import com.tothework.shared.error.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;

/** 근로기준법·최저임금법의 수치 기준을 한곳에 모아둔다. */
public final class LaborStandards {

    /** 2026년 최저시급 (원). 매년 고시에 따라 갱신 필요. */
    public static final int MINIMUM_HOURLY_WAGE = 10_320;

    /** 주 최대 근로시간 상한 (소정 40시간 + 연장 12시간, 분 단위). */
    public static final int MAX_WEEKLY_WORK_MINUTES = 52 * 60;

    /** 1주 법정 기준 근로시간. 이를 초과하는 근로는 연장근로다. */
    public static final int STANDARD_WEEKLY_HOURS = 40;

    /** 1일 법정 기준 근로시간. 이를 초과하는 근로는 연장근로다. */
    public static final int STANDARD_DAILY_HOURS = 8;

    /** 연소근로자(18세 미만)의 1일 최대 근로시간 (분). 당사자 합의 연장(1일 1시간)은 미지원. */
    public static final int MINOR_MAX_DAILY_WORK_MINUTES = 7 * 60;

    /** 연소근로자(18세 미만)의 1주 최대 근로시간 (분). */
    public static final int MINOR_MAX_WEEKLY_WORK_MINUTES = 35 * 60;

    /** 주휴수당 발생 기준 주간 근로시간 (분). */
    public static final int WEEKLY_HOLIDAY_ELIGIBLE_MINUTES = 15 * 60;

    /** 사업소득 원천징수율 (소득세 3% + 지방소득세 0.3%). */
    public static final double WITHHOLDING_TAX_RATE = 0.033;

    /**
     * 4대보험 근로자 부담 요율 합계의 근사치
     * (국민연금 4.5% + 건강보험 3.545% + 장기요양 0.459% + 고용보험 0.9%). 매년 고시에 따라 갱신 필요.
     */
    public static final double FOUR_INSURANCES_EMPLOYEE_RATE = 0.09404;

    private LaborStandards() {
    }

    /** 근로기준법상 연소근로자(만 18세 미만) 여부. 생년월일 미입력(소셜 가입 등) 시 성인으로 간주한다. */
    public static boolean isMinor(LocalDate birthDate, LocalDate onDate) {
        return birthDate != null && Period.between(birthDate, onDate).getYears() < 18;
    }

    /** 해당 날짜가 속한 ISO 주(월~일)의 월요일. 주 단위 근로시간 집계 범위를 잡을 때 공통으로 쓴다. */
    public static LocalDate mondayOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** 해당 날짜가 속한 ISO 주(월~일)의 일요일. */
    public static LocalDate sundayOfWeek(LocalDate date) {
        return date.with(DayOfWeek.SUNDAY);
    }

    /** 근로기준법 제54조: 근로시간 4시간 이상이면 30분, 8시간 이상이면 1시간 이상의 휴게를 줘야 한다. */
    public static int statutoryBreakMinutes(long workMinutes) {
        if (workMinutes >= 8 * 60) {
            return 60;
        }
        if (workMinutes >= 4 * 60) {
            return 30;
        }
        return 0;
    }

    /**
     * 체류시간(출근~퇴근)에 대해 법정 요건을 만족하는 최소 휴게시간을 구한다.
     * 휴게를 빼면 근로시간이 줄어 요구 휴게도 줄어드는 순환 관계가 있어, 30분 단위 후보 중 최소값을 찾는다.
     */
    public static int defaultBreakMinutes(long spanMinutes) {
        for (int candidate : new int[] {0, 30, 60}) {
            if (candidate >= statutoryBreakMinutes(spanMinutes - candidate)) {
                return candidate;
            }
        }
        return 60;
    }

    /**
     * 휴게시간을 결정·검증한다. 미입력 시 법정 강제 정책이면 최소치를 자동 적용하고, 자율 정책이면 0분으로 둔다.
     * 입력값은 체류시간보다 짧아야 하며, 법정 강제 정책에서는 법정 최소치 미만을 허용하지 않는다.
     */
    public static int resolveBreakMinutes(boolean statutory, long spanMinutes, Integer requested) {
        if (requested == null) {
            return statutory ? defaultBreakMinutes(spanMinutes) : 0;
        }
        if (requested >= spanMinutes) {
            throw new InvalidRequestException("휴게시간은 근무 체류시간보다 짧아야 합니다.");
        }
        if (statutory) {
            int minimum = statutoryBreakMinutes(spanMinutes - requested);
            if (requested < minimum) {
                throw new InvalidRequestException(
                        "근로기준법상 최소 " + minimum + "분의 휴게가 필요합니다. (매장 설정에서 휴게 정책을 자율로 바꿀 수 있습니다)");
            }
        }
        return requested;
    }
}
