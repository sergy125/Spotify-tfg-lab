import type { SpotifyTokenProvider } from './SpotifyTokenProvider';

export class ManualTokenProvider implements SpotifyTokenProvider {
    constructor(private token: string = '') {}

    setToken(token: string): void {
        this.token = token.trim();
    }

    async getAccessToken(): Promise<string> {
        const token = this.token.trim();
        if (!token) {
            throw new Error('Pega un access token de Spotify antes de cargar los resultados.');
        }
        return token;
    }
}
