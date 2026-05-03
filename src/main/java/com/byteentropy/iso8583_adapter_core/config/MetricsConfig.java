package com.byteentropy.iso8583_adapter_core.config;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

public class MetricsConfig {
    public static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public static final Counter txnCounter = Counter.builder("iso8583.transactions.total")
            .description("Total transactions processed")
            .register(registry);

    public static final Counter errorCounter = Counter.builder("iso8583.transactions.errors")
            .description("Total failed transactions")
            .register(registry);

    public static final Timer txnTimer = Timer.builder("iso8583.transactions.latency")
            .description("Processing latency")
            .register(registry);

    public static final Counter validationErrorCounter = Counter.builder("iso8583.transactions.validation.errors")
        .description("Total transactions that failed business/sub-field validation")
        .register(registry);        
}