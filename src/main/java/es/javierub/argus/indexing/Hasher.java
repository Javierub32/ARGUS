package es.javierub.argus.indexing;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Hasher {
    public static String sha256(Path archivo)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = Files.newInputStream(archivo)) {
            byte[] buffer = new byte[8192];
            int leidos;

            while ((leidos = input.read(buffer)) != -1) {
                digest.update(buffer, 0, leidos);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }
}
