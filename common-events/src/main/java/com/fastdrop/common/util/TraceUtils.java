package com.fastdrop.common.util;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceUtils {

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACE_ID_KEY = "trace_id";
    public static final String SPAN_ID_KEY = "span_id";

    private TraceUtils() {}

    /**
     * Extracts or generates a valid W3C traceparent header: 00-{traceId}-{spanId}-01
     */
    public static String getOrCreateTraceParent() {
        String currentTraceId = MDC.get(TRACE_ID_KEY);
        String currentSpanId = MDC.get(SPAN_ID_KEY);

        if (currentTraceId == null || currentTraceId.isBlank()) {
            currentTraceId = UUID.randomUUID().toString().replace("-", "");
            currentSpanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(TRACE_ID_KEY, currentTraceId);
            MDC.put(SPAN_ID_KEY, currentSpanId);
        } else if (currentSpanId == null || currentSpanId.isBlank()) {
            currentSpanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(SPAN_ID_KEY, currentSpanId);
        }

        return String.format("00-%s-%s-01", currentTraceId, currentSpanId);
    }

    /**
     * Sets MDC trace context from a W3C traceparent string
     */
    public static void setTraceContext(String traceParent) {
        if (traceParent != null && traceParent.startsWith("00-")) {
            String[] parts = traceParent.split("-");
            if (parts.length >= 3) {
                MDC.put(TRACE_ID_KEY, parts[1]);
                MDC.put(SPAN_ID_KEY, parts[2]);
                return;
            }
        }
        // Fallback
        getOrCreateTraceParent();
    }

    public static void clearTraceContext() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
    }
}
