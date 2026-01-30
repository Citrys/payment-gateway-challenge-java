package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ApiPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentRepository paymentRepository;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    ApiPaymentResponse payment = new ApiPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

    paymentRepository.save(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.card_number_last_four").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiry_month").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiry_year").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenValidPaymentWithCardEndingInOddNumberThenAuthorized() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    UUID paymentId = UUID.randomUUID();
    mvc.perform(MockMvcRequestBuilders.put("/payment/" + paymentId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("1111"))
        .andExpect(jsonPath("$.expiry_month").value(12))
        .andExpect(jsonPath("$.expiry_year").value(2026))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1000))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void whenValidPaymentWithCardEndingInEvenNumberThenDeclined() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111112",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "GBP",
          "amount": 2000,
          "cvv": 456
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.card_number_last_four").value("1112"))
        .andExpect(jsonPath("$.expiry_month").value(12))
        .andExpect(jsonPath("$.expiry_year").value(2026))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(2000))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void whenCardNumberTooShortThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "123456789",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCardNumberTooLongThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "12345678901234567890",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCardNumberContainsNonNumericCharactersThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "411111111111111a",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenExpiryMonthLessThanOneThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 0,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenExpiryMonthGreaterThanTwelveThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 13,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenExpiryDateInPastThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 1,
          "expiry_year": 2024,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCurrencyNotSupportedThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "JPY",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCurrencyLengthNotThreeCharactersThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "US",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenAmountIsZeroThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 0,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenAmountIsNegativeThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": -100,
          "cvv": 123
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCvvTooShortThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 12
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCvvTooLongThenRejected() throws Exception {
    String paymentRequest = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": 12345
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenValidPaymentWithFourDigitCvvThenAuthorized() throws Exception {
    String paymentRequest = """
        {
          "card_number": "5555555555554445",
          "expiry_month": 3,
          "expiry_year": 2027,
          "currency": "EUR",
          "amount": 5000,
          "cvv": 9999
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("4445"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void whenValidPaymentWith14DigitCardThenAuthorized() throws Exception {
    String paymentRequest = """
        {
          "card_number": "12345678901235",
          "expiry_month": 6,
          "expiry_year": 2028,
          "currency": "GBP",
          "amount": 500,
          "cvv": 111
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("1235"))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void whenValidPaymentWith19DigitCardThenDeclined() throws Exception {
    String paymentRequest = """
        {
          "card_number": "1234567890123456782",
          "expiry_month": 9,
          "expiry_year": 2027,
          "currency": "USD",
          "amount": 7500,
          "cvv": 888
        }
        """;

    mvc.perform(MockMvcRequestBuilders.put("/payment/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentRequest))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.card_number_last_four").value("6782"))
        .andExpect(jsonPath("$.id").exists());
  }
}
