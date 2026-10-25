package com.tothework;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 서비스 간 이벤트 리스너(@ApplicationModuleListener)는 비동기로 돈다. 토큰 정리 같은 야간 작업이 있다. */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class TotheworkApplication {

    /**
     * 서비스의 기준 시간대. 근로기준법이 적용되는 한국 매장만 다루므로 하나로 고정한다.
     *
     * <p>코드 곳곳이 {@code LocalDate.now()}로 "오늘"을 정하고, 출퇴근 시각을 시간대 없이 저장하며, 야간근로(22~06시)를
     * 그 시각으로 판정한다. 서버 시간대에 맡기면 EC2 기본값(UTC)에서 오전 9시 전 출근이 전날로 기록되고 낮 근무에
     * 야간수당이 붙는다. OS 설정에 기대지 않도록 기동할 때 정한다.
     */
    static final String SERVICE_TIME_ZONE = "Asia/Seoul";

    public static void main(String[] args) {
        useServiceTimeZone();
        SpringApplication.run(TotheworkApplication.class, args);
    }

    /** 스케줄러·로그·날짜 계산이 쓰는 JVM 기본 시간대를 서비스 시간대로 맞춘다. 애플리케이션이 뜨기 전에 불러야 한다. */
    static void useServiceTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(SERVICE_TIME_ZONE));
    }
}
