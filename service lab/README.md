# Service Lab

Servicio Java 21 con Spring Boot, Maven, RestClient, Spring Data JPA y **SQLite**. Obtiene hasta 50 canciones del top personal de Spotify del periodo elegido, busca sus características en ReccoBeats y las guarda en un archivo local.

## Darle a Play

En este equipo ya está preparado:

1. Selecciona **Service Lab** en la barra superior de IntelliJ.
2. Pulsa **▶ Play**.
3. Haz clic en la consola de Run, escribe `1`, `2` o `3` y pulsa Enter.
4. Espera al mensaje `Importación terminada: ...`. En Database pulsa **Refresh** para ver el nuevo ranking.

```text
SERVICE LAB — Elige el periodo de Spotify
  1) short_term  — aproximadamente las últimas 4 semanas
  2) medium_term — aproximadamente los últimos 6 meses
  3) long_term   — aproximadamente el último año, según Spotify
Escribe 1, 2 o 3 y pulsa Enter (0 para cancelar):
```

También puedes escribir el nombre exacto (`short_term`, `medium_term`, `long_term`). Si te equivocas, vuelve a preguntar; `0` cancela. El periodo largo es el que define Spotify, no una elección arbitraria de años.

**Cada importación sustituye todas las filas de `tracks` por el ranking elegido.** No conserva canciones de periodos anteriores. El archivo SQLite permanece en la misma ruta para que IntelliJ pueda seguir conectado.

La salida de Run se ha simplificado: sin el dibujo de Spring Boot y sin sus mensajes informativos de arranque. Un código de salida 130 al pulsar Stop o Ctrl+C indica una interrupción manual, no un fallo en la importación.

El lanzador `run-local.sh` compila con Java 21 y arranca la aplicación. SQLite abre el archivo local y la importación se ejecuta después de elegir el periodo. No necesitas instalar ni arrancar un servidor de base de datos.

También puedes arrancarlo desde la raíz del repositorio:

```bash
cd "service lab"
./run-local.sh
```

El botón Stop detiene la aplicación. Las canciones permanecen en el archivo SQLite.

## Dónde está la base de datos

**`service lab/service-lab.db`**, junto a `pom.xml` y este README.

En IntelliJ, abre **Database → service-lab (SQLite) → main → tables → tracks**. También puedes abrir `service-lab.db` con cualquier visor SQLite. En una instalación nueva, usa **Database → + → Data Source → SQLite** y selecciona este archivo.

El archivo y sus posibles auxiliares `-journal`, `-wal` y `-shm` están excluidos de Git. No borres `service-lab.db` si quieres conservar las canciones. Hibernate crea la tabla `tracks` cuando el archivo no existe.

La migración de este equipo conserva las canciones de la base anterior. Sus archivos originales siguen guardados en `.local/pgdata/` como respaldo, pero ya no se utilizan para ejecutar Service Lab.

## Token y configuración

El token está en el archivo local **`.env`**, excluido de Git. Cuando expire, sustituye `SPOTIFY_ACCESS_TOKEN` y vuelve a pulsar Play. El token debe pertenecer a un usuario y tener el permiso **`user-top-read`**; introduce solo su valor, sin `Bearer`.

La aplicación carga `.env` desde esta carpeta o desde `service lab/.env` al ejecutarla desde la raíz. Las variables de entorno reales del proceso tienen prioridad. No se implementa OAuth ni renovación de tokens.

Para una instalación nueva:

```bash
cp .env.example .env
```

Edita el token. Usa el formato `VARIABLE=valor`, sin `export` ni comillas, ya que el lanzador también carga el archivo en la shell.

| Variable | Valor por defecto | Uso |
| --- | --- | --- |
| `SPOTIFY_ACCESS_TOKEN` | Vacío | Token manual con `user-top-read` |
| `DB_URL` | `jdbc:sqlite:service-lab.db` | Archivo SQLite; la ruta relativa parte del directorio de trabajo |
| `SERVER_PORT` | `8080` | Puerto del servicio |
| `SERVER_ADDRESS` | `127.0.0.1` | Dirección de escucha |
| `IMPORT_ON_STARTUP` | `true` | Preguntar el periodo e importar al arrancar; usa `false` para consultar sin importar |
| `IMPORT_TIME_RANGE` | Vacío | Opcional: periodo exacto para ejecuciones sin menú (`short_term`, `medium_term`, `long_term`) |

SQLite no necesita usuario ni contraseña. La aplicación limita el pool a una conexión y espera hasta cinco segundos si el archivo está bloqueado por otra operación.

