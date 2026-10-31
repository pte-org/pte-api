package com.pte.chat.internal.service.privacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Emails and phone numbers are replaced by a fixed placeholder before a question leaves the system. */
class ChatContactDetailsTest {

    @Test
    void anEmailBecomesAPlaceholder() {
        assertThat(ChatContactDetails.mask("Fix this, mail me at nguyen.van.an@gmail.com thanks"))
                .isEqualTo("Fix this, mail me at [email] thanks");
    }

    @Test
    void everyEmailInTheTextIsMasked() {
        assertThat(ChatContactDetails.mask("a@b.co and Student+pte@School-Name.edu.vn"))
                .isEqualTo("[email] and [email]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "+84 912 345 678", "(028) 3822-1234", "0912.345.678"})
    void aPhoneNumberBecomesAPlaceholder(String phone) {
        assertThat(ChatContactDetails.mask("Call me on " + phone + " please")).isEqualTo("Call me on [phone] please");
    }

    @ParameterizedTest
    @ValueSource(strings = {"84912345678", "84 912 345 678", "0 9 1 2 3 4 5 6 7 8"})
    void aVietnameseNumberWithTheCountryCodeIsMaskedButSpreadOutDigitsAreNot(String number) {
        boolean masked = ChatContactDetails.mask("Call " + number).equals("Call [phone]");

        assertThat(masked).isEqualTo(!number.startsWith("0 9"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "In 2023-2024 the rate rose",
            "Born 12-05-2004 in Hanoi",
            "Years 2020 2021 2022 2023",
            "Meeting on 01-02-2024 10:00 sharp",
            "Between 1990 - 2000 and 2010 - 2020",
            "Page 12-13, figures 1.2.3.4.5.6.7.8.9",
            "ISBN 978-3-16-148410-0",
            "Score: 1,234,567,890",
    })
    void yearsDatesAndOtherNumbersAreNotMistakenForPhoneNumbers(String text) {
        assertThat(ChatContactDetails.mask(text)).isEqualTo(text);
        assertThat(ChatContactDetails.contains(text)).isFalse();
    }

    @Test
    void aNumberOfNineDigitsStartingWithZeroIsMaskedButEightIsNot() {
        assertThat(ChatContactDetails.mask("call 091234567 now")).isEqualTo("call [phone] now");
        assertThat(ChatContactDetails.mask("call 09123456 now")).isEqualTo("call 09123456 now");
    }

    @Test
    void textWithoutContactDetailsIsReturnedUnchanged() {
        String text = "I scored 79 out of 90 in 2025, 3 or 4 times. What does the @ symbol mean?";

        assertThat(ChatContactDetails.mask(text)).isEqualTo(text);
        assertThat(ChatContactDetails.contains(text)).isFalse();
    }

    @Test
    void containsReportsBothKinds() {
        assertThat(ChatContactDetails.contains("write to a@b.co")).isTrue();
        assertThat(ChatContactDetails.contains("call 0912345678")).isTrue();
    }

    @Test
    void maskingAnAlreadyMaskedTextChangesNothing() {
        String once = ChatContactDetails.mask("a@b.co 0912345678");

        assertThat(ChatContactDetails.mask(once)).isEqualTo(once);
        assertThat(ChatContactDetails.contains(once)).isFalse();
    }

    @Test
    void anEmptyStringStaysEmpty() {
        assertThat(ChatContactDetails.mask("")).isEmpty();
    }
}
