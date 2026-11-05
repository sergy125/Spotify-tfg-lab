# Backend de terminal

Ejecutar desde `backend/`, con un JDK 21 o superior:

```sh
mkdir -p /tmp/spotify-backend-classes
javac -cp 'lib/*' -d /tmp/spotify-backend-classes Main.java BackendConsole.java models/*.java database/*.java spotify/*.java processor/*.java audiofeatures/*.java
java --enable-native-access=ALL-UNNAMED -cp '/tmp/spotify-backend-classes:lib/*' Main
```

Configura `SPOTIFY_CLIENT_ID` y `SPOTIFY_CLIENT_SECRET` en el entorno de ejecución.
La clave de Gemini se guarda en `backend/gemini.properties`, un archivo local ignorado por Git.
Copia `gemini.properties.example` como `gemini.properties` y sustituye el valor de `api.key`
por tu clave. No necesitas variables de entorno para Gemini.
El modelo se define mediante `MODEL` en `audiofeatures/IAfeatures.java`.

## Conectar una cuenta desde otro móvil u ordenador

El menú «Conectar un nuevo Spotify» acepta un callback público HTTPS. La cuenta
se guarda en la tabla `Users` de este backend, con sus tokens, y el menú continúa
con el usuario conectado. El navegador confirma el éxito después de guardar en SQLite;
no muestra los tokens. Cada opción de conectar permite autorizar una cuenta durante
diez minutos; para conectar otra, vuelve a elegir esa opción.

1. Instala `cloudflared` y, en otra terminal, ejecuta:

   ```sh
   cloudflared tunnel --url http://127.0.0.1:8888
   ```

2. Copia la URL HTTPS que muestra, por ejemplo `https://ejemplo.trycloudflare.com`.
   En Spotify Developer Dashboard → tu aplicación → Settings → Redirect URIs,
   añade **exactamente** `https://ejemplo.trycloudflare.com/callback` y guarda.
3. En las variables de ejecución de **Backend Main** en IntelliJ cambia
   `SPOTIFY_REDIRECT_URI` a esa misma URL completa con `/callback`.
   Configura `SPOTIFY_CALLBACK_PORT=8888` (es el puerto local por defecto para HTTPS).
   En terminal, exporta esas variables antes de ejecutar `Main`.
4. Arranca el backend, elige conectar un nuevo Spotify y envía a la otra persona
   el enlace de autorización que imprime la consola. Ese enlace sí se puede abrir
   desde otro dispositivo: el `state` se comprueba en el backend sin depender de
   cookies del navegador original. Usa el enlace recién generado una sola vez.
5. Mantén el backend y el túnel abiertos hasta que el móvil confirme que la cuenta
   se ha guardado y tu consola muestre `Usuario conectado`.

Si reinicias un túnel temporal y cambia su URL, actualiza tanto Spotify como
`SPOTIFY_REDIRECT_URI` y reinicia el backend. Un dominio fijo con proxy HTTPS evita
ese cambio. El proxy debe reenviar el path y los parámetros del callback sin modificarlos
al puerto `SPOTIFY_CALLBACK_PORT` del ordenador del backend. No necesitas abrir
puertos del router para el túnel.

Spotify exige HTTPS para callbacks públicos. En modo Development, las cuentas
tienen que estar autorizadas en User Management del panel de Spotify; publicar el
callback no elimina esa restricción de Spotify.

Sin túnel puedes seguir usando `http://127.0.0.1:8888/callback`, pero solo desde
el ordenador donde ejecutas el backend.

Referencias: [redirect URIs de Spotify](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)
y [restricciones de Development Mode](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).

Prueba del callback con Spotify simulado y SQLite temporal (incluye estado incorrecto,
guardado de tokens sin exponerlos en la respuesta y rechazo de callbacks repetidos):

```sh
javac -cp '/tmp/spotify-backend-classes:lib/*' -d /tmp/spotify-backend-classes tests/spotify/SpotifyRemoteLoginTest.java
java --enable-native-access=ALL-UNNAMED -ea -cp '/tmp/spotify-backend-classes:lib/*' spotify.SpotifyRemoteLoginTest
```

ReccoBeats se consulta primero. Solo las canciones cuyas audiofeatures siguen incompletas tras consultar ReccoBeats
pasan a `IAfeatures`, en grupos de hasta 20 canciones distintas por petición. Esto incluye
canciones encontradas sin audiofeatures o con campos nulos. Si devuelve todas las audiofeatures,
no se envían a IA aunque falte popularidad. Los errores de red tampoco activan IA.
Gemini estima las once features y popularidad (entero 0..100), con JSON numérico sin nulos.
Solo se rellenan huecos; se conservan los valores existentes. Las canciones completas
(features y popularidad) se reutilizan sin más peticiones. Las incompletas ya guardadas
se procesan cuando vuelven a aparecer en el historial o los tops importados.

La columna booleana `Songs.ai` se añade automáticamente a bases existentes al crear las tablas.
SQLite muestra `0` (false) y `1` (true). `ai=true` indica que alguna feature procede de IA;
`ai=false` indica que ninguna procede de IA. Una fila antigua todavía incompleta también empieza
con false: no significa que ya tenga todas sus features.

