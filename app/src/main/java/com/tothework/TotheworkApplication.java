package com.tothework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 서비스 간 이벤트 리스너(@ApplicationModuleListener)는 비동기로 돈다. 토큰 정리 같은 야간 작업이 있다. */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class TotheworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TotheworkApplication.class, args);
    }
}
