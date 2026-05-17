package com.doubleCalendar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient calendarRestClient(RestClient.Builder builder, YandexCalendarConfig config) {
        // Используем обычный ExecutorService (cached thread pool)
        // Для Java 21+ можно заменить на Executors.newVirtualThreadPerTaskExecutor()
        ExecutorService executor = Executors.newCachedThreadPool();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()))
                .executor(executor)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(config.getReadTimeoutSeconds()));

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}