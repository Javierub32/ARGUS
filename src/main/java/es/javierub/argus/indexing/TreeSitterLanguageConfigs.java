package es.javierub.argus.indexing;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@NoArgsConstructor
public final class TreeSitterLanguageConfigs {

    public static final List<TreeSitterLanguageConfig> languageConfigs =
            List.of(
                    new TreeSitterLanguageConfig(
                            "java",
                            "tree-sitter-java",
                            "tree_sitter_java",
                            List.of(".java"),
                            List.of(
                                    "class_declaration",
                                    "interface_declaration",
                                    "enum_declaration",
                                    "record_declaration",
                                    "annotation_type_declaration",
                                    "method_declaration",
                                    "constructor_declaration",
                                    "field_declaration"
                            ),
                            "0.23.5",
                            15
                    )
            );

    public Optional<TreeSitterLanguageConfig> findByPath(String relativePath) {
        String path = relativePath.toLowerCase(Locale.ROOT);

        return languageConfigs.stream().filter(
                config -> config.extensions().stream()
                .anyMatch(extension -> path.endsWith(extension)))
                .findFirst();
    }
}