## Java 21 y Maven en otro equipo

Instala un **JDK 21**, por ejemplo [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21). Comprueba `java -version`. En macOS, si tienes varios JDK:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

Abre `pom.xml` como proyecto Maven en IntelliJ y selecciona Java 21 como Project SDK y como JRE de Maven. La clase principal es `es.sergy.servicelab.ServiceLabApplication`. Para guardar siempre en esta carpeta, configura su Working directory como `service lab`.

Maven Wrapper está incluido; no necesitas instalar Maven. Desde esta carpeta:

```bash
./mvnw spring-boot:run
```

O genera y ejecuta el JAR:

```bash
./mvnw clean verify
java -jar target/service-lab-0.0.1-SNAPSHOT.jar
```

En Windows usa `mvnw.cmd`. La primera compilación necesita conexión para descargar las dependencias. En este equipo, Play usa los runtimes preparados en `.local/`, excluidos de Git; en otros equipos el lanzador utiliza Maven Wrapper y el JDK instalado.

## Consultar e importar

Ver las canciones guardadas, ordenadas por posición:

```bash
curl -s http://localhost:8080/api/tracks
```

Repetir la importación sin reiniciar el servicio:

```bash
curl -i -X POST "http://localhost:8080/api/import/top-tracks?time_range=short_term"
```

No necesita body. El parámetro `time_range` acepta `short_term`, `medium_term` y `long_term`; si se omite, usa `medium_term`. Un periodo no válido devuelve HTTP 400 sin modificar datos. Usa el token configurado en el proceso. Ejemplo de resumen:

```json
{"processed":50,"withReccoBeats":9,"withoutReccoBeats":41}
```

Los números dependen del ranking y la disponibilidad de ReccoBeats. Spotify puede devolver menos de 50 canciones. `withReccoBeats` cuenta canciones con al menos una característica disponible; `withoutReccoBeats` incluye canciones sin audio y fallos de consulta. Los avisos de consola distinguen los errores de la ausencia normal de datos.

## Cómo se guardan los datos

- Spotify se consulta con `GET /v1/me/top/tracks?time_range={periodo}&limit=50`. El periodo procede del menú o del parámetro HTTP. La primera canción recibida tiene `rank = 1` y se conserva exactamente el orden de `items`.
- Es un ranking de afinidad calculado por Spotify. No hay un contador de reproducciones.
- La única tabla es `tracks`. `spotify_id` es su clave primaria: cada importación vacía la tabla e inserta exclusivamente el ranking elegido, sin duplicados.
- Se guardan nombre, artistas, álbum, duración en milisegundos, posición y las nueve características: danceability, energy, valence, acousticness, instrumentalness, liveness, speechiness, tempo y loudness.
- Las canciones de importaciones anteriores se eliminan, tanto si cambias el periodo como si repites el mismo. `GET /api/tracks` devuelve solo el último ranking guardado.
- ReccoBeats se consulta con el ID de Spotify en `GET /v1/track?ids={spotifyId}` y luego con su ID interno en `GET /v1/track/{reccoBeatsId}/audio-features`.
- Si no hay audio, las características se guardan a `NULL`, también si antes tenían valores. No se sustituyen por cero.
- Un fallo de ReccoBeats no detiene las demás canciones. Ante un 429 se omiten las consultas restantes de esa ejecución y se guardan todos los metadatos de Spotify. Una nueva importación lo vuelve a intentar.
- La escritura completa se hace en una transacción después de las llamadas HTTP. Un fallo SQL revierte los cambios para evitar un ranking a medias. Las importaciones se ejecutan de una en una dentro del proceso; usa una sola instancia del laboratorio.
- Si Spotify falla, el ranking anterior permanece intacto. Una respuesta correcta con `items: []` deja la tabla vacía. Si la escritura SQL falla, el borrado también se revierte.

Consulta SQL:

```sql
SELECT spotify_id, name, artists, rank, danceability, energy, tempo
FROM tracks
ORDER BY rank IS NULL, rank, spotify_id;
```

## Errores habituales

