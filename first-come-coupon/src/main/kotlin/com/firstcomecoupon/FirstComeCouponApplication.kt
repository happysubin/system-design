package com.firstcomecoupon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class FirstComeCouponApplication

fun main(args: Array<String>) {
    runApplication<FirstComeCouponApplication>(*args)
}
