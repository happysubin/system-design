package com.paymentlab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PaymentLabApplication

fun main(args: Array<String>) {
    runApplication<PaymentLabApplication>(*args)
}