Una respuesta nula, incompleta o con números fuera de rango se rechaza: no se guardan estimaciones
vacías ni se ponen valores inventados localmente para ocultar errores de la API. Los errores de
clave o conexión impiden completar la importación; se puede reintentar después. Los límites
HTTP 429 se gestionan automáticamente con esperas y reintentos.
Si Gemini devuelve HTTP 503 (servicio temporalmente no disponible), se reintenta el
mismo grupo hasta tres veces, respetando `Retry-After` o esperando 5, 10 y 20 segundos.
Si continúa fallando, se informa de la indisponibilidad y se puede reintentar más tarde.

Pruebas sin red ni consumo de créditos, después de compilar:

```sh
javac -cp '/tmp/spotify-backend-classes:lib/*' -d /tmp/spotify-backend-classes tests/audiofeatures/IAfeaturesTest.java
java --enable-native-access=ALL-UNNAMED -ea -cp '/tmp/spotify-backend-classes:lib/*' audiofeatures.IAfeaturesTest
```

En el menú inicial de cuentas, la opción «Buscar en ReccoBeats las features de canciones
estimadas con IA» revisa todas las filas `ai=1` sin iniciar sesión en Spotify ni consultar Gemini.
Solo guarda una canción si ReccoBeats devuelve las once audiofeatures completas; entonces
sustituye las estimaciones, cambia `ai` a `0` y muestra su nombre. Si no hay features, la
respuesta es parcial o falla la conexión, conserva todos sus datos y `ai=1`. Al final muestra
un resumen. Las canciones que ya tienen `ai=0` no se consultan.

Prueba del menú sin red:

```sh
javac -cp '/tmp/spotify-backend-classes:lib/*' -d /tmp/spotify-backend-classes tests/BackendConsoleTest.java
java --enable-native-access=ALL-UNNAMED -ea -cp '/tmp/spotify-backend-classes:lib/*' BackendConsoleTest
```

Si ReccoBeats responde HTTP 429, se espera y se reintenta la misma petición hasta que deje
de limitarla. Se respeta `Retry-After` (segundos o fecha HTTP); si no es válido, se esperan
5, 10, 20, 40 y después 60 segundos entre reintentos. El programa muestra cada espera.
No se salta la canción por un 429 ni se modifica `ai` mientras espera. Se puede cancelar
la ejecución; otros errores de conexión o HTTP mantienen el tratamiento anterior.

Prueba de reintentos sin red ni esperas reales:

```sh
javac -cp '/tmp/spotify-backend-classes:lib/*' -d /tmp/spotify-backend-classes tests/audiofeatures/ReccoBeatsRetryTest.java
java --enable-native-access=ALL-UNNAMED -ea -cp '/tmp/spotify-backend-classes:lib/*' audiofeatures.ReccoBeatsRetryTest
```

La espera ante HTTP 429 se aplica también al importar muchas canciones: todas las consultas
HTTP de ReccoBeats pasan por el mismo método. Gemini utiliza la misma interpretación de
`Retry-After` y la misma espera progresiva de 5 a 60 segundos. Reintenta únicamente el grupo
actual de hasta 20 canciones, sin volver a enviar los grupos ya completados. Durante la espera
se muestra un mensaje; se puede cancelar la ejecución. Si el límite es diario, la espera puede
prolongarse hasta que se restablezca la cuota. Los errores diferentes de 429 no se reintentan.

Prueba de Gemini sin red ni consumo de créditos:

```sh
javac -cp '/tmp/spotify-backend-classes:lib/*' -d /tmp/spotify-backend-classes tests/audiofeatures/IAfeaturesRetryTest.java
java --enable-native-access=ALL-UNNAMED -ea -cp '/tmp/spotify-backend-classes:lib/*' audiofeatures.IAfeaturesRetryTest
```

## Tops de artistas

El menú de actualización mantiene las opciones 1–4 para historial y tops de canciones.
Las nuevas opciones son:

- `6`: `artist_top_short` (`short_term`).
- `7`: `artist_top_medium` (`medium_term`).
- `8`: `artist_top_long` (`long_term`).

La opción `5` actualiza todo: historial, tres tops de canciones y tres tops de artistas.
`SpotifyClient.getArtistTopShort/Medium/Long()` consulta `/me/top/artists` con `limit=50`.
Se utiliza el permiso `user-top-read` que ya solicita el login.

Cada tabla de artistas guarda `user_id`, `position` (desde 1), `artist_id`, `nombre` y `href`.
Se conserva el orden de Spotify y se sustituye únicamente el usuario y periodo seleccionado,
en una transacción. Una respuesta válida vacía deja vacío ese top; una respuesta inválida
conserva el anterior. No se consulta ReccoBeats ni Gemini para importar artistas.
La creación de las tablas está incluida en `MusicDatabase.createAllTables()`.

Prueba completa de los tops de artistas, sin red:

```sh
javac -cp '/tmp/spotify-backend-classes:lib/*' -d /tmp/spotify-backend-classes tests/ArtistTopsTest.java
java --enable-native-access=ALL-UNNAMED -ea -cp '/tmp/spotify-backend-classes:lib/*' ArtistTopsTest
```
