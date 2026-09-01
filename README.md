# ARGUS

**AI-assisted Retrieval and Generation for Understanding Source Code**

ARGUS es un sistema local de búsqueda semántica de código basado en Retrieval-Augmented Generation (RAG). Su objetivo es ayudar a los desarrolladores a localizar dónde se implementa una funcionalidad en un repositorio, indicando el archivo, la línea relevante y una explicación generada por un modelo de lenguaje.

El proyecto forma parte de un TFG y se encuentra en desarrollo. La base actual contiene el backend inicial de Spring Boot y la configuración de los modelos locales; la indexación del código, la API de consultas y la integración con el IDE se irán incorporando progresivamente.

## Objetivos

- Mantener el procesamiento del código y de los modelos en local siempre que sea posible.
- Permitir consultas en lenguaje natural sobre repositorios de código.
- Dividir el código en fragmentos semánticamente útiles para mejorar la recuperación.
- Generar respuestas con contexto recuperado desde el propio repositorio.
- Devolver referencias precisas al archivo y a la línea donde se encuentra la funcionalidad.
- Comparar distintas estrategias de indexación completa e incremental.
- Estudiar, como ampliación, una recuperación híbrida con embeddings y GraphRAG.

## Arquitectura objetivo

```text
Cliente de terminal / extensión de IDE
                |
                v
        API REST de Spring Boot
                |
        +-------+--------+
        |                |
        v                v
  Recuperación       Ollama local
  vectorial          embeddings + LLM
  Apache Lucene
        |
        v
  Fragmentos de código
  archivo, línea y contexto
```

El flujo previsto es el siguiente:

1. Se analiza el repositorio y se divide el código en fragmentos mediante Tree-sitter.
2. Cada fragmento se transforma en un embedding utilizando un modelo local.
3. Los vectores y sus metadatos se almacenan en un índice vectorial basado en Apache Lucene.
4. La consulta del usuario también se convierte en un embedding.
5. Se recuperan los fragmentos más similares y se envían como contexto al modelo de lenguaje.
6. El backend devuelve una respuesta con la ruta del archivo, la línea relevante y una explicación.

La extensión de VS Code y la posible compatibilidad con otros IDEs forman parte de la arquitectura prevista, pero no están implementadas todavía en esta versión.

## Tecnologías

- **Java 25**
- **Spring Boot 4.1.1**
- **Spring AI 2.0.1**
- **Spring Web MVC** para la futura API REST
- **Spring Boot Actuator** para endpoints de monitorización
- **Spring Boot Validation** para validar peticiones
- **Ollama** para ejecutar localmente el modelo de chat y el modelo de embeddings
- **Apache Lucene 10.5.1** como base del almacenamiento y recuperación vectorial
- **Maven Wrapper** para compilar sin instalar Maven globalmente
- **GraalVM Native Build Tools** para generar un ejecutable nativo
- **Lombok** como dependencia opcional
- **Tree-sitter**, previsto para el análisis sintáctico multilenguaje

## Configuración actual

La configuración principal se encuentra en [`src/main/resources/application.properties`](src/main/resources/application.properties):

| Propiedad | Valor por defecto | Descripción |
|---|---|---|
| `spring.ai.ollama.base-url` | `http://localhost:11434` | URL del servidor Ollama |
| `spring.ai.ollama.chat.model` | `mistral` | Modelo de lenguaje para generar respuestas |
| `spring.ai.ollama.embedding.model` | `mxbai-embed-large` | Modelo utilizado para crear embeddings |
| `app.lucene.index-path` | `${user.home}/.code-rag/index` | Ubicación del índice local |

## Requisitos

Para compilar y ejecutar el backend se necesita:

- JDK 25.
- Ollama instalado y ejecutándose en `http://localhost:11434`.
- Los modelos configurados descargados en Ollama:

```powershell
ollama pull mistral
ollama pull mxbai-embed-large
```

Para generar el ejecutable nativo también se necesita GraalVM 25 o superior con `native-image` disponible.

## Compilación

El repositorio incluye Maven Wrapper. En Windows PowerShell, el nombre correcto del wrapper es `mvnw.cmd`.

### Paquete JVM

```powershell
.\mvnw.cmd clean package -DskipTests
```

El JAR se genera en:

```text
target/code-rag-backend-0.0.1-SNAPSHOT.jar
```

En Linux o macOS se puede utilizar:

```bash
./mvnw clean package -DskipTests
```

### Ejecutable nativo con GraalVM

```powershell
.\mvnw.cmd -Pnative clean native:compile -DskipTests
```

En Windows, el ejecutable nativo se genera normalmente en `target/code-rag-backend.exe`.

En Linux o macOS:

```bash
./mvnw -Pnative clean native:compile -DskipTests
```

## Ejecución

### Con Maven

```powershell
.\mvnw.cmd spring-boot:run
```

### Ejecutando el JAR

```powershell
java -jar target/code-rag-backend-0.0.1-SNAPSHOT.jar
```

### Ejecutando la imagen nativa

```powershell
.\target\code-rag-backend.exe
```

## Tests

Para ejecutar la suite de tests:

```powershell
.\mvnw.cmd test
```

## Estado del proyecto

Actualmente el repositorio contiene el arranque del backend Spring Boot, la configuración de Spring AI y Ollama, la integración base con Lucene y un test de carga del contexto. Las siguientes piezas se encuentran en desarrollo:

- API REST para indexación y consultas.
- División del código con Tree-sitter.
- Indexación completa e incremental mediante `git diff`.
- Recuperación semántica con embeddings.
- Generación de respuestas con referencias a archivo y línea.
- Cliente de terminal y extensión de VS Code.
- Evaluación de latencia, velocidad de indexación y exactitud.
- Estudio de una estrategia híbrida con GraphRAG.

