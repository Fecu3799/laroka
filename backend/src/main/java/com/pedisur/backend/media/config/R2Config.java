package com.pedisur.backend.media.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Integración con Cloudflare R2 (US-15-01).
 *
 * R2 es compatible con la API de S3, así que se reutiliza el SDK de AWS S3 v2
 * apuntando al endpoint de R2 vía {@code endpointOverride}. Las credenciales y
 * el endpoint llegan por variables de entorno (R2_ACCESS_KEY, R2_SECRET_KEY,
 * R2_ENDPOINT, R2_BUCKET_NAME) — nunca hardcodeadas.
 *
 * Los defaults vacíos evitan que el contexto falle al arrancar cuando R2 no está
 * configurado (perfil test / entornos sin storage): en ese caso el cliente se
 * construye con credenciales anónimas y sin endpoint override. El bean se crea
 * igual, pero solo el endpoint de upload lo invoca realmente.
 */
@Getter
@Configuration
public class R2Config {

    @Value("${r2.access-key:}")
    private String accessKey;

    @Value("${r2.secret-key:}")
    private String secretKey;

    @Value("${r2.bucket-name:}")
    private String bucketName;

    @Value("${r2.endpoint:}")
    private String endpoint;

    /**
     * Base de la URL pública del bucket (ej: https://pub-xxxx.r2.dev). Es
     * distinta del endpoint S3 de escritura. Se usa para construir la URL que el
     * endpoint de upload retorna.
     */
    @Value("${r2.public-url:}")
    private String publicUrl;

    /**
     * Cliente S3 v2 apuntando a R2. Singleton, thread-safe.
     *
     * Si las credenciales o el endpoint no están configurados se construye un
     * cliente "inerte" (credenciales anónimas, sin endpoint override) para no
     * romper el arranque del contexto; cualquier llamada real fallará de forma
     * controlada y será mapeada a 502 por el adapter.
     */
    @Bean
    public S3Client r2S3Client() {
        boolean credentialsConfigured = !accessKey.isBlank() && !secretKey.isBlank();
        AwsCredentialsProvider credentialsProvider = credentialsConfigured
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : AnonymousCredentialsProvider.create();

        S3ClientBuilder builder = S3Client.builder()
                // R2 no usa regiones reales; "auto" es el valor convencional.
                .region(Region.of("auto"))
                .credentialsProvider(credentialsProvider)
                // Desde el SDK 2.30 el default (WHEN_SUPPORTED) adjunta un checksum CRC32
                // automático vía aws-chunked / STREAMING-UNSIGNED-PAYLOAD-TRAILER en cada
                // putObject. R2 no lo soporta igual que S3 real y lo rechaza con error de
                // firma. WHEN_REQUIRED vuelve al comportamiento previo (solo adjunta checksum
                // cuando la operación lo exige), compatible con R2.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                // R2 trabaja con acceso path-style (bucket en el path, no en el host).
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
