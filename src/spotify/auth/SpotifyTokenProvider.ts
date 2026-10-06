// Un futuro OAuthTokenProvider implementará este mismo contrato.
export interface SpotifyTokenProvider {
    getAccessToken(): Promise<string>;
}
