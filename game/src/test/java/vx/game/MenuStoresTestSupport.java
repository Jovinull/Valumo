package vx.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Ajuda dos testes do menu: sha256 de um arquivo (o original não pode mudar). */
final class MenuStoresTestSupport {
    static String sha(Path f) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f)));
    }

    private MenuStoresTestSupport() { }
}
