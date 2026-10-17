"""Busca canciones en ReccoBeats y guarda su información en SQLite."""
import argparse
import http.client
import json
import socket
import sqlite3
import ssl
import re
import unicodedata
from pathlib import Path
from urllib.parse import quote, urlencode

FEATURES = ('acousticness', 'danceability', 'energy', 'instrumentalness', 'key',
            'liveness', 'loudness', 'mode', 'speechiness', 'tempo', 'valence')
DEFAULT_DB = Path(__file__).resolve().parent / 'canciones.db'


class APIError(Exception):
    def __init__(self, message, status=None):
        super().__init__(message)
        self.status = status


class ReccoBeats:
    def get(self, path, params=None):
        if params:
            path += '?' + urlencode(params)
        conn = http.client.HTTPSConnection('api.reccobeats.com', timeout=20)
        try:
            conn.request('GET', path, headers={'Accept': 'application/json'})
            response = conn.getresponse()
            body = response.read()
            if response.status != 200:
                raise APIError(f'ReccoBeats respondió HTTP {response.status}. '
                               'Si es 429, espera un momento y vuelve a intentar.', response.status)
            try:
                return json.loads(body)
            except (ValueError, UnicodeDecodeError) as exc:
                raise APIError('La API devolvió una respuesta JSON inválida.') from exc
        except (OSError, http.client.HTTPException, ssl.SSLError, socket.timeout) as exc:
            raise APIError(f'No se pudo conectar con ReccoBeats: {exc}') from exc
        finally:
            conn.close()

    def search(self, title, page=0):
        data = self.get('/v1/track/search', {'searchText': title, 'page': page, 'size': 20})
        if not isinstance(data, dict) or not isinstance(data.get('content'), list):
            raise APIError('Formato inesperado en la búsqueda de canciones.')
        return data

    def detail(self, track_id):
        data = self.get('/v1/track/' + quote(track_id, safe=''))
        if not isinstance(data, dict) or data.get('id') != track_id:
            raise APIError('Formato inesperado en el detalle de la canción.')
        return data

    def features(self, track_id):
        try:
            data = self.get('/v1/track/' + quote(track_id, safe='') + '/audio-features')
        except APIError as exc:
            if exc.status == 404:
                return None
            raise
        if not isinstance(data, dict) or not any(k in data for k in FEATURES):
            raise APIError('Formato inesperado en las características de audio.')
        return data


class Database:
    def __init__(self, path):
        self.conn = sqlite3.connect(path)
        self.conn.row_factory = sqlite3.Row
        columns = ', '.join(f'"{f}" {"INTEGER" if f in ("key", "mode") else "REAL"}' for f in FEATURES)
        self.conn.execute(f'''CREATE TABLE IF NOT EXISTS canciones (
            id TEXT PRIMARY KEY, nombre TEXT NOT NULL, artistas TEXT NOT NULL,
            href TEXT, isrc TEXT, duracion_ms INTEGER, popularidad INTEGER,
            {columns}, audio_disponible INTEGER NOT NULL,
            metadata_json TEXT NOT NULL, audio_json TEXT,
            actualizado_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)''')
        self.conn.commit()

    def get(self, track_id):
        return self.conn.execute('SELECT * FROM canciones WHERE id = ?', (track_id,)).fetchone()

    def save(self, track, audio):
        artists = ', '.join(a.get('name', '') for a in track.get('artists', []))
        fields = ['id', 'nombre', 'artistas', 'href', 'isrc', 'duracion_ms', 'popularidad',
                  *FEATURES, 'audio_disponible', 'metadata_json', 'audio_json']
        values = [track['id'], track.get('trackTitle', track.get('name', 'Sin título')), artists,
                  track.get('href') or (audio or {}).get('href'),
                  track.get('isrc') or (audio or {}).get('isrc'), track.get('durationMs'),
                  track.get('popularity'), *((audio or {}).get(f) for f in FEATURES),
                  int(audio is not None), json.dumps(track, ensure_ascii=False),
                  json.dumps(audio, ensure_ascii=False) if audio is not None else None]
        names = ', '.join(f'"{f}"' for f in fields)
        updates = ', '.join(f'"{f}" = excluded."{f}"' for f in fields if f != 'id')
        with self.conn:
            self.conn.execute(f'INSERT INTO canciones ({names}) VALUES ({", ".join("?" for _ in fields)}) '
                              f'ON CONFLICT(id) DO UPDATE SET {updates}, actualizado_en = CURRENT_TIMESTAMP', values)

    def close(self):
        self.conn.close()


