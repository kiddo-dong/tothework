package com.tothework.workforce.attendance;

/** 스케줄 대비 실제 근태의 판정 결과. */
public enum WorkComplianceStatus {
    /** 정상 근무. */
    NORMAL,
    /** 지각. */
    LATE,
    /** 조퇴. */
    EARLY_LEAVE,
    /** 지각 + 조퇴. */
    LATE_AND_EARLY_LEAVE,
    /** 무단결근 (스케줄 종료가 지났는데 출근 기록 없음). */
    ABSENT,
    /** 연차 사용. */
    ON_LEAVE,
    /** 아직 근무 중 (퇴근 전이라 조퇴 여부 판단 불가). */
    WORKING,
    /** 스케줄 없이 이루어진 근무. */
    EXTRA
}
