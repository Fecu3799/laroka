package com.laroka.backend.payment.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.laroka.backend.shared.exception.BusinessException;

class MercadoPagoAdapterTest {

    private static final String PAYMENT_ID = "mp-payment-123";
    private static final String REFUND_URL =
            "https://api.mercadopago.com/v1/payments/" + PAYMENT_ID + "/refunds";

    private MockRestServiceServer server;
    private MercadoPagoAdapter adapter;

    // Liga un MockRestServiceServer al RestClient del adapter para inspeccionar el
    // body de las requests sin salir a la red real de MercadoPago.
    private void newAdapterWithToken(String token) {
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.adapter = new MercadoPagoAdapter(token, "https://notif.test", builder);
    }

    // --- US-17-01: reembolso total (sin amount) → body vacío ---

    @Test
    void refundPayment_withoutAmount_sendsEmptyBody() {
        newAdapterWithToken("test-token");
        server.expect(requestTo(REFUND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        adapter.refundPayment(PAYMENT_ID, null);

        server.verify();
    }

    // --- US-17-01: reembolso parcial (con amount) → body con el monto ---

    @Test
    void refundPayment_withAmount_sendsAmountInBody() {
        newAdapterWithToken("test-token");
        server.expect(requestTo(REFUND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"amount\":150.50}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        adapter.refundPayment(PAYMENT_ID, new BigDecimal("150.50"));

        server.verify();
    }

    // --- backward-compat: el overload de un solo argumento reembolsa el total ---

    @Test
    void refundPayment_singleArgOverload_sendsEmptyBody() {
        newAdapterWithToken("test-token");
        server.expect(requestTo(REFUND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        adapter.refundPayment(PAYMENT_ID);

        server.verify();
    }

    // --- sandbox no-op: sin accessToken no se llama a la API (aun con amount) ---

    @Test
    void refundPayment_blankToken_isNoOp() {
        newAdapterWithToken("");

        // Sin expectativas: si se emitiera cualquier request, MockRestServiceServer
        // fallaría con "unexpected request". verify() en verde ⇒ no hubo llamada.
        adapter.refundPayment(PAYMENT_ID, new BigDecimal("100.00"));

        server.verify();
    }

    // --- error de la API → BusinessException tipada ---

    @Test
    void refundPayment_apiError_throwsBusinessException() {
        newAdapterWithToken("test-token");
        server.expect(requestTo(REFUND_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.refundPayment(PAYMENT_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reembolso");

        server.verify();
    }
}
