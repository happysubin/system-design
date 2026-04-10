package com.pglab.payment.api

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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorizePaymentApiTest(
    @Autowired private val mockMvc: MockMvc,
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
                              "approvalCode": "card-appr-001"
                            },
                            {
                              "instrumentType": "BANK_ACCOUNT",
                              "requestedAmount": 10000,
                              "approvedAmount": 10000,
                              "pgTransactionId": "bank-a-001"
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
                              "pgTransactionId": "card-b-001"
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
    }
}
