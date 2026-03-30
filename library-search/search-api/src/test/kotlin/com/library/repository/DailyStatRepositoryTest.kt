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
    lateinit var repository: DailyStatRepository

    @Autowired
    lateinit var em: EntityManager


    init {
        "dailyStat 엔티티 저장 성공" {

            val givenQuery = "HTTP"
            val givenLocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
            val saved = repository.save(DailyStat(query = givenQuery, localDateTime = givenLocalDateTime))

            em.clear()

            val result = repository.findById(saved.id).orElseThrow()

            result.id shouldBe saved.id
            result.query shouldBe givenQuery
            result.localDateTime shouldBe givenLocalDateTime
        }
    }
}