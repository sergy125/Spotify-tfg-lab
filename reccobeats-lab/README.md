# Buscador de canciones con ReccoBeats

Programa de terminal en Python 3.9 o superior. Solo usa la biblioteca estándar: no requiere instalar paquetes ni configurar claves.

## Ejecutar

```bash
python3 main.py
python3 main.py "Hello"
python3 main.py "sue me audrey hobert"
python3 main.py "Hello" --actualizar
python3 main.py --db ./mi_biblioteca.db
```

Escribe un título y elige el número de la canción. Se muestran los títulos, artistas e IDs de las coincidencias; puedes ver más páginas con `n` y cancelar con `0`. La búsqueda puede incluir coincidencias parciales y distintas versiones: elige la grabación que quieras guardar. En modo interactivo, pulsa Enter sin escribir un título para salir.

También puedes escribir el título seguido del artista, como `sue me audrey hobert`. El programa consulta la entrada completa y sus prefijos para encontrar el título, elimina resultados repetidos y pone primero las coincidencias de título y artista (sin distinguir mayúsculas ni tildes). La prioridad se aplica a los resultados recuperados de cada página; depende de que la canción esté en el catálogo de ReccoBeats.

## Base de datos

Por defecto se crea `canciones.db` junto al programa. La tabla `canciones` contiene una fila por ID de ReccoBeats, nombre, artistas, enlace, ISRC, duración, popularidad y las once características de audio: acousticness, danceability, energy, instrumentalness, key, liveness, loudness, mode, speechiness, tempo y valence. También conserva las respuestas completas en `metadata_json` y `audio_json` para no perder otros campos.

Una selección ya guardada con audio se lee de SQLite; `--actualizar` vuelve a consultar la API y actualiza la misma fila. La búsqueda por nombre siempre consulta ReccoBeats para mostrar las coincidencias actuales. Cada grabación conserva sus propios valores; compartir un título no significa compartir características de audio.

Si el endpoint de audio devuelve 404, se guardan los metadatos y los campos de audio quedan en NULL, con `audio_disponible = 0`. Al seleccionar esa canción de nuevo, se intenta obtener el audio otra vez. Los errores de red, HTTP y JSON se muestran sin guardar una respuesta incompleta como válida.

Puedes consultar SQLite con cualquier visor o con:

```sql
SELECT nombre, artistas, tempo, energy, danceability FROM canciones;
```

## API oficial

- Búsqueda: https://reccobeats.com/docs/apis/search-tracks
- Detalle: https://reccobeats.com/docs/apis/get-track-by-id
- Audio: https://reccobeats.com/docs/apis/get-track-audio-features

El programa usa `/v1/track/search?searchText=...&page=...&size=20`, `/v1/track/{id}` y `/v1/track/{id}/audio-features`.

## Comprobaciones

```bash
python3 -m unittest discover -s tests -v
```
