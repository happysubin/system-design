package com.library

import com.library.entity.DailyStat
import com.library.repository.DailyStatRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ApplicationStatRunner(
    private val dailyStatRepository: DailyStatRepository,
): CommandLineRunner {

    override fun run(vararg args: String) {
        dailyStatRepository.saveAll(
            listOf(
                DailyStat(query = "HTTP", eventDateTime = LocalDateTime.now()),
                DailyStat(query = "HTTP", eventDateTime = LocalDateTime.now()),
                DailyStat(query = "HTTP", eventDateTime = LocalDateTime.now()),
                DailyStat(query = "HTTP", eventDateTime = LocalDateTime.now()),
                DailyStat(query = "HTTP", eventDateTime = LocalDateTime.now()),
                DailyStat(query = "HTTP", eventDateTime = LocalDateTime.now()),

                DailyStat(query = "JAVA", eventDateTime = LocalDateTime.now()),
                DailyStat(query = "JAVA", eventDateTime = LocalDateTime.now()),

                DailyStat(query = "KOTLIN", eventDateTime = LocalDateTime.now())
            )
        )
    }
}