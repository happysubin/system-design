package com.paymentlab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PaymentLabApplication

fun main(args: Array<String>) {
    runApplication<PaymentLabApplication>(*args)
}
