package br.com.miranda.gestor.ativos.brutos.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConfigSqsTest {

    @Test
    void usa_credenciais_estaticas_quando_properties_trazem_chaves() {
        var provider = ConfigSqs.provedorCredenciais("local", "secret");

        assertInstanceOf(StaticCredentialsProvider.class, provider);
        AwsCredentials credentials = provider.resolveCredentials();
        assertEquals("local", credentials.accessKeyId());
        assertEquals("secret", credentials.secretAccessKey());
    }

    @Test
    void usa_cadeia_padrao_quando_chaves_nao_foram_configuradas() {
        var provider = ConfigSqs.provedorCredenciais("", " ");

        assertInstanceOf(DefaultCredentialsProvider.class, provider);
    }
}
