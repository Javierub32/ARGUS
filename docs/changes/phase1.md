# Fase 1: Base de indexación — decisiones técnicas y arquitectura

Este documento registra las decisiones técnicas tomadas durante la primera
fase de ARGUS. El objetivo de la fase es disponer de un backend local capaz de
recibir la ruta de un proyecto, detectar sus archivos de texto, generar sus
embeddings y persistir el resultado para poder construir la recuperación
semántica en fases posteriores.

La implementación de esta fase se ha validado en **Windows x64**. La
compilación nativa, el ejecutable `.exe` y el endpoint de indexación se han
probado en ese entorno.

## 1. Un único backend Spring Boot

### Decisión

ARGUS se implementa inicialmente como una aplicación Spring Boot que concentra
la API REST, el escaneo de archivos, la generación de embeddings y la
persistencia local. El objetivo final es distribuirla como un ejecutable nativo
de Windows.

### Justificación

Mantener estas responsabilidades en un único proceso simplifica la instalación
y la ejecución del producto. La distribución nativa reduce las dependencias
que debe instalar el usuario final y evita que tenga que disponer de Java 25 y
GraalVM 25 para ejecutar ARGUS.

La imagen nativa no elimina la dependencia del servidor de modelos: durante el
uso, Ollama sigue siendo un servicio local necesario para generar embeddings y,
en el futuro, respuestas del modelo de chat.

## 2. Spring Boot, Spring AI y Ollama

### Decisión

Spring Boot proporciona el servidor HTTP y la configuración de la aplicación.
Spring AI se utiliza como abstracción para realizar las peticiones a Ollama,
en particular mediante `EmbeddingModel` para generar los vectores de los
archivos.

### Justificación

Spring AI permite mantener la integración con los modelos separada de la
lógica de indexación y configurar el proveedor y los modelos desde
`application.properties`. Esto deja abierta la posibilidad de evaluar otros
modelos o proveedores sin acoplar el dominio a una implementación HTTP
concreta.

## 3. Modelos iniciales de Ollama

### Decisión

La configuración inicial utiliza:

```properties
spring.ai.ollama.chat.model=qwen2.5-coder:3b
spring.ai.ollama.embedding.model=embeddinggemma:300m
```

### Justificación

Estos modelos se han elegido a partir de la investigación realizada para esta
fase por su equilibrio entre capacidad y coste de ejecución local para el tipo
de proyecto. `embeddinggemma:300m` es el modelo utilizado actualmente durante
la indexación. `qwen2.5-coder:3b` queda configurado para la futura fase de
consultas y generación de respuestas.

La selección no se considera definitiva. En fases posteriores se evaluarán
otras combinaciones de modelos con distintas capacidades, tamaños, latencias y
calidad de recuperación.

## 4. Lucene embebido en lugar de PgVector u otra BD similar

### Decisión

Los chunks y sus embeddings se almacenan en índices vectoriales de Apache
Lucene embebidos en la aplicación.

### Justificación

PgVector es una extensión de PostgreSQL y habría obligado a añadir una base de
datos como servicio independiente. Lucene permite centralizar el índice local
dentro de ARGUS y distribuirlo junto con el servidor principal, sin requerir
otro servicio para almacenar y buscar los vectores.


## 5. Persistencia local de metadata y de chunks

### Decisión

Cada proyecto tiene una identidad derivada de la ruta normalizada y dispone de:

- Un archivo `indexed-files.json` con la metadata de sus archivos.
- Un índice Lucene independiente con sus chunks y embeddings.

Las ubicaciones se configuran mediante:

```properties
app.indexed-files-root=${user.home}/.argus/indexed-files
app.chunks-root=${user.home}/.argus/chunks
```

### Justificación

Esta estructura permite mantener los datos separados por proyecto sin
introducir una base de datos externa.

## 6. Indexación incremental mediante SHA-256

### Decisión

