package com.tothework.store.api;

/** 스케줄·근태의 휴게시간 정책. */
public enum BreakPolicy {
    /** 근로기준법 제54조의 최소 휴게시간을 강제한다 (기본값). */
    STATUTORY,
    /** 휴게시간을 자유롭게 입력한다 (휴게 없이 조기퇴근하는 운영 방식 등). */
    FLEXIBLE
}
