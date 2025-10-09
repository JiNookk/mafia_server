           package com.jingwook.mafia_server.config;

           import org.slf4j.Logger;
           import org.slf4j.LoggerFactory;
           import org.springframework.context.annotation.Bean;
           import org.springframework.context.annotation.Configuration;
           import org.springframework.core.io.buffer.DataBuffer;
           import org.springframework.core.io.buffer.DataBufferUtils;
           import org.springframework.http.server.reactive.ServerHttpRequest;
           import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
           import org.springframework.http.server.reactive.ServerHttpResponse;
           import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
           import org.springframework.web.server.WebFilter;
           import reactor.core.publisher.Flux;
           import reactor.core.publisher.Mono;

           import java.nio.charset.StandardCharsets;

@Configuration
public class LoggingConfig {

    private static final Logger logger = LoggerFactory.getLogger("API_LOGGER");

    @Bean
    public WebFilter loggingFilter() {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();
            ServerHttpRequest request = exchange.getRequest();

            // 요청 로깅
            logger.info("=== REQUEST START ===");
            logger.info("Method: {}", request.getMethod());
            logger.info("Path: {}", request.getPath().value());
            logger.info("Headers: {}", request.getHeaders());
            logger.info("Query Params: {}", request.getQueryParams());

            ServerHttpRequest requestToUse = request;
            ServerHttpResponse responseToUse = exchange.getResponse();

            // Request Body 로깅이 필요한 경우
            if (hasBody(request)) {
                requestToUse = new LoggingServerHttpRequestDecorator(request);
            }

            // Response 로깅
            responseToUse = new LoggingServerHttpResponseDecorator(exchange.getResponse(), startTime);

            return chain.filter(exchange.mutate()
                    .request(requestToUse)
                    .response(responseToUse)
                    .build());
        };
    }

    private boolean hasBody(ServerHttpRequest request) {
        String method = request.getMethod().name();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private static class LoggingServerHttpRequestDecorator extends ServerHttpRequestDecorator {

        public LoggingServerHttpRequestDecorator(ServerHttpRequest delegate) {
            super(delegate);
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return super.getBody().doOnNext(dataBuffer -> {
                String body = dataBuffer.toString(StandardCharsets.UTF_8);
                logger.info("Request Body: {}", body);
            });
        }
    }

    private static class LoggingServerHttpResponseDecorator extends ServerHttpResponseDecorator {
        private final long startTime;

        public LoggingServerHttpResponseDecorator(ServerHttpResponse delegate, long startTime) {
            super(delegate);
            this.startTime = startTime;
        }

        @Override
        public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
            return super.writeWith(
                Flux.from(body).map(dataBuffer -> {
                    byte[] content = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(content);


                    String responseBody = new String(content, StandardCharsets.UTF_8);
                    long duration = System.currentTimeMillis() - startTime;

                    logger.info("=== RESPONSE ===");
                    logger.info("Status: {}", getStatusCode());
                    logger.info("Response Body: {}", responseBody);
                    logger.info("Duration: {}ms", duration);
                    logger.info("=== REQUEST END ===");

                    return getDelegate().bufferFactory().wrap(content);
                })
            );
        }
    }
}