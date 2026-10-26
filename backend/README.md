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
clave, cuota o conexión impiden completar la importación; se puede reintentar después.

Pruebas sin red ni consumo de créditos, después de compilar:

```sh
javac -cp '/tmp/spotify-backend-classes:lib/*' -d /tmp/spotify-backend-classes tests/audiofeatures/IAfeaturesTest.java
java --enable-native-access=ALL-UNNAMED -ea -cp '/tmp/spotify-backend-classes:lib/*' audiofeatures.IAfeaturesTest
```
