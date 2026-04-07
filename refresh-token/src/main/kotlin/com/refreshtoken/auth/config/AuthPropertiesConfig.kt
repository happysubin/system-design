package com.refreshtoken.auth.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthPropertiesConfig {
    @Bean
    fun authClock(): Clock = Clock.systemUTC()
}
