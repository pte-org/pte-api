package com.pte.chat.internal.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Model output is untrusted: only a full, well-typed {is_on_topic, markdown_content} object passes. */
class ChatOutputParserTest {

    /** Distinctive token that must never come back out through an exception message. */
    private static final String SECRET = "RAW-SECRET-TOKEN-9f3a21";

    private static final String VALID = "{\"is_on_topic\":true,\"markdown_content\":\"Use **present perfect**.\"}";

    @Test
    void cap_is_twenty_thousand_characters() {
        assertThat(ChatOutputParser.MAX_MARKDOWN_CHARS).isEqualTo(20_000);
    }

    @Test
    void parses_a_valid_on_topic_object() {
        ChatModelOutput output = ChatOutputParser.parse(VALID);

        assertThat(output.onTopic()).isTrue();
        assertThat(output.markdown()).isEqualTo("Use **present perfect**.");
    }

    @Test
    void parses_surrounding_whitespace_and_newlines() {
        ChatModelOutput output = ChatOutputParser.parse("  \n\t" + VALID + "\n \n");

        assertThat(output).isEqualTo(new ChatModelOutput(true, "Use **present perfect**."));
    }

    @Test
    void strips_a_json_fence() {
        ChatModelOutput output = ChatOutputParser.parse("```json\n" + VALID + "\n```");

        assertThat(output).isEqualTo(new ChatModelOutput(true, "Use **present perfect**."));
    }

    @Test
    void strips_a_bare_fence() {
        ChatModelOutput output = ChatOutputParser.parse("```\n" + VALID + "\n```");

        assertThat(output).isEqualTo(new ChatModelOutput(true, "Use **present perfect**."));
    }

    @Test
    void strips_a_fence_with_whitespace_around_it() {
        ChatModelOutput output = ChatOutputParser.parse("\n  ```json\r\n  " + VALID + "  \r\n```  \n\n");

        assertThat(output).isEqualTo(new ChatModelOutput(true, "Use **present perfect**."));
    }

    @Test
    void strips_a_fence_written_on_one_line() {
        ChatModelOutput output = ChatOutputParser.parse("```json " + VALID + " ```");

        assertThat(output).isEqualTo(new ChatModelOutput(true, "Use **present perfect**."));
    }

    @Test
    void keeps_code_fences_that_live_inside_the_markdown_value() {
        String raw = "{\"is_on_topic\":true,\"markdown_content\":\"Try:\\n```text\\nI have been\\n```\"}";

        ChatModelOutput output = ChatOutputParser.parse(raw);

        assertThat(output.markdown()).isEqualTo("Try:\n```text\nI have been\n```");
    }

    @Test
    void keeps_unicode_and_escaped_characters() {
        String raw = "{\"is_on_topic\":true,\"markdown_content\":\"Dịch: \\\"xin chào\\\" - \\u00e9\"}";

        assertThat(ChatOutputParser.parse(raw).markdown()).isEqualTo("Dịch: \"xin chào\" - é");
    }

    @Test
    void accepts_markdown_exactly_at_the_cap() {
        String markdown = "a".repeat(ChatOutputParser.MAX_MARKDOWN_CHARS);
        String raw = "{\"is_on_topic\":true,\"markdown_content\":\"" + markdown + "\"}";

        assertThat(ChatOutputParser.parse(raw).markdown()).hasSize(ChatOutputParser.MAX_MARKDOWN_CHARS);
    }