| Situación | Comportamiento |
| --- | --- |
| Token vacío | HTTP 503 con instrucciones de configuración |
| Spotify 401 | Cambia el token en `.env` y reinicia; el endpoint devuelve 401 |
| Spotify 403 | Revisa `user-top-read` y el acceso del usuario a tu app Spotify |
| Spotify 429 | HTTP 429 con `Retry-After` cuando está disponible; espera antes de repetir |
| Spotify inaccesible o respuesta inválida | HTTP 502; se conserva el ranking anterior |
| ReccoBeats sin canción o audio (404) | Canción guardada con las características a `NULL` |
| ReccoBeats 429, 5xx, timeout o JSON inválido | Aviso en consola; se guarda sin audio y se continúa |
| Archivo SQLite inaccesible o bloqueado | Revisa su ruta, permisos y si otro programa mantiene una escritura abierta |

Si el token falla durante la importación automática, el servidor sigue disponible y se muestra el motivo. Los clientes HTTP tienen cinco segundos de timeout de conexión y quince de lectura. Los errores no incluyen el token ni el cuerpo original de Spotify.

## Clases

El código está en `src/main/java/es/sergy/servicelab/`:

- `SpotifyClient`: interfaz independiente del mecanismo de acceso.
- `SpotifyTokenClient`: implementación con token manual.
- `ReccoBeatsClient`: resolución del ID y consulta del audio.
- `Track` y `TrackRepository`: entidad JPA y acceso a SQLite.
- `TrackImportService`: coordinación y persistencia del ranking.
- `StartupImportRunner`: menú de consola e importación al arrancar.
- `TimeRange`: los tres periodos admitidos y su valor para Spotify.
- `ImportController`: los dos endpoints HTTP.
- `SpotifyTrack` y `AudioFeatures`: DTO.
- `HttpClientConfig`, `ExternalApiException` y `ApiExceptionHandler`: clientes HTTP, timeouts y errores.

Para añadir OAuth en el futuro, sustituye el bean `SpotifyTokenClient` por otra implementación de `SpotifyClient`. El servicio de importación depende únicamente de esa interfaz.

## Flujo del programa

1. **`run-local.sh`** carga `.env`, selecciona Java 21, compila el proyecto y ejecuta el JAR.
2. **`ServiceLabApplication`** arranca Spring Boot. Spring prepara el cliente HTTP, SQLite y las demás clases mediante inyección de dependencias.
3. **`StartupImportRunner`** muestra el menú. **`TimeRange`** transforma la opción elegida en `short_term`, `medium_term` o `long_term`.
4. **`TrackImportService.importTopTracks(periodo)`** coordina la importación. Primero llama a **`SpotifyClient`**, que es una interfaz.
5. Spring proporciona **`SpotifyTokenClient`** como implementación de esa interfaz. Lee el token de configuración y solicita las 50 canciones del periodo; las convierte a DTO **`SpotifyTrack`** manteniendo el orden.
6. Para cada canción, el servicio llama a **`ReccoBeatsClient`**. Este obtiene el ID interno de ReccoBeats a partir del ID de Spotify y después consulta las características, representadas por **`AudioFeatures`**. Si no hay datos o falla esa canción, sus campos quedan a `NULL` y continúa.
7. El servicio abre una transacción, llama a **`TrackRepository.deleteAllInBatch()`**, crea entidades **`Track`** con posiciones del 1 al 50 y las guarda con **`saveAllAndFlush()`**. Hibernate traduce esas operaciones a SQL sobre `service-lab.db`.
8. **`StartupImportRunner`** imprime el resumen. La aplicación queda en marcha para consultar datos.

**`ImportController`** ofrece una segunda entrada al mismo flujo: `POST /api/import/top-tracks?time_range=...` llama al mismo servicio, y `GET /api/tracks` consulta el repositorio. No necesita pasar por el menú.

El servicio no conoce el token: solo usa `SpotifyClient`. Una implementación futura con OAuth recibirá el mismo periodo y devolverá los mismos DTO, sin cambiar la lógica de importación.

## Pruebas

```bash
./mvnw verify
```

Las pruebas usan una base SQLite temporal real y HTTP simulado, sin consultar Spotify ni ReccoBeats. Comprueban las peticiones, los endpoints, las 50 posiciones, el reemplazo sin duplicados al cambiar de periodo, las canciones ausentes, los errores 401/429, el menú, las opciones inválidas, la cancelación y la reversión ante un fallo SQL. No modifican `service-lab.db`.

## Documentación oficial

- [Spotify: top personal y permiso user-top-read](https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks)
- [ReccoBeats: búsqueda por Spotify ID](https://reccobeats.com/docs/apis/get-tracks)
- [ReccoBeats: características por ID interno](https://reccobeats.com/docs/apis/get-track-audio-features)
- [Hibernate: dialecto SQLite](https://docs.hibernate.org/orm/6.6/dialect/)
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc)
