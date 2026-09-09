package com.hz.logistics.parentservice.autoconfigure.logging

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class LoggingRedactionTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun redactsEveryCorpusCanaryBeforeAConsumerCanInspectTheEvent() {
        withSanitizer { sanitizer ->
            val corpus = loadCorpus()
            val sanitized = sanitizer.sanitize(eventFrom(corpus))
            val serialized = flattenEvent(sanitized)

            sensitiveCanaries(corpus).forEach { canary ->
                assertThat(serialized).doesNotContain(canary)
            }
            assertThat(serialized).contains("[MASKED]")
        }
    }

    @Test
    fun handlesMessagesArgumentsMdcFieldsHeadersQueriesNestedValuesAndThrowableGraphs() {
        withSanitizer { sanitizer ->
            val corpus = loadCorpus()
            val sanitized = sanitizer.sanitize(eventFrom(corpus))

            assertThat(sanitized.message).doesNotContain(
                corpus.path("baseline").path("authorization").asText(),
                corpus.path("baseline").path("jwt").asText(),
            )
            assertThat(sanitized.arguments.joinToString()).doesNotContain(
                corpus.path("baseline").path("password").asText(),
                corpus.path("baseline").path("accessToken").asText(),
            )
            assertThat(flatten(sanitized.fields)).doesNotContain(*sensitiveCanaries(corpus).toTypedArray())
            assertThat(flattenThrowable(sanitized.throwable)).doesNotContain(
                corpus.path("baseline").path("secret").asText(),
                corpus.path("baseline").path("apiKey").asText(),
                corpus.path("configured").path("customerEmail").asText(),
                corpus.path("configured").path("recipientPhone").asText(),
            )
        }
    }

    @Test
    fun preservesNearMatchesThatAreNotSensitiveValuesOrConfiguredSelectors() {
        withSanitizer { sanitizer ->
            val corpus = loadCorpus()
            val sanitized = sanitizer.sanitize(eventFrom(corpus))

            corpus.path("nearMatches").forEach { nearMatch ->
                assertThat(flattenEvent(sanitized)).contains(nearMatch.asText())
            }
        }
    }

    private fun withSanitizer(assertions: (PlatformLogSanitizer) -> Unit) {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LoggingRedactionTestApplication::class.java))
            .withPropertyValues(
                "logistics.parent-service.logging.redaction-mask=[MASKED]",
                "logistics.parent-service.logging.additional-sensitive-fields[0]=customerEmail",
                "logistics.parent-service.logging.additional-sensitive-paths[0]=shipment.recipient.phone",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertions(context.getBean(PlatformLogSanitizer::class.java))
            }
    }

    private fun eventFrom(corpus: JsonNode): PlatformLogEvent {
        val baseline = corpus.path("baseline")
        val configured = corpus.path("configured")
        val root = IllegalStateException("root " + baseline.path("secret").asText())
        val cause = IllegalArgumentException(
            "cause " + baseline.path("apiKey").asText() + " " + configured.path("customerEmail").asText(),
        )
        cause.addSuppressed(IllegalStateException("suppressed " + configured.path("recipientPhone").asText()))
        root.initCause(cause)

        return PlatformLogEvent(
            message = "Authorization: " + baseline.path("authorization").asText() +
                " jwt " + baseline.path("jwt").asText() +
                " " + corpus.path("nearMatches").first().asText(),
            arguments = listOf(
                "password=" + baseline.path("password").asText(),
                "access_token=" + baseline.path("accessToken").asText(),
            ),
            fields = mapOf(
                "Authorization" to baseline.path("authorization").asText(),
                "headers" to mapOf("X-Api-Key" to baseline.path("apiKey").asText()),
                "query" to mapOf("refresh_token" to baseline.path("refreshToken").asText()),
                "customerEmail" to configured.path("customerEmail").asText(),
                "shipment" to mapOf(
                    "recipient" to mapOf("phone" to configured.path("recipientPhone").asText()),
                ),
                "items" to listOf(
                    mapOf("password" to baseline.path("password").asText()),
                    mapOf("note" to corpus.path("nearMatches").last().asText()),
                ),
            ),
            throwable = root,
        )
    }

    private fun loadCorpus(): JsonNode =
        requireNotNull(javaClass.getResourceAsStream("/redaction-corpus/redaction-cases.json"))
            .use(objectMapper::readTree)

    private fun sensitiveCanaries(corpus: JsonNode): List<String> =
        corpus.path("baseline").fields().asSequence().map { it.value.asText() }.toList() +
            corpus.path("configured").fields().asSequence().map { it.value.asText() }.toList()

    private fun flattenEvent(event: PlatformLogEvent): String =
        listOf(event.message, event.arguments.joinToString(), flatten(event.fields), flattenThrowable(event.throwable))
            .joinToString(" ")

    private fun flatten(value: Any?): String = when (value) {
        is Map<*, *> -> value.entries.joinToString(" ") { flatten(it.key) + " " + flatten(it.value) }
        is Iterable<*> -> value.joinToString(" ") { flatten(it) }
        else -> value.toString()
    }

    private fun flattenThrowable(throwable: Throwable?): String {
        if (throwable == null) {
            return ""
        }
        return listOf(
            throwable::class.qualifiedName,
            throwable.message,
            flattenThrowable(throwable.cause),
            throwable.suppressedExceptions.joinToString(" ") { flattenThrowable(it) },
        ).joinToString(" ")
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
class LoggingRedactionTestApplication