def normalize(text):
    text = unicodedata.normalize('NFKD', text.casefold())
    text = ''.join(c for c in text if not unicodedata.combining(c))
    return ' '.join(re.findall(r'\w+', text))


def search_candidates(api, query, page):
    # La API puede buscar solo por título: probamos los prefijos de la entrada
    # para recuperar «Sue Me» incluso al escribir «Sue Me Audrey Hobert».
    words = query.split()
    queries = [' '.join(words[:end]) for end in range(len(words), 0, -1)]
    candidates = {}
    more = False
    for text in queries:
        data = api.search(text, page)
        more |= not data.get('last', len(data['content']) < 20)
        for track in data['content']:
            candidates.setdefault(track['id'], track)

    normalized_query = normalize(query)
    query_words = set(normalized_query.split())

    def relevance(track):
        name = normalize(track.get('trackTitle', track.get('name', '')))
        artists = [normalize(a.get('name', '')) for a in track.get('artists', [])]
        exact_pair = any(normalized_query == name + ' ' + artist for artist in artists)
        combined = set((name + ' ' + ' '.join(artists)).split())
        coverage = len(query_words & combined) / max(len(query_words), 1)
        return (exact_pair, coverage, normalized_query == name,
                track.get('popularity') or 0)

    return sorted(candidates.values(), key=relevance, reverse=True), more


def select_track(api, title):
    page = 0
    while True:
        tracks, more = search_candidates(api, title, page)
        if not tracks:
            print('No se encontraron más canciones.' if page else 'No se encontraron canciones.')
            return None
        print(f'\nResultados — página {page + 1}:')
        for i, track in enumerate(tracks, 1):
            artists = ', '.join(a.get('name', '') for a in track.get('artists', []))
            name = track.get('trackTitle', track.get('name', 'Sin título'))
            print(f'{i}. {name} — {artists or "Artista desconocido"} [ID: {track["id"]}]')
        prompt = 'Elige el número (0: cancelar' + (', n: siguiente página' if more else '') + '): '
        while True:
            choice = input(prompt).strip().lower()
            if choice == '0':
                return None
            if choice == 'n' and more:
                page += 1
                break
            if choice.isdigit() and 1 <= int(choice) <= len(tracks):
                return tracks[int(choice) - 1]
            print('Introduce una opción válida.')


def show(row):
    print(f'\n{row["nombre"]} — {row["artistas"]}')
    for field in FEATURES:
        print(f'  {field}: {row[field] if row[field] is not None else "No disponible"}')
    if not row['audio_disponible']:
        print('La API no dispone de características de audio para esta canción.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cancion', nargs='?', help='Título que quieres buscar')
    parser.add_argument('--db', type=Path, default=DEFAULT_DB, help='Ruta del archivo SQLite')
    parser.add_argument('--actualizar', action='store_true', help='Volver a consultar datos ya guardados')
    args = parser.parse_args()
    db = Database(args.db)
    api = ReccoBeats()
    try:
        title = args.cancion
        while True:
            if title is None:
                title = input('\nNombre de la canción (Enter para salir): ').strip()
            if not title.strip():
                return
            try:
                selected = select_track(api, title.strip())
                if selected:
                    saved = db.get(selected['id'])
                    if saved and saved['audio_disponible'] and not args.actualizar:
                        print('Canción ya guardada; usando los datos de SQLite.')
                    else:
                        track = api.detail(selected['id'])
                        audio = api.features(selected['id'])
                        db.save(track, audio)
                        print(f'Canción guardada en {args.db.resolve()}')
                    show(db.get(selected['id']))
            except APIError as exc:
                print(f'Error: {exc}')
                if args.cancion:
                    return 1
            if args.cancion:
                return
            title = None
    finally:
        db.close()


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (KeyboardInterrupt, EOFError):
        print('\nPrograma finalizado.')
    except sqlite3.Error as exc:
        print(f'Error de SQLite: {exc}')
        raise SystemExit(1)
