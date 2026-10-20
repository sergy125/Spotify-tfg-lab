package models;

public class User {
    private String userId;
    private String accessToken;
    private String refreshToken;
    private String expiresAt;
    private String authorizedAt;

    public User() {
    }

    public User(String userId) {
        this.userId = userId;
    }

    public User(String userId, String accessToken, String refreshToken,
                String expiresAt, String authorizedAt) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.authorizedAt = authorizedAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getAuthorizedAt() {
        return authorizedAt;
    }

    public void setAuthorizedAt(String authorizedAt) {
        this.authorizedAt = authorizedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
