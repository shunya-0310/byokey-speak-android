package com.byokeylab.speak

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmErrorClassifierTest {

    @Test
    fun unauthorizedIsInvalidApiKey() {
        assertEquals(LlmErrorKind.InvalidApiKey, LlmErrorClassifier.classifyHttp(401, ""))
        assertEquals(LlmErrorKind.InvalidApiKey, LlmErrorClassifier.classifyHttp(403, ""))
    }

    @Test
    fun notFoundIsInvalidModel() {
        assertEquals(LlmErrorKind.InvalidModel, LlmErrorClassifier.classifyHttp(404, "model not found"))
    }

    @Test
    fun badRequestWithModelErrorIsInvalidModel() {
        assertEquals(
            LlmErrorKind.InvalidModel,
            LlmErrorClassifier.classifyHttp(400, """{"error":{"message":"The model `gpt-x` does not exist"}}""")
        )
        assertEquals(
            LlmErrorKind.InvalidModel,
            LlmErrorClassifier.classifyHttp(400, """{"error":{"message":"Invalid model name"}}""")
        )
    }

    @Test
    fun badRequestWithApiKeyErrorIsInvalidApiKey() {
        assertEquals(
            LlmErrorKind.InvalidApiKey,
            LlmErrorClassifier.classifyHttp(400, """{"error":{"message":"API key not valid"}}""")
        )
    }

    @Test
    fun quotaExhaustionIsBilling() {
        assertEquals(
            LlmErrorKind.Billing,
            LlmErrorClassifier.classifyHttp(429, """{"error":{"type":"insufficient_quota"}}""")
        )
        assertEquals(
            LlmErrorKind.Billing,
            LlmErrorClassifier.classifyHttp(429, """{"error":{"message":"Your credit balance is too low"}}""")
        )
        assertEquals(LlmErrorKind.Billing, LlmErrorClassifier.classifyHttp(402, ""))
    }

    @Test
    fun plainTooManyRequestsIsRateLimit() {
        assertEquals(LlmErrorKind.RateLimit, LlmErrorClassifier.classifyHttp(429, "Too many requests"))
    }

    @Test
    fun serverErrorIsProvider() {
        assertEquals(LlmErrorKind.Provider, LlmErrorClassifier.classifyHttp(500, ""))
        assertEquals(LlmErrorKind.Provider, LlmErrorClassifier.classifyHttp(529, "overloaded"))
    }

    @Test
    fun unclassifiedIsUnknown() {
        assertEquals(LlmErrorKind.Unknown, LlmErrorClassifier.classifyHttp(418, "teapot"))
    }
}
