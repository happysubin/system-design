package com.library.repository

import com.library.entity.DailyStat
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
@DataJpaTest
class DailyStatRepositoryTest: StringSpec() {

    @Autowired
    private lateinit var dailyStatRepository: DailyStatRepository

    @Autowired
    lateinit var repository: DailyStatRepository

    @Autowired
    lateinit var em: EntityManager


    init {
        "dailyStat 엔티티 저장 성공" {

            val givenQuery = "HTTP"
            val givenLocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
            val saved = repository.save(DailyStat(query = givenQuery, eventDateTime = givenLocalDateTime))

            em.clear()

            val result = repository.findById(saved.id).orElseThrow()

            result.id shouldBe saved.id
            result.query shouldBe givenQuery
            result.eventDateTime shouldBe givenLocalDateTime
        }

        "쿼리의 카운트를 조회" {
            val givenQuery = "HTTP"
            val now = LocalDateTime.of(2026, 1, 1, 0, 0)
            val stat1 = DailyStat(query = givenQuery, eventDateTime = now.plusMinutes(10))
            val stat2 = DailyStat(query = givenQuery, eventDateTime = now.minusMinutes(10))
            val stat3 = DailyStat(query = givenQuery, eventDateTime = now.plusMinutes(11))
            val stat4 = DailyStat(query = "JAVA", eventDateTime = now.plusMinutes(10))

            repository.saveAll(listOf(stat1, stat2, stat3, stat4))

            val count = dailyStatRepository.countByQueryAndEventDateTimeBetween(givenQuery, now, now.plusDays(1))

            count shouldBe 2

        }
    }
}