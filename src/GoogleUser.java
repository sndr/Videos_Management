/**
 * Representa um usuário autenticado via Google.
 */
public class GoogleUser {
    private final String name;
    private final String email;
    private final String channelName;
    private final int subscribers;
    private final String token;

    public GoogleUser(String name, String email, String channelName, int subscribers, String token) {
        this.name = name;
        this.email = email;
        this.channelName = channelName;
        this.subscribers = subscribers;
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getChannelName() {
        return channelName;
    }

    public int getSubscribers() {
        return subscribers;
    }

    public String getToken() {
        return token;
    }

    public String getInitials() {
        if (name == null || name.trim().isEmpty()) return "G";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    public String getFormattedSubscribers() {
        if (subscribers < 0) return "Indisponível";
        if (subscribers >= 1_000_000) {
            return String.format("%.1fM", subscribers / 1_000_000.0);
        } else if (subscribers >= 1_000) {
            return String.format("%.1fK", subscribers / 1_000.0);
        }
        return String.valueOf(subscribers);
    }
}