La indexación compara la ejecución actual con la metadata persistida. Se
calculan hashes SHA-256 para identificar el proyecto, identificar cada archivo
y detectar cambios en su contenido.

### Justificación

Reprocesar todo el repositorio en cada petición sería innecesario y costoso,
especialmente cuando cada archivo procesado requiere una petición al modelo de
embeddings. La estrategia incremental permite conservar los archivos sin
cambios, reprocesar solo los nuevos o modificados y eliminar del índice los
archivos que ya no existen.

El informe de la API incluye los archivos nuevos, modificados, eliminados y sin
cambios, además de los archivos y chunks procesados y los tiempos medidos.

## 7. `WholeFileChunker` como estrategia inicial

### Decisión

La primera fase utiliza `WholeFileChunker`, que crea un único chunk con todo el
contenido de cada archivo y solicita un embedding para ese contenido.

### Justificación

Esta estrategia facilita disponer de un flujo completo de indexación antes de
introducir todo el análisis sintáctico con el Tree-sitter. Permite validar el escaneo, la persistencia,
la integración con Ollama y la indexación vectorial con una implementación
pequeña y fácil de verificar.

`TreeSitterChunker` será la siguiente función a implementar. Próximamente se compararán ambas estrategias
viendo la calidad de recuperación, memoria, latencia y número de chunks.

## 8. Límite de 3 MiB por archivo

### Decisión

`app.indexing.max-file-size-bytes` se ha fijado inicialmente en `3145728` bytes,
es decir, 3 MiB.

### Justificación

Con `WholeFileChunker`, un archivo grande se envía completo al modelo de
embeddings en una sola operación. Durante las pruebas, un archivo JSON de
aproximadamente 3 MB tardó 4,6 minutos en indexarse. Ese tiempo hace inviable
procesar archivos mucho mayores con la estrategia actual y puede aumentar el
consumo de memoria.

El límite es una decisión práctica para esta fase. Cuando se implemente `TreeSitterChunker`
se estudiará si se puede subir este límite.

## 9. Detección por contenido en lugar de una lista de extensiones

### Decisión

Un archivo se indexa si pasa varias comprobaciones de contenido; la extensión
por sí sola no determina si es indexable.

### Justificación

Inicialmente se planteó mantener una lista de extensiones válidas. Esa solución
obligaría a hardcodear todas las extensiones de lenguajes, configuraciones y
formatos de documentación que podrían aparecer en un repositorio. También
rechazaría archivos de texto sin extensión o con una extensión desconocida.

`FileContentDetector` comprueba que el elemento sea un archivo regular, que no
sea un enlace simbólico, que no supere el límite de tamaño, que no empiece por
firmas binarias conocidas, que no contenga bytes nulos y que una muestra de su
contenido sea UTF-8 válido. Si las comprobaciones indican texto, el archivo se
indexa; si indican contenido binario, se descarta.

Además, `FileScanner` ignora directorios generados o de dependencias como
`.git`, `target`, `node_modules`, `build`, `dist`, `.idea`, `tmp` y `memory`.

## 10. Compatibilidad de la imagen nativa con GraalVM

### Decisión

La configuración nativa incorpora tres ajustes:

1. Spring utiliza el cliente HTTP JDK en los modos imperativo y reactivo:

   ```properties
   spring.http.clients.imperative.factory=jdk
   spring.http.clients.reactive.connector=jdk
   ```

2. Lucene abre los índices mediante `NIOFSDirectory`.
3. `IndexedFile` se registra con `@RegisterReflectionForBinding`.

### Justificación

La ejecución en la JVM funcionaba, pero la primera imagen nativa sufría un
`segmentation fault` al procesar `POST /api/index`. El fallo se producía en la
ruta de Netty durante la ejecución nativa, por lo que se sustituyó el cliente
HTTP por el cliente JDK.

Después de evitar el cierre del proceso apareció un segundo problema propio de
las imágenes nativas: Jackson necesitaba construir `IndexedFile` pero  GraalVM no conserva automáticamente todos
los constructores, así que la clase se registró explícitamente.

