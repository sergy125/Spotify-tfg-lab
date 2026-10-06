import type { SpotifyTokenProvider } from '../auth/SpotifyTokenProvider';

export class SpotifyApiClient {
    private readonly baseUrl = 'https://api.spotify.com/v1';

    constructor(private readonly tokenProvider: SpotifyTokenProvider) {}

    async get<T>(endpoint: string): Promise<T> {
        if (!endpoint.startsWith('/') || endpoint.startsWith('//')) {
            throw new Error('El endpoint debe ser una ruta relativa a la API de Spotify.');
        }
        const token = await this.tokenProvider.getAccessToken();
        let response: Response;
        try {
            response = await fetch(`${this.baseUrl}${endpoint}`, {
                headers: { Authorization: `Bearer ${token}` },
            });
        } catch {
            throw new Error('No se ha podido conectar con Spotify. Comprueba tu conexión.');
        }
        if (!response.ok) {
            let detail = response.statusText || 'La petición ha fallado';
            if (response.status === 401) {
                detail = 'El token no es válido o ha caducado. Pega uno nuevo.';
            } else if (response.status === 403) {
                detail = 'Acceso denegado. Comprueba los permisos del token y de la aplicación.';
            } else if (response.status === 429) {
                const retryAfter = response.headers.get('Retry-After');
                detail = retryAfter
                    ? `Demasiadas peticiones. Reintenta en ${retryAfter} segundos.`
                    : 'Demasiadas peticiones. Espera antes de reintentar.';
            }
            throw new Error(`Error de Spotify (HTTP ${response.status}): ${detail}`);
        }
        try {
            return await response.json() as T;
        } catch {
            throw new Error('Spotify ha devuelto una respuesta JSON no válida.');
        }
    }
}
