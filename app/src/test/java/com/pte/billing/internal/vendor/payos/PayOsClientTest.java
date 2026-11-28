package com.pte.billing.internal.vendor.payos;

import com.pte.billing.domain.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayOsClientTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private PayOsClient client;
    private PayOsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PayOsProperties();
        properties.setClientId("client-id");
        properties.setApiKey("api-key");
        properties.setChecksumKey("1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675");
        properties.setBaseUrl("https://api-merchant.payos.vn");
        properties.setReturnUrl("https://tenant.example/payment/success");
        properties.setCancelUrl("https://tenant.example/payment/cancel");
        client = new PayOsClient(org.springframework.web.client.RestClient.create(), properties, jsonMapper, com.pte.TestClocks.fixed());
    }

    @Test
    void verifyWebhookSignature_matchesPayOsDocumentedVector() throws Exception {
        JsonNode payload = jsonMapper.readTree("""
                {
                  "code": "00",
                  "desc": "success",
                  "success": true,
                  "data": {
                    "orderCode": 123,
                    "amount": 3000,
                    "description": "VQRIO123",
                    "accountNumber": "12345678",
                    "reference": "TF230204212323",
                    "transactionDateTime": "2023-02-04 18:25:00",
                    "currency": "VND",
                    "paymentLinkId": "124c33293c43417ab7879e14c8d9eb18",
                    "code": "00",
                    "desc": "Thành công",
                    "counterAccountBankId": "",
                    "counterAccountBankName": "",
                    "counterAccountName": "",
                    "counterAccountNumber": "",
                    "virtualAccountName": "",
                    "virtualAccountNumber": ""
                  },
                  "signature": "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03"
                }
                """);

        assertThat(client.verifyWebhookSignature(payload)).isTrue();
        assertThat(client.verifyWebhookSignature(payload, "forged")).isFalse();
    }

    @Test
    void createPaymentLink_signsTheFieldsInAlphabeticalOrder() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-merchant.payos.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicReference<String> sent = new AtomicReference<>();
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> sent.set(request.getBody().toString()))
                .andRespond(withSuccess("{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"orderCode\":1000000007,"
                        + "\"checkoutUrl\":\"https://pay.payos.vn/web/x\"}}", MediaType.APPLICATION_JSON));
        PayOsClient signing = new PayOsClient(builder.build(), properties, jsonMapper, com.pte.TestClocks.fixed());
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1000000007L, new BigDecimal("10000.00"), "VND");

        assertThat(signing.createPaymentLink(order)).isEqualTo("https://pay.payos.vn/web/x");

        JsonNode body = jsonMapper.readTree(sent.get());
        String data = "amount=10000&cancelUrl=https://tenant.example/payment/cancel&description="
                + body.path("description").asText() + "&orderCode=1000000007&returnUrl=https://tenant.example/payment/success";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertThat(body.path("signature").asText())
                .isEqualTo(HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))));
        server.verify();
    }
}
