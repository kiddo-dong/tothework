package com.tothework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/** 서비스 간 이벤트 리스너(@ApplicationModuleListener)는 비동기로 돈다. */
@EnableAsync
@SpringBootApplication
public class TotheworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TotheworkApplication.class, args);
    }
}
