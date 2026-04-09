package com.paymentlab.payment.api

import com.paymentlab.inventory.application.InsufficientInventoryReservationException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [PaymentController::class, WebhookController::class])
class PaymentErrorHandler {

    @ExceptionHandler(InsufficientInventoryReservationException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleInsufficientStock(): Map<String, String> {
        return mapOf("message" to "insufficient stock")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(exception: IllegalArgumentException): Map<String, String> {
        return mapOf("message" to (exception.message ?: "bad request"))
    }

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleConflict(exception: IllegalStateException): Map<String, String> {
        return mapOf("message" to (exception.message ?: "conflict"))
    }
}
