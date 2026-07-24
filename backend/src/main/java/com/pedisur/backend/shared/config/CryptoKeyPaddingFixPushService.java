package com.pedisur.backend.shared.config;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.jose4j.lang.JoseException;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

/**
 * Workaround para el bug conocido de nl.martijndwars:web-push 5.1.2
 * (issue #212): la librería codifica las claves del header {@code Crypto-Key}
 * con {@code Base64.getUrlEncoder()} <em>con</em> padding ({@code =}), mientras
 * que el header {@code Authorization} sí usa {@code withoutPadding()}.
 *
 * Chrome (FCM) rechaza cualquier padding en {@code Crypto-Key} y responde 403;
 * Firefox lo tolera. El bug está en {@code AbstractPushService.prepareRequest},
 * que es {@code protected final} y no se puede sobreescribir. Pero el pipeline
 * de envío ({@code send → sendAsync → preparePost}) pasa por {@code preparePost},
 * que es público y no final: lo sobreescribimos para reescribir el header ya
 * construido, quitando solo el padding final de cada valor Base64 y dejando
 * intactos los separadores {@code name=value} y {@code ;}.
 *
 * Es la opción menos invasiva: reutiliza todo el pipeline de la librería (JWT,
 * cifrado, cliente HTTP async) y solo corrige el header ofensivo. No requiere
 * reflection, cliente HTTP manual ni cambio de librería.
 */
public class CryptoKeyPaddingFixPushService extends PushService {

    private static final String CRYPTO_KEY_HEADER = "Crypto-Key";

    public CryptoKeyPaddingFixPushService() {
        super();
    }

    @Override
    public HttpPost preparePost(Notification notification, Encoding encoding)
            throws GeneralSecurityException, IOException, JoseException {
        HttpPost post = super.preparePost(notification, encoding);

        Header cryptoKey = post.getFirstHeader(CRYPTO_KEY_HEADER);
        if (cryptoKey != null) {
            // setHeader reemplaza el header existente con el mismo nombre.
            post.setHeader(CRYPTO_KEY_HEADER, stripBase64Padding(cryptoKey.getValue()));
        }

        return post;
    }

    /**
     * Quita el padding Base64 de cada valor del header Crypto-Key.
     *
     * El header tiene forma {@code dh=<base64>;p256ecdsa=<base64>} (aesgcm) o
     * {@code p256ecdsa=<base64>} (aes128gcm). Los {@code =} que siguen a
     * {@code dh}/{@code p256ecdsa} son separadores name=value; el padding son
     * los {@code =} al final de cada valor Base64. Se eliminan solo estos
     * últimos, segmento por segmento (separados por {@code ;}).
     */
    static String stripBase64Padding(String headerValue) {
        return Arrays.stream(headerValue.split(";"))
                .map(CryptoKeyPaddingFixPushService::stripSegmentPadding)
                .collect(Collectors.joining(";"));
    }

    private static String stripSegmentPadding(String segment) {
        int separator = segment.indexOf('=');
        if (separator < 0) {
            return segment;
        }
        String name = segment.substring(0, separator);
        String value = segment.substring(separator + 1).replaceAll("=+$", "");
        return name + "=" + value;
    }
}
