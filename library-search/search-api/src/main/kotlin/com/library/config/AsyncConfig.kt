package com.library.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.Arrays
import java.util.Arrays.*
import java.util.concurrent.Executor


@Configuration
class AsyncConfig: AsyncConfigurer {

    // io 집약적인지, cpu 집약적인지에 따라 설정 값이 달라진다.
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        val cpuCoreCount = Runtime.getRuntime().availableProcessors()
        executor.corePoolSize = cpuCoreCount
        // 스레드의 작업이 너무나도 빨리 끝나는 경우, 이걸 Integer.MAX로 두는 경우도 있다.
        executor.maxPoolSize = cpuCoreCount * 2
        // 0 이라면 큐를 사용하지 않음
        executor.queueCapacity = 10
        //유휴 스레드가 얼마나 놀고 있으면 정리할지
        executor.keepAliveSeconds = 60
        // 종료할 때 실행 중인 작업을 강제로 끊지 말고, 큐에 쌓인 작업까지 끝내게 하라
        executor.setWaitForTasksToCompleteOnShutdown(true)
        // 스프링 컨테이너가 이 executor 종료를 최대 60초까지 기다리게 하라
        executor.setAwaitTerminationSeconds(60)
        executor.setThreadNamePrefix("LS-")
        // 아래는 디폴트로 사용
//        executor.setRejectedExecutionHandler {  }

        executor.initialize()


        return executor
    }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return CustomAsyncExceptionHandler()
    }

    class CustomAsyncExceptionHandler : AsyncUncaughtExceptionHandler {

        val log: Logger = LoggerFactory.getLogger(this.javaClass)
        override fun handleUncaughtException(
            ex: Throwable,
            method: Method,
            vararg params: Any?
        ) {
            log.error("Failed to execute {}", ex.message)
            listOf(params).forEach({ param -> log.error("parameter value = {}", param) })
        }

    }
}