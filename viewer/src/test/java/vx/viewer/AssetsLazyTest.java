package vx.viewer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integração dos lotes de conteúdo: o catálogo de props pode crescer (a biblioteca do laboratório já dobrou o
 * {@code props.json}) sem virar memória gasta. Registrar não carrega: nenhuma malha existe até uma cena pedir o prop pelo
 * nome. (O carregamento em si precisa de GL e é medido nos roteiros: {@code tools/menu/14-memoria.txt}.)
 */
class AssetsLazyTest {
    @Test
    void oCatalogoRegistraTudoSemCarregarNada() {
        try (Assets a = new Assets(Path.of("assets"))) {
            Map<String, Assets.Prop> props = a.props("e");
            assertTrue(props.size() >= 81, "aprovados + biblioteca de conteúdo + variantes publicadas: " + props.size());
            assertTrue(props.containsKey("house"), "aprovado");
            assertTrue(props.containsKey("workshop_forge"), "biblioteca do lote 1");
            assertTrue(props.containsKey("market_stall"), "biblioteca do lote 2");
            assertTrue(props.containsKey("sign_trade__oficio-herbalista"), "variante publicada");
            assertFalse(props.containsKey("tree_tiered"), "rejeitado não entra no props.json");
            assertTrue(props.keySet().contains("well"));
            assertEquals(0, a.loadedProps("e"), "registrar, consultar e listar nomes não malham nada");
            assertNull(props.get("nao_existe"), "nome desconhecido: null, sem carregar");
            assertEquals(0, a.loadedProps("e"));
        }
    }
}
