package com.pte.billing.internal.vendor.payos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PayOsClientTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private PayOsClient client;

    @BeforeEach
    void setUp() {
        PayOsProperties properties = new PayOsProperties();
        properties.setClientId("client-id");
        properties.setApiKey("api-key");
        properties.setChecksumKey("1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675");
        properties.setBaseUrl("https://api-merchant.payos.vn");
        properties.setReturnUrl("https://tenant.example/payment/success");
        properties.setCancelUrl("https://tenant.example/payment/cancel");
        client = new PayOsClient(org.springframework.web.client.RestClient.create(), properties, jsonMapper);
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
}
