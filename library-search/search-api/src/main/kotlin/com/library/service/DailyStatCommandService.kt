package com.library.service

import com.library.entity.DailyStat
import com.library.repository.DailyStatRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DailyStatCommandService(
    private val dailyStatRepository: DailyStatRepository
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun save(dailyStat: DailyStat) {
        log.info("Saving DailyStat: {}", dailyStat)
        dailyStatRepository.save(dailyStat)
    }
}