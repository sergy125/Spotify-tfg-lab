# Laboratorio de estimaciones de audiofeatures

Proyecto Java 21+ independiente. Consulta Gemini directamente, sin arrancar el backend,
iniciar sesión en Spotify, consultar ReccoBeats ni modificar ninguna base de datos.
Incluye su propia dependencia Gson en `lib/`.

Desde `ai-features-lab/`:

```sh
# Utiliza automáticamente la clave ya configurada en ../backend/gemini.properties.
./run.sh canciones.example.json resultados.json
# O solo imprimir la comparación de las veinte canciones:
./run.sh
```

La consulta real consume la cuota de tu API. Lee `api.key` de `../backend/gemini.properties`,
el mismo archivo que utiliza el backend, ignorado por Git. No requiere una copia local de la clave.
Para compilar sin hacer peticiones: `./run.sh --help`.

El JSON de entrada contiene una lista de canciones con `id`, `title`, `artists` y `album`
(textos obligatorios; identificadores únicos). También acepta `nombre` y `artistas`, como el backend.
El programa crea canciones con features vacías:
aunque la entrada incluya valores de referencia de las canciones objetivo, no se envían al modelo.
Por cada canción se imprime una tabla con las once features: referencia, estimación,
diferencia (estimada menos referencia) y error absoluto. Tempo se compara en BPM y loudness
en dB. Key y mode muestran diferencias entre códigos, no distancias musicales.
Las referencias deben incluir las once features numéricas y no tener `ai=true`.
La popularidad se sigue estimando para mantener la misma petición del backend, pero no
forma parte de la comparación de audiofeatures.
El segundo argumento permite guardar opcionalmente los metadatos y las once features
estimadas, `popularity` y `ai=true`. No sobrescribe archivos existentes.
Se guarda al terminar todas las consultas correctamente.

`audiofeatures/IAfeatures.java` parte de una copia de la clase del backend, con el mismo
modelo (`gemini-3.5-flash-lite`), esquema JSON, configuración y grupos de hasta
20 canciones. El prompt añade diez ejemplos de `json10.json` y se utiliza la clave del backend. Son copias independientes:
los cambios futuros de una no se propagan a la otra.
El modelo recibe metadatos, no audio. Se conservan las validaciones y los reintentos
ante HTTP 429 del backend; puedes cancelar con Ctrl+C si la cuota sigue agotada.

Cada ejecución carga `json10.json` e incluye sus diez canciones con features conocidas
y comentarios en el prompt. Las primeras cinco son las de `json5.json`, conservadas sin cambios:
Visión, FIESTA PRIVADA, Nike Tiburon, Sports car y Too Many Drugs.
Se añaden Nunca Debiste Cruzar el Mississippi (Leiva), La Familia (PXXR GVNG),
me matas remixx (Xiyo, Fernandezzz), Prófugos (Elenco de Soy Luna) y Too Late (The Weeknd).
`json5.json` se conserva para documentar el experimento anterior y no se carga en esta variante.
Se mantienen como objetivos las veinte canciones de `canciones.example.json`.
Los ejemplos se validan antes de consultar la API: deben tener features completas,
`ai=false`, comentarios y no coincidir con los objetivos por ID o título y artista.
No se activa búsqueda en internet ni se envía audio. Los comentarios orientan la interpretación
de las features y no son etiquetas emocionales verificadas.

## Canciones de referencia

`canciones.example.json` contiene veinte filas exportadas de `backend/database/lab`, tabla
`Songs`, con los nombres de campos del backend y `ai` convertido a booleano como en `Song`.
Conserva todos los valores guardados. UNA BIMBO POR MADRID (Metrika, D.Basto), Opalite,
Dear John (Taylor's Version), I Don’t Wanna Live Forever (Fifty Shades Darker) y
Better Than Revenge tienen `ai=false`. Se mantienen las versiones exactas de la base de datos.
Al ejecutar este archivo, las features guardadas se ignoran para la petición a Gemini;
permanecen en el archivo como referencia para la comparación en pantalla.

Prueba del flujo completo con una respuesta simulada, sin consumir API:

```sh
./run.sh --help
javac --release 21 -cp 'out:lib/*' -d out tests/MainTest.java
java -cp 'out:lib/*' MainTest
```

Las primeras cinco canciones conservan su orden y valores. Las quince añadidas se han
seleccionado por variedad de artistas y contraste en sus features guardadas: acousticness,
energy, instrumentalness, speechiness, tempo y valence. Todas tienen `ai=false` y las once
features completas. La variedad está limitada al catálogo disponible en la base de datos.
