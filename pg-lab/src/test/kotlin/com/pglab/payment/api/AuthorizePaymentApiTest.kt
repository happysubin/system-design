package com.pglab.payment.api

import com.pglab.payment.authorization.Authorization
import com.pglab.payment.ledger.LedgerEntry
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorizePaymentApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `결제 승인 API는 승인 결과를 저장하고 요약 응답을 반환한다`() {
        mockMvc.perform(
            post("/api/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "merchantId": "merchant-1",
                      "merchantOrderId": "order-api-001",
                      "totalAmount": 50000,
                      "currency": "KRW",
                      "lines": [
                        {
                          "lineReference": "line-1",
                          "payeeId": "seller-A",
                          "lineAmount": 10000,
                          "quantity": 1
                        },
                        {
                          "lineReference": "line-2",
                          "payeeId": "seller-B",
                          "lineAmount": 10000,
                          "quantity": 1
                        },
                        {
                          "lineReference": "line-3",
                          "payeeId": "seller-C",
                          "lineAmount": 30000,
                          "quantity": 1
                        }
                      ],
                      "allocations": [
                        {
                          "payerReference": "payer-A",
                          "allocationAmount": 20000,
                          "authorizations": [
                            {
                              "instrumentType": "CARD",
                              "requestedAmount": 10000,
                              "approvedAmount": 10000,
                              "pgTransactionId": "card-a-001",
                              "approvalCode": "card-appr-001",
                              "linePortions": [
                                {
                                  "lineReference": "line-1",
                                  "amount": 10000
                                }
                              ]
                            },
                            {
                              "instrumentType": "BANK_ACCOUNT",
                              "requestedAmount": 10000,
                              "approvedAmount": 10000,
                              "pgTransactionId": "bank-a-001",
                              "linePortions": [
                                {
                                  "lineReference": "line-2",
                                  "amount": 10000
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "payerReference": "payer-B",
                          "allocationAmount": 30000,
                          "authorizations": [
                            {
                              "instrumentType": "CARD",
                              "requestedAmount": 30000,
                              "approvedAmount": 30000,
                              "pgTransactionId": "card-b-001",
                              "linePortions": [
                                {
                                  "lineReference": "line-3",
                                  "amount": 30000
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderStatus").value("AUTHORIZED"))
            .andExpect(jsonPath("$.allocationCount").value(2))
            .andExpect(jsonPath("$.authorizationCount").value(3))
            .andExpect(jsonPath("$.ledgerEntryCount").value(3))
            .andExpect(jsonPath("$.upstreamResult").value("APPROVED"))
    }

    @Test
    fun `결제 승인 API는 주문 라인과 승인 라인 분배를 입력으로 받는다`() {
        mockMvc.perform(
            post("/api/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "merchantId": "merchant-1",
                      "merchantOrderId": "order-api-002",
                      "totalAmount": 50000,
                      "currency": "KRW",
                      "lines": [
                        {
                          "lineReference": "line-1",
                          "payeeId": "seller-A",
                          "lineAmount": 20000,
                          "quantity": 1
                        },
                        {
                          "lineReference": "line-2",
                          "payeeId": "seller-B",
                          "lineAmount": 30000,
                          "quantity": 1
                        }
                      ],
                      "allocations": [
                        {
                          "payerReference": "payer-A",
                          "allocationAmount": 50000,
                          "authorizations": [
                            {
                              "instrumentType": "CARD",
                              "requestedAmount": 50000,
                              "approvedAmount": 50000,
                              "pgTransactionId": "card-a-002",
                              "linePortions": [
                                {
                                  "lineReference": "line-1",
                                  "amount": 20000
                                },
                                {
                                  "lineReference": "line-2",
                                  "amount": 30000
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderStatus").value("AUTHORIZED"))
            .andExpect(jsonPath("$.allocationCount").value(1))
            .andExpect(jsonPath("$.authorizationCount").value(1))
            .andExpect(jsonPath("$.ledgerEntryCount").value(2))
            .andExpect(jsonPath("$.upstreamResult").value("APPROVED"))
    }

    @Test
    fun `결제 승인 API는 optional approvedAt을 authorization과 seller 원장 발생 시각으로 전달한다`() {
        val approvedAt = OffsetDateTime.parse("2026-04-18T10:15:30+09:00")

        mockMvc.perform(
            post("/api/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "merchantId": "merchant-1",
                      "merchantOrderId": "order-api-approved-at-001",
                      "totalAmount": 50000,
                      "currency": "KRW",
                      "lines": [
                        {
                          "lineReference": "line-1",
                          "payeeId": "seller-A",
                          "lineAmount": 20000,
                          "quantity": 1
                        },
                        {
                          "lineReference": "line-2",
                          "payeeId": "seller-B",
                          "lineAmount": 30000,
                          "quantity": 1
                        }
                      ],
                      "allocations": [
                        {
                          "payerReference": "payer-A",
                          "allocationAmount": 50000,
                          "authorizations": [
                            {
                              "instrumentType": "CARD",
                              "requestedAmount": 50000,
                              "approvedAmount": 50000,
                              "pgTransactionId": "card-api-approved-at-001",
                              "approvedAt": "2026-04-18T10:15:30+09:00",
                              "linePortions": [
                                {
                                  "lineReference": "line-1",
                                  "amount": 20000
                                },
                                {
                                  "lineReference": "line-2",
                                  "amount": 30000
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderStatus").value("AUTHORIZED"))
            .andExpect(jsonPath("$.ledgerEntryCount").value(2))

        entityManager.flush()
        entityManager.clear()

        val savedAuthorization = entityManager.createQuery(
            "select a from Authorization a where a.pgTransactionId = :pgTransactionId",
            Authorization::class.java,
        )
            .setParameter("pgTransactionId", "card-api-approved-at-001")
            .singleResult

        val savedLedgerEntries = entityManager.createQuery(
            "select l from LedgerEntry l where l.referenceTransactionId = :referenceTransactionId order by l.id",
            LedgerEntry::class.java,
        )
            .setParameter("referenceTransactionId", "card-api-approved-at-001")
            .resultList

        assertEquals(approvedAt.toInstant(), savedAuthorization.approvedAt?.toInstant())
        assertEquals(listOf(approvedAt.toInstant(), approvedAt.toInstant()), savedLedgerEntries.map { it.occurredAt.toInstant() })
    }
}
