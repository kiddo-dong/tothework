package com.tothework.workforce.api;

/** 급여 공제 방식. */
public enum TaxMode {
    /** 공제 없음 (세전 지급). */
    NONE,
    /** 사업소득 3.3% 원천징수 (소득세 3% + 지방소득세 0.3%). */
    WITHHOLDING_3_3,
    /** 4대보험 근로자 부담분 공제. */
    FOUR_INSURANCES
}
