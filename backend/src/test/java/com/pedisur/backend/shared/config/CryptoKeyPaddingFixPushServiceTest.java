package com.pedisur.backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifica el workaround del header Crypto-Key (issue #212 de web-push): se
 * elimina solo el padding Base64 de cada valor, preservando los separadores
 * name=value y el ';' entre segmentos.
 */
class CryptoKeyPaddingFixPushServiceTest {

    @Test
    void stripsPaddingFromBothSegmentsInAesgcm() {
        // Formato aesgcm: dh=<base64>;p256ecdsa=<base64>, ambos con padding.
        String input = "dh=BCDEf123==;p256ecdsa=GHIjkl456=";
        assertThat(CryptoKeyPaddingFixPushService.stripBase64Padding(input))
                .isEqualTo("dh=BCDEf123;p256ecdsa=GHIjkl456");
    }

    @Test
    void stripsPaddingFromSingleSegmentInAes128gcm() {
        // Formato aes128gcm: solo p256ecdsa=<base64>.
        String input = "p256ecdsa=ABCdef789==";
        assertThat(CryptoKeyPaddingFixPushService.stripBase64Padding(input))
                .isEqualTo("p256ecdsa=ABCdef789");
    }

    @Test
    void leavesValuesWithoutPaddingUnchanged() {
        String input = "dh=NoPadHere;p256ecdsa=AlsoNone";
        assertThat(CryptoKeyPaddingFixPushService.stripBase64Padding(input))
                .isEqualTo(input);
    }

    @Test
    void preservesNameValueSeparatorEvenWhenValueIsAllPadding() {
        // El '=' que sigue al nombre es separador, no padding: no debe perderse.
        assertThat(CryptoKeyPaddingFixPushService.stripBase64Padding("p256ecdsa="))
                .isEqualTo("p256ecdsa=");
    }
}
