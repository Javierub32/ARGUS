# ARGUS

**AI-assisted Retrieval and Generation for Understanding Source Code**

ARGUS es un backend local para indexar repositorios de código y preparar su
recuperación semántica. El proyecto se encuentra en
desarrollo.

> **Entorno validado:** Windows x64 para la generación del `.exe` y cualquier sistema operativo con JVM.

Todas las decisiones técnicas de este proyecto se encuentran en:
[`docs/changes/phase1.md`](docs/changes/phase1.md).

## Estado actual

La versión actual incluye:

- Endpoint REST `POST /api/index` para indexar un directorio existente.
- Escaneo recursivo del proyecto.
- Detección de contenido de texto UTF-8 y filtrado de archivos binarios para no indexar archivos incoherentes.
- Límite de tamaño configurable de 3 MiB por archivo (cuando se implemente el Tree-Sitter se reevaluará).
- Exclusión de directorios generados o de dependencias (`.git`, `target`,
  `node_modules`, `build`, `dist`, `.idea`, `tmp`, ...).
- Indexación incremental mediante hashes SHA-256: solo se vuelven a procesar
  los archivos nuevos o modificados y se eliminan del índice los borrados.
- Generación de un embedding por archivo mediante Ollama. La estrategia actual
  crea un único chunk con el contenido completo del archivo.
- Persistencia de los chunks en índices vectoriales Apache Lucene.
- Persistencia de la metadata de los archivos que actualmente se encuentran indexados en JSON.
- Generación de un ejecutable nativo de Windows con GraalVM.

La división sintáctica con Tree-sitter, la búsqueda semántica y la generación
de respuestas RAG todavía están pendientes.

`TreeSitterChunker` es la siguiente implementación que se realizará. La implementación usada actualmente es
`WholeFileChunker`, que genera un chunk por archivo por simplificidad de la primera fase del proyecto.

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
    |       (Apache Lucene)
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
- **Lombok**

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

Actualmente esta es la configuración recomendada del proyecto. En caso de querer
cambiar los modelos, modifica las dos propiedades de Ollama y descarga
los modelos correspondientes en Ollama.



## Requisitos
- JDK 25 para ejecutar el proyecto en la JVM.
- GraalVM 25 o superior con `native-image` para compilar el ejecutable nativo.
- Ollama ejecutándose en `http://localhost:11434`.
- El proyecto incluye Maven Wrapper. En Windows puedes ejecutar los comandos
  con `.\mvnw.cmd` sin instalar Maven globalmente; el wrapper descarga y usa
  automáticamente la versión de Maven configurada para el proyecto (3.9.16).
- Windows x64 (solo para compilar el .exe).
- Visual Studio Build Tools con el compilador MSVC y el Windows SDK. La
    terminal usada para la compilación nativa debe tener disponible `cl.exe` (solo para compilar el .exe).

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
$body = @{ projectRoot = "C:/Users/javie/Desktop/Hackathon" } | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/index" `
    -ContentType "application/json" `
    -Body $body
```

La respuesta informa de los archivos nuevos, modificados, eliminados y sin
cambios, además del número de archivos y chunks procesados y de los tiempos de
indexación.

### Formato de la respuesta

Si la indexación finaliza correctamente, el endpoint devuelve `200 OK` con un
cuerpo JSON como el siguiente (valores de ejemplo):

```json
{
  "projectRoot": "C:/Users/javie/Desktop/Hackathon",
  "newFiles": 10,
  "modifiedFiles": 2,
  "deletedFiles": 1,
  "unchangedFiles": 25,
  "processedFiles": 12,
  "processedChunks": 12,
  "durationMs": 15420,
  "durationEmbeddingMs": 14800
}
```

| Campo | Tipo | Descripción                                                                                                               |
|---|---|---------------------------------------------------------------------------------------------------------------------------|
| `projectRoot` | `string` | Ruta absoluta y normalizada del directorio indexado.                                                                      |
| `newFiles` | `integer` | Archivos nuevos respecto a la indexación anterior.                                                                        |
| `modifiedFiles` | `integer` | Archivos cuyo contenido ha cambiado.                                                                                      |
| `deletedFiles` | `integer` | Archivos del índice anterior que ya no aparecen en el escaneo actual y se eliminan del índice.                            |
| `unchangedFiles` | `integer` | Archivos conservados sin volver a procesarlos.                                                                            |
| `processedFiles` | `integer` | Archivos procesados en esta petición: `newFiles + modifiedFiles`.                                                         |
| `processedChunks` | `integer` | Chunks generados para los archivos procesados en esta petición (actualmente igual al numero de archivos procesados).      |
| `durationMs` | `integer` | Duración total de la indexación, en milisegundos.                                                                         |
| `durationEmbeddingMs` | `integer` | Tiempo acumulado de generación de chunks y embeddings y de su persistencia para los archivos procesados, en milisegundos. |

## Compilación con JVM

Se puede ejecutar desde IntelliJ clickando en ejecutar el proyecto.
Para compilación a .exe no se ha probado en otro sistema operativo que no sea Windows aún.

## Compilación en Windows

Todos los comandos de esta sección están pensados para PowerShell en Windows.
Para la compilación nativa, hay que tener instaladas las **herramientas de desarrollo Visual Studio con C++**.

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
desde una de las terminales de Visual Studio y comprueba que
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
