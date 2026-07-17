package com.laroka.backend.notification.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailAdapterTest {

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final String TO = "dueno@laroka.app";
    private static final String SUBJECT = "Reporte de bug";
    private static final String BODY = "Algo se rompió en el checkout";

    private MockRestServiceServer server;
    private ResendEmailAdapter adapter;

    // Liga un MockRestServiceServer al RestClient del adapter para inspeccionar el
    // body de la request sin salir a la red real de Resend.
    private void newAdapter(String apiKey, String from) {
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.adapter = new ResendEmailAdapter(apiKey, from, builder);
    }

    // --- envío exitoso: arma el body correcto y no lanza ---

    @Test
    void send_success_postsExpectedPayload() {
        newAdapter("test-key", "LaRoka <no-reply@laroka.app>");
        server.expect(requestTo(RESEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "from": "LaRoka <no-reply@laroka.app>",
                          "to": ["dueno@laroka.app"],
                          "subject": "Reporte de bug",
                          "text": "Algo se rompió en el checkout"
                        }
                        """))
                .andRespond(withSuccess("{\"id\":\"email-123\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> adapter.send(TO, SUBJECT, BODY)).doesNotThrowAnyException();

        server.verify();
    }

    // --- fallo del proveedor: se loguea y NO se propaga al caller ---

    @Test
    void send_providerError_doesNotThrow() {
        newAdapter("test-key", "no-reply@laroka.app");
        server.expect(requestTo(RESEND_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> adapter.send(TO, SUBJECT, BODY)).doesNotThrowAnyException();

        server.verify();
    }

    // --- sin api-key: no-op, no se emite ninguna request ---

    @Test
    void send_blankApiKey_isNoOp() {
        newAdapter("", "no-reply@laroka.app");

        // Sin expectativas: si se emitiera cualquier request, MockRestServiceServer
        // fallaría con "unexpected request". verify() en verde ⇒ no hubo llamada.
        assertThatCode(() -> adapter.send(TO, SUBJECT, BODY)).doesNotThrowAnyException();

        server.verify();
    }
}
