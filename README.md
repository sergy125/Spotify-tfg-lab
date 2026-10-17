# Spotify TFG

Dos proyectos independientes en un mismo repositorio. Cada uno conserva su configuración y sus dependencias.

## App móvil (`spotify-app/`)

```bash
cd spotify-app
npm install
npm start
```

Las dependencias existentes ya están en esta carpeta. `npm install` es necesario al descargar el repositorio de nuevo.

## ReccoBeats Lab (`reccobeats-lab/`)

Programa de terminal con Python 3.9 o superior; solo usa la biblioteca estándar.

```bash
cd reccobeats-lab
python3 main.py
```

Consulta [su README](reccobeats-lab/README.md) para más opciones.

```bash
cd reccobeats-lab
python3 -m unittest discover -s tests -v
```

La base de datos local se ha copiado, pero no se incluye en Git. El programa puede crearla de nuevo si no existe.

Git se gestiona desde esta raíz. Ejecuta los comandos de cada proyecto desde su propia carpeta.
