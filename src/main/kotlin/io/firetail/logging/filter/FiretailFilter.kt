package io.firetail.logging.filter

import io.firetail.logging.config.Constants.Companion.CORRELATION_ID
import io.firetail.logging.config.Constants.Companion.OP_NAME
import io.firetail.logging.config.Constants.Companion.REQUEST_ID
import io.firetail.logging.config.FiretailConfig
import io.firetail.logging.util.LogContext
import io.firetail.logging.util.StringUtils
import io.firetail.logging.wrapper.SpringRequestWrapper
import io.firetail.logging.wrapper.SpringResponseWrapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.io.IOException
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class FiretailFilter(
    private val logContext: LogContext,
    private val firetailConfig: FiretailConfig,
) {
    private val stringUtils = StringUtils() // UTF-8
    private val firetailLogger = FiretailLogger(stringUtils, firetailConfig.logHeaders)

    @Bean
    fun firetailRequestFilter(): OncePerRequestFilter {
        return object : OncePerRequestFilter() {
            @Throws(ServletException::class, IOException::class)
            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                chain: FilterChain,
            ) {
                if (firetailConfig.ignorePatterns != null && request.requestURI.matches(firetailConfig.ignorePatterns!!.toRegex())) {
                    chain.doFilter(request, response)
                } else {
                    logContext.generateAndSetMDC(request)
                    try {
                        getHandlerMethod(request)
                    } catch (e: Exception) {
                        LOGGER.trace("Cannot get handler method")
                    }
                    val startTime = System.currentTimeMillis()
                    val wrappedRequest = SpringRequestWrapper(request)
                    firetailLogger.logRequest(wrappedRequest)
                    val wrappedResponse = SpringResponseWrapper(response)
                    try {
                        with(wrappedResponse) {
                            setHeader(REQUEST_ID, MDC.get(REQUEST_ID))
                            setHeader(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        }
                        chain.doFilter(wrappedRequest, wrappedResponse)
                        firetailLogger.logResponse(startTime, wrappedResponse)
                    } catch (e: Exception) {
                        firetailLogger.logResponse(startTime, wrappedResponse, 500)
                        throw e
                    }
                }
            }
        }
    }

    private fun getHandlerMethod(request: HttpServletRequest) {
        val mappings = firetailConfig.context.getBean("requestMappingHandlerMapping")
            as RequestMappingHandlerMapping
        val nullableHandler = mappings.getHandler(request)
        if (Objects.nonNull(nullableHandler)) {
            val handler = nullableHandler?.handler as HandlerMethod
            MDC.put(OP_NAME, handler.beanType.simpleName + "." + handler.method.name)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FiretailLogger::class.java)
    }
}
