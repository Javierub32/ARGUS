# ARGUS

**AI-assisted Retrieval and Generation for Understanding Source Code**

ARGUS es un backend local para indexar repositorios de código y preparar su
recuperación semántica. El proyecto forma parte del TFG y se encuentra en
desarrollo.

> **Entorno validado:** Windows x64. La compilación nativa con GraalVM, el
> ejecutable `.exe` y la petición `POST /api/index` se han probado en Windows.
> No se ha validado todavía en otros sistemas operativos.

## Estado actual

La versión actual incluye:

- Endpoint REST `POST /api/index` para indexar un directorio existente.
- Validación de la ruta recibida en la petición.
- Escaneo recursivo y determinista del proyecto.
- Detección de contenido de texto UTF-8 y filtrado de archivos binarios.
- Límite de tamaño configurable de 3 MiB por archivo.
- Exclusión de directorios generados o de dependencias (`.git`, `target`,
  `node_modules`, `build`, `dist`, `.idea`, `tmp` y `memory`) y de
  `repomix-output.xml`.
- Indexación incremental mediante hashes SHA-256: solo se vuelven a procesar
  los archivos nuevos o modificados y se eliminan del índice los borrados.
- Generación de un embedding por archivo mediante Ollama. La estrategia actual
  crea un único chunk con el contenido completo del archivo.
- Persistencia de los chunks en índices vectoriales Apache Lucene con similitud
  COSINE.
- Persistencia de la metadata de los archivos en JSON, con escritura temporal y
  sustitución atómica cuando el sistema de archivos lo permite.
- Generación de un ejecutable nativo de Windows con GraalVM.

La división sintáctica con Tree-sitter, la búsqueda semántica y la generación
de respuestas RAG todavía están pendientes. `TreeSitterChunker` se mantiene
como punto de extensión; la implementación usada actualmente es
`WholeFileChunker`.

## Arquitectura actual

```text
Cliente HTTP
    |
    | POST /api/index
    v
ProjectIndexRestController
    |
    v
ProjectIndexService
    |
    +--> FileScanner + FileContentDetector
    |       (archivos de texto válidos)
    |
    +--> SHA-256 + comparación con metadata anterior
    |
    +--> WholeFileChunker
    |       |
    |       +--> Ollama / embeddinggemma:300m
    |
    +--> ChunkRepository
    |       (Apache Lucene, NIOFSDirectory)
    |
    +--> IndexedFileRepository
            (metadata JSON por proyecto)
```

El modelo de chat `qwen2.5-coder:3b` queda configurado para la futura fase de
consultas. La ruta disponible actualmente es la de indexación y utiliza el
modelo de embeddings.

## Tecnologías

- **Java 25**
- **Spring Boot 4.1.1**
- **Spring AI 2.0.1**
- **Spring Web MVC** para la API REST
- **Spring Boot Validation** para validar las peticiones
- **Spring Boot Actuator** para monitorización
- **Ollama** para ejecutar los modelos localmente
- **Apache Lucene 10.5.1** para almacenar los vectores y la metadata de los
  chunks
- **GraalVM Native Build Tools** para generar el ejecutable nativo
- **Maven Wrapper 3.3.4**, configurado para descargar Maven 3.9.16
- **Lombok** como dependencia opcional

## Configuración

La configuración principal está en
[`src/main/resources/application.properties`](src/main/resources/application.properties):

| Propiedad | Valor actual | Descripción |
|---|---|---|
| `spring.application.name` | `ARGUS` | Nombre de la aplicación |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | URL del servidor Ollama |
| `spring.ai.ollama.chat.model` | `qwen2.5-coder:3b` | Modelo de chat configurado |
| `spring.ai.ollama.embedding.model` | `embeddinggemma:300m` | Modelo utilizado para generar embeddings |
| `spring.http.clients.imperative.factory` | `jdk` | Cliente HTTP JDK para la imagen nativa |
| `spring.http.clients.reactive.connector` | `jdk` | Conector HTTP JDK para la imagen nativa |
| `app.indexed-files-root` | `${user.home}/.argus/indexed-files` | Metadata JSON de los archivos indexados |
| `app.chunks-root` | `${user.home}/.argus/chunks` | Índices Lucene de los chunks |
| `app.indexing.max-file-size-bytes` | `3145728` | Tamaño máximo por archivo, 3 MiB |
| `management.endpoints.web.exposure.include` | `health,info` | Endpoints de Actuator expuestos |