    @Test
    void rejects_markdown_one_over_the_cap() {
        String markdown = "a".repeat(ChatOutputParser.MAX_MARKDOWN_CHARS + 1);
        String raw = "{\"is_on_topic\":true,\"markdown_content\":\"" + markdown + "\"}";

        assertThatThrownBy(() -> ChatOutputParser.parse(raw))
                .isInstanceOfSatisfying(ChatAiException.class,
                        error -> assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE));
    }

    @Test
    void off_topic_without_markdown_returns_empty_markdown() {
        ChatModelOutput output = ChatOutputParser.parse("{\"is_on_topic\":false}");

        assertThat(output.onTopic()).isFalse();
        assertThat(output.markdown()).isEmpty();
    }

    @Test
    void off_topic_ignores_whatever_markdown_the_model_wrote() {
        ChatModelOutput output = ChatOutputParser.parse(
                "{\"is_on_topic\":false,\"markdown_content\":\"Sure, here is a poem about cats\"}");

        assertThat(output.onTopic()).isFalse();
        assertThat(output.markdown()).isEmpty();
    }

    @Test
    void off_topic_ignores_null_blank_and_oversized_markdown() {
        assertThat(ChatOutputParser.parse("{\"is_on_topic\":false,\"markdown_content\":null}").markdown()).isEmpty();
        assertThat(ChatOutputParser.parse("{\"is_on_topic\":false,\"markdown_content\":\"  \"}").markdown()).isEmpty();
        String huge = "{\"is_on_topic\":false,\"markdown_content\":\""
                + "b".repeat(ChatOutputParser.MAX_MARKDOWN_CHARS + 50) + "\"}";
        assertThat(ChatOutputParser.parse(huge)).isEqualTo(new ChatModelOutput(false, ""));
    }

    @Test
    void off_topic_inside_a_fence_is_parsed_too() {
        ChatModelOutput output = ChatOutputParser.parse("```json\n{\"is_on_topic\":false,\"markdown_content\":\"\"}\n```");

        assertThat(output).isEqualTo(new ChatModelOutput(false, ""));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidInputs")
    void rejects_everything_else_as_invalid_response(String name, String raw) {
        assertThatThrownBy(() -> ChatOutputParser.parse(raw))
                .isInstanceOfSatisfying(ChatAiException.class, error -> {
                    assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
                    assertThat(error.usage()).isNotNull();
                    assertThat(error.getMessage()).isNotBlank();
                    assertThat(error.getMessage()).doesNotContain(SECRET);
                    assertThat(String.valueOf(error.getCause())).doesNotContain(SECRET);
                });
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("null input", null),
                Arguments.of("empty input", ""),
                Arguments.of("blank input", "  \n\t "),
                Arguments.of("plain prose", "Sure! " + SECRET),
                Arguments.of("json array", "[{\"is_on_topic\":true,\"markdown_content\":\"" + SECRET + "\"}]"),
                Arguments.of("json string scalar", "\"" + SECRET + "\""),
                Arguments.of("json number scalar", "42"),
                Arguments.of("json boolean scalar", "true"),
                Arguments.of("json null", "null"),
                Arguments.of("empty object", "{}"),
                Arguments.of("missing is_on_topic",
                        "{\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("is_on_topic null",
                        "{\"is_on_topic\":null,\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("is_on_topic string true",
                        "{\"is_on_topic\":\"true\",\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("is_on_topic string false",
                        "{\"is_on_topic\":\"false\",\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("is_on_topic number one",
                        "{\"is_on_topic\":1,\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("is_on_topic number zero",
                        "{\"is_on_topic\":0,\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("is_on_topic array",
                        "{\"is_on_topic\":[true],\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("on topic with missing markdown", "{\"is_on_topic\":true}"),
                Arguments.of("on topic with null markdown", "{\"is_on_topic\":true,\"markdown_content\":null}"),
                Arguments.of("on topic with empty markdown", "{\"is_on_topic\":true,\"markdown_content\":\"\"}"),
                Arguments.of("on topic with blank markdown",
                        "{\"is_on_topic\":true,\"markdown_content\":\"  \\n\\t \"}"),
                Arguments.of("on topic with numeric markdown",
                        "{\"is_on_topic\":true,\"markdown_content\":" + 12345 + "}"),
                Arguments.of("on topic with object markdown",
                        "{\"is_on_topic\":true,\"markdown_content\":{\"text\":\"" + SECRET + "\"}}"),
                Arguments.of("on topic with array markdown",
                        "{\"is_on_topic\":true,\"markdown_content\":[\"" + SECRET + "\"]}"),
                Arguments.of("on topic with boolean markdown",
                        "{\"is_on_topic\":true,\"markdown_content\":true}"),
                Arguments.of("truncated mid string",
                        "{\"is_on_topic\":true,\"markdown_content\":\"" + SECRET),
                Arguments.of("truncated mid object", "{\"is_on_topic\":true,"),
                Arguments.of("truncated right after the brace", "{"),
                Arguments.of("unterminated fence",
                        "```json\n{\"is_on_topic\":true,\"markdown_content\":\"" + SECRET),
                Arguments.of("fence around prose", "```json\n" + SECRET + "\n```"),
                Arguments.of("trailing garbage after the object",
                        "{\"is_on_topic\":true,\"markdown_content\":\"ok " + SECRET + "\"} trailing"),
                Arguments.of("two objects back to back",
                        "{\"is_on_topic\":true,\"markdown_content\":\"" + SECRET + "\"}{\"is_on_topic\":false}"),
                Arguments.of("prose before the object",
                        "Here you go: {\"is_on_topic\":true,\"markdown_content\":\"" + SECRET + "\"}"),
                Arguments.of("trailing garbage after a fence",
                        "```json\n{\"is_on_topic\":true,\"markdown_content\":\"" + SECRET + "\"}\n``` thanks"));
    }

    @Test
    void error_for_oversized_markdown_does_not_echo_the_content() {
        String raw = "{\"is_on_topic\":true,\"markdown_content\":\"" + SECRET
                + "x".repeat(ChatOutputParser.MAX_MARKDOWN_CHARS) + "\"}";

        assertThatThrownBy(() -> ChatOutputParser.parse(raw))
                .isInstanceOfSatisfying(ChatAiException.class, error -> {
                    assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
                    assertThat(error.getMessage()).doesNotContain(SECRET);
                });
    }

    @Test
    void model_output_is_a_value_record() {
        assertThat(new ChatModelOutput(true, "a")).isEqualTo(new ChatModelOutput(true, "a"));
        assertThat(new ChatModelOutput(true, "a")).isNotEqualTo(new ChatModelOutput(false, "a"));
    }
}
