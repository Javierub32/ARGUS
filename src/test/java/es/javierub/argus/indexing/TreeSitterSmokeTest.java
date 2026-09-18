package es.javierub.argus.indexing;

import io.github.treesitter.jtreesitter.InputEncoding;
import io.github.treesitter.jtreesitter.Language;
import io.github.treesitter.jtreesitter.Node;
import io.github.treesitter.jtreesitter.Parser;
import io.github.treesitter.jtreesitter.Tree;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterSmokeTest {

    @Test
    void loadsJavaGrammarAndParsesJavaSource() {
        Path nativeDirectory = Path.of(
                System.getProperty(
                        "argus.tree-sitter.native-dir",
                        "native/dist/windows-x64"
                )
        ).toAbsolutePath().normalize();

        Path coreLibrary = nativeDirectory.resolve("tree-sitter.dll");
        Path javaLibrary = nativeDirectory.resolve("tree-sitter-java.dll");

        assertTrue(
                Files.isRegularFile(coreLibrary),
                "No existe: " + coreLibrary
        );

        assertTrue(
                Files.isRegularFile(javaLibrary),
                "No existe: " + javaLibrary
        );

        // Carga explícita del runtime de Tree-sitter.
        System.load(coreLibrary.toString());

        SymbolLookup symbols = SymbolLookup.libraryLookup(
                javaLibrary.toString(),
                Arena.global()
        );

        Language language = Language.load(
                symbols,
                "tree_sitter_java"
        );

        assertEquals(15, language.getAbiVersion());

        String source = """
                package demo;

                public class ClienteService {
                    public void buscarCliente() {
                    }
                }
                """;

        try (Parser parser = new Parser(language);
             Tree tree = parser
                     .parse(source, InputEncoding.UTF_8)
                     .orElseThrow()) {

            Node root = tree.getRootNode();

            assertEquals("program", root.getType());
            assertFalse(root.hasError());

            String syntaxTree = root.toSexp();

            assertTrue(syntaxTree.contains("class_declaration"));
            assertTrue(syntaxTree.contains("method_declaration"));
        }
    }
}