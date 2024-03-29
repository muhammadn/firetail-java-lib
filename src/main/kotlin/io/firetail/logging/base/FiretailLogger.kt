package io.firetail.logging.base

import io.firetail.logging.servlet.SpringRequestWrapper
import io.firetail.logging.servlet.SpringResponseWrapper
import io.firetail.logging.util.StringUtils
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FiretailLogger(
    private val stringUtils: StringUtils = StringUtils(),
    private val firetailConfig: FiretailConfig,
) {
    fun logRequest(wrappedRequest: SpringRequestWrapper) =
        if (firetailConfig.logHeaders) {
            logWithHeaders(wrappedRequest)
        } else {
            logNoHeaders(wrappedRequest)
        }

    private fun logNoHeaders(wrappedRequest: SpringRequestWrapper) {
        LOGGER.info(
            "Request: method={}, uri={}, payload={}, audit={}",
            wrappedRequest.method,
            wrappedRequest.requestURI,
            stringUtils.toString(wrappedRequest.inputStream.readAllBytes(), wrappedRequest.characterEncoding),
            StructuredArguments.value(Constants.AUDIT, true),
        )
    }

    private fun logWithHeaders(wrappedRequest: SpringRequestWrapper) {
        LOGGER.info(
            "Request: method={}, uri={}, payload={}, headers={}, audit={}",
            wrappedRequest.method,
            wrappedRequest.requestURI,
            stringUtils.toString(wrappedRequest.inputStream.readAllBytes(), wrappedRequest.characterEncoding),
            wrappedRequest.allHeaders,
            StructuredArguments.value(Constants.AUDIT, true),
        )
    }

    fun logResponse(
        startTime: Long,
        wrappedResponse: SpringResponseWrapper,
        status: Int = wrappedResponse.status,
    ) {
        val duration = System.currentTimeMillis() - startTime
        wrappedResponse.characterEncoding = stringUtils.charSet()
        if (firetailConfig.logHeaders) {
            logWithHeaders(duration, status, wrappedResponse)
        } else {
            logNoHeaders(duration, status, wrappedResponse)
        }
    }

    private fun logNoHeaders(
        duration: Long,
        status: Int,
        wrappedResponse: SpringResponseWrapper,
    ) {
        LOGGER.info(
            "Response({} ms): status={}, payload={}, audit={}",
            StructuredArguments.value(Constants.RESPONSE_TIME, duration),
            StructuredArguments.value(Constants.RESPONSE_STATUS, status),
            stringUtils.toString(wrappedResponse.contentAsByteArray),
            StructuredArguments.value(Constants.AUDIT, true),
        )
    }

    private fun logWithHeaders(
        duration: Long,
        status: Int,
        wrappedResponse: SpringResponseWrapper,
    ) {
        LOGGER.info(
            "Response({} ms): status={}, payload={}, headers={}, audit={}",
            StructuredArguments.value(Constants.RESPONSE_TIME, duration),
            StructuredArguments.value(Constants.RESPONSE_STATUS, status),
            stringUtils.toString(wrappedResponse.contentAsByteArray),
            wrappedResponse.allHeaders,
            StructuredArguments.value(Constants.AUDIT, true),
        )
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }
}