La prueba de `FSDirectory` mostró además la incompatibilidad de
`Arena.ofShared` descrita en la sección de Lucene. Con estos ajustes, el
ejecutable `target\\argus.exe` mantiene el proceso activo y procesa la ruta
`POST /api/index` correctamente en Windows.

## 11. Lombok para reducir código repetitivo

### Decisión

Se utiliza Lombok en los DTO y en las clases que necesitan constructores,
getters, setters y métodos de igualdad.

### Justificación

Las anotaciones como `@Data`, `@NoArgsConstructor` y `@AllArgsConstructor`
mantienen las clases de dominio más pequeñas y legibles. El procesamiento de
anotaciones se configura en Maven para compilación y tests.

## 12. API REST mínima para la primera fase

### Decisión

La primera API expone:

```text
POST /api/index
```

El cuerpo recibe un `projectRoot` con la ruta del directorio a indexar. La
petición se valida y el controlador comprueba que la ruta exista y sea un
directorio antes de delegar en `ProjectIndexService`.

### Justificación

Una única operación permite validar el flujo de extremo a extremo sin
adelantar decisiones de la fase de búsqueda. Las rutas de consulta semántica y
la generación de respuestas se añadirán cuando estén implementados el índice
consultable y el flujo RAG.

## 13. Maven Wrapper y distribución nativa en Windows

### Decisión

El repositorio conserva Maven Wrapper para que la compilación use una versión
conocida de Maven sin exigir una instalación global. En Windows se utiliza
`mvnw.cmd`; el wrapper está configurado para Maven 3.9.16.

La compilación nativa se realiza con GraalVM y el compilador MSVC desde una
terminal de Visual Studio que tenga `cl.exe` disponible:

```powershell
.\mvnw.cmd -Pnative clean native:compile -DskipTests
```

El resultado esperado es:

```text
target\\argus.exe
```

### Justificación

El `.exe` permite que el usuario final ejecute el backend sin instalar Java 25
ni GraalVM 25. La máquina que construye el ejecutable sí necesita GraalVM,
`native-image` y las herramientas de compilación de Visual Studio.

## Cobertura de la fase 1

La suite de tests cubre las piezas principales de esta fase:

| Área | Cobertura |
|---|---|
| Contexto de Spring Boot | Arranque del contexto de la aplicación |
| Controlador REST | Delegación de una ruta válida y rechazo de rutas inexistentes o que no sean directorios |
| `FileContentDetector` | Texto UTF-8, archivos sin extensión, firmas binarias, bytes nulos, enlaces simbólicos y límite de tamaño |
| `FileScanner` | Recorrido recursivo, orden determinista y exclusiones |
| `Hasher` | Hashes SHA-256 de texto y archivos y propagación de errores de lectura |
| `WholeFileChunker` | Creación del chunk, embedding, líneas y lenguaje asociado a la ruta |
| `ChunkRepository` | Escritura, sustitución, borrado, vectores obligatorios y validación de identificadores |
| `IndexedFileRepository` | Persistencia, orden, snapshots, validación y aislamiento por proyecto |
| `ProjectIndexService` | Archivos nuevos, modificados, eliminados y sin cambios en varias ejecuciones |

## Resultado y trabajo pendiente

La fase 1 deja funcionando el recorrido completo desde `POST /api/index` hasta
la persistencia de metadata y vectores en local, tanto en la JVM como en el
ejecutable nativo validado en Windows.

Las siguientes decisiones deberán contrastarse en fases posteriores:

- Comparación de `WholeFileChunker` con `TreeSitterChunker`.
- Recuperación KNN de chunks y endpoint de consulta.
- Construcción del contexto y generación de respuestas RAG con el modelo de
  chat.
- Evaluación de otras combinaciones de modelos de chat y embeddings.
- Reevaluación del límite de tamaño para archivos grandes.
- Validación de la distribución en otros entornos, que todavía no se ha
  realizado.
