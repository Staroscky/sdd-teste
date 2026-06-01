package com.example.extrato.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "upstream.recentes.url=http://recentes-test",
        "upstream.recentes.timeout.connect=1000",
        "upstream.recentes.timeout.read=2000",
        "upstream.futuros.url=http://futuros-test",
        "upstream.futuros.timeout.connect=1500",
        "upstream.futuros.timeout.read=3000"
})
class UpstreamPropertiesTest {

    @Autowired
    private UpstreamProperties properties;

    @Test
    void deveBindarPropriedadesDeRecentesCorretamente() {
        assertThat(properties.recentes().url()).isEqualTo("http://recentes-test");
        assertThat(properties.recentes().timeout().connect()).isEqualTo(1000);
        assertThat(properties.recentes().timeout().read()).isEqualTo(2000);
    }

    @Test
    void deveBindarPropriedadesDeFuturosCorretamente() {
        assertThat(properties.futuros().url()).isEqualTo("http://futuros-test");
        assertThat(properties.futuros().timeout().connect()).isEqualTo(1500);
        assertThat(properties.futuros().timeout().read()).isEqualTo(3000);
    }

    @Test
    void timeoutsDeRecentesEFuturosDevemSerIndependentes() {
        assertThat(properties.recentes().timeout().connect())
                .isNotEqualTo(properties.futuros().timeout().connect());
        assertThat(properties.recentes().timeout().read())
                .isNotEqualTo(properties.futuros().timeout().read());
    }
}