Para cambiar los modelos, modifica las dos propiedades de Ollama y descarga
los modelos correspondientes en Ollama.

## Requisitos en Windows

- Windows x64.
- JDK 25 para ejecutar el proyecto en la JVM.
- GraalVM 25 o superior con `native-image` para compilar el ejecutable nativo.
- Visual Studio Build Tools con el compilador MSVC y el Windows SDK. La
  terminal usada para la compilación nativa debe tener disponible `cl.exe`.
- Ollama ejecutándose en `http://localhost:11434`.
- No es necesario instalar Maven globalmente: el repositorio incluye
  `mvnw.cmd` y el Maven Wrapper.

Descarga los modelos configurados desde PowerShell:

```powershell
ollama pull qwen2.5-coder:3b
ollama pull embeddinggemma:300m
```

## API de indexación

La ruta disponible es:

```text
POST http://localhost:8080/api/index
Content-Type: application/json
```

El cuerpo debe contener la ruta de un directorio existente:

```powershell
$body = @{ projectRoot = "C:/Users/javie/Desktop/MiataThon" } | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/index" `
    -ContentType "application/json" `
    -Body $body
```

La respuesta informa de los archivos nuevos, modificados, eliminados y sin
cambios, además del número de archivos y chunks procesados y de los tiempos de
indexación.

## Compilación en Windows

Todos los comandos de esta sección están pensados para PowerShell en Windows.
Para la compilación nativa, abre **Developer PowerShell for Visual Studio** o
**x64 Native Tools Command Prompt for Visual Studio**, de forma que `cl.exe`
esté disponible.

### Preparar GraalVM

Ajusta `JAVA_HOME` a la instalación local de GraalVM. Este es un ejemplo de la
ruta utilizada durante las pruebas:

```powershell
$env:JAVA_HOME = "C:\Users\javie\.jdks\graalvm-ce-25.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

java -version
native-image --version
where.exe cl
```

### Ejecutar los tests

```powershell
.\mvnw.cmd test
```

### Generar el JAR para la JVM

```powershell
.\mvnw.cmd clean package -DskipTests
```

El resultado se genera en:

```text
target\argus-0.0.1-SNAPSHOT.jar
```

### Generar el ejecutable nativo `.exe`

```powershell
.\mvnw.cmd -Pnative clean native:compile -DskipTests
```

El ejecutable se genera en:

```text
target\argus.exe
```

Si aparece un error indicando que no se encuentra `cl.exe`, repite el comando
desde una de las terminales de Visual Studio indicadas arriba y comprueba que
GraalVM sea el `JAVA_HOME` activo.

## Ejecución en Windows

### Con Maven

```powershell
.\mvnw.cmd spring-boot:run
```

### Ejecutando el JAR

```powershell
java -jar target\argus-0.0.1-SNAPSHOT.jar
```

### Ejecutando el `.exe` nativo

```powershell
.\target\argus.exe
```

Antes de hacer una petición, Ollama debe estar ejecutándose con los modelos
configurados disponibles.

## Configuración específica para GraalVM nativo

La imagen nativa usa el cliente HTTP JDK en lugar de Netty para evitar el fallo
de memoria que se producía al indexar. Lucene utiliza `NIOFSDirectory`, porque
la variante `FSDirectory` produjo un `UnsupportedFeatureError` relacionado con
`Arena.ofShared` en GraalVM para Windows. Además, `IndexedFile` está registrado
para reflexión para que Jackson pueda leer la metadata JSON dentro del `.exe`.

