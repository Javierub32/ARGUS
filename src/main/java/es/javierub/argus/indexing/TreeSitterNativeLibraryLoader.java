package es.javierub.argus.indexing;

import io.github.treesitter.jtreesitter.Language;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TreeSitterNativeLibraryLoader {
    private final Path nativeDirectory;

    // We use a ConcurrentHashMap to prevent multiple reads or writes on the loader
    private final Map<String, Language> languages =new ConcurrentHashMap<>();

    private boolean coreLoaded = false;

    public TreeSitterNativeLibraryLoader(
            @Value("${app.tree-sitter.native-dir:native/dist/windows-x64}")
            String nativeDirectory
    ) {
        this.nativeDirectory = Path.of(nativeDirectory).toAbsolutePath().normalize();
    }

    public Language load(TreeSitterLanguageConfig definition) {
        // We only load the core or a new language if it isn't loaded already
        ensureCoreLoaded();

        return languages.computeIfAbsent(definition.languageId(),languajeId -> loadLanguage(definition));
    }

    private Language loadLanguage(TreeSitterLanguageConfig definition) {
        Path grammarLibrary = nativeDirectory.resolve(System.mapLibraryName(definition.libraryName()));

        if (!Files.isRegularFile(grammarLibrary)) {
            throw new IllegalStateException("No existe la librería nativa: " + grammarLibrary);
        }

        SymbolLookup symbols = SymbolLookup.libraryLookup(grammarLibrary.toString(), Arena.global());

        Language language = Language.load(symbols, definition.symbolName());

        if (language.getAbiVersion() != definition.abiVersion()) {
            throw new IllegalStateException("ABI incompatible. Expected: "
                    + language.getAbiVersion() + "Actual: " + definition.abiVersion());
        }

        return language;
    }

    private synchronized void ensureCoreLoaded() {
        if (coreLoaded) {
            return;
        }

        Path coreLibrary = nativeDirectory.resolve(System.mapLibraryName("tree-sitter"));

        if (!Files.isRegularFile(coreLibrary)) {
            throw new IllegalStateException("Tree-sitter runtime doesn't exist: " + coreLibrary);
        }

        System.load(coreLibrary.toString());

        coreLoaded = true;
    }
}