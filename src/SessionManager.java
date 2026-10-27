import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.*;

/**
 * Gerenciador de Sessão, Cookies e Tokens de Autenticação.
 * Persiste localmente os dados da sessão para manter o usuário autenticado.
 */
public class SessionManager {

    private static final String FILE_NAME = ".videos_management_session.properties";
    private static final Path SESSION_PATH = Paths.get(System.getProperty("user.home"), FILE_NAME);

    /**
     * Salva a sessão do usuário com token criptográfico e timestamp de expiração (cookie de sessão).
     */
    public static void saveSession(GoogleUser user, boolean rememberMe) {
        if (!rememberMe) {
            clearSession();
            return;
        }

        Properties props = new Properties();
        String accessToken = user.getToken();
        if (accessToken == null || accessToken.trim().isEmpty() || accessToken.startsWith("token_") || accessToken.startsWith("session_")) {
            clearSession();
            return;
        }
        // Access tokens do Google duram cerca de uma hora; sem refresh token, exigir novo login ao expirar.
        long expiresAt = System.currentTimeMillis() + (55L * 60 * 1000);

        props.setProperty("auth.token", accessToken);
        props.setProperty("auth.expiresAt", String.valueOf(expiresAt));
        props.setProperty("user.name", user.getName() != null ? user.getName() : "");
        props.setProperty("user.email", user.getEmail() != null ? user.getEmail() : "");
        props.setProperty("user.channel", user.getChannelName() != null ? user.getChannelName() : "");
        props.setProperty("user.subscribers", String.valueOf(user.getSubscribers()));

        try (OutputStream out = Files.newOutputStream(SESSION_PATH)) {
            props.store(out, "Videos Management - Active User Session & Token");
            out.flush();
            protegerArquivoLocal();
        } catch (IOException ex) {
            System.err.println("Aviso: Não foi possível persistir a sessão local: " + ex.getMessage());
        }
    }

    /**
     * Carrega a sessão salva caso o token seja válido e não tenha expirado.
     * Retorna o GoogleUser autenticado ou null se não houver sessão válida.
     */
    public static GoogleUser loadSession() {
        if (!Files.exists(SESSION_PATH)) {
            return null;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(SESSION_PATH)) {
            props.load(in);

            String token = props.getProperty("auth.token");
            String expiresStr = props.getProperty("auth.expiresAt");
            String name = props.getProperty("user.name");
            String email = props.getProperty("user.email");
            String channel = props.getProperty("user.channel");
            String subsStr = props.getProperty("user.subscribers", "-1");

            if (token == null || token.startsWith("token_") || token.startsWith("session_") || expiresStr == null || email == null || email.trim().isEmpty()) {
                clearSession();
                return null;
            }

            long expiresAt = Long.parseLong(expiresStr);
            if (System.currentTimeMillis() > expiresAt) {
                // Token expirou
                clearSession();
                return null;
            }

            int subs = -1;
            try {
                subs = Integer.parseInt(subsStr);
            } catch (NumberFormatException ignored) {}

            return new GoogleUser(name, email, channel, subs, token);
        } catch (Exception ex) {
            clearSession();
            return null;
        }
    }

    /**
     * Remove o arquivo de token de sessão (Logout).
     */
    public static void clearSession() {
        try {
            Files.deleteIfExists(SESSION_PATH);
        } catch (IOException ignored) {}
    }

    /**
     * Verifica se existe um token de sessão salvo e válido.
     */
    public static boolean hasValidSession() {
        return loadSession() != null;
    }

    public static String getSessionFilePath() {
        return SESSION_PATH.toAbsolutePath().toString();
    }

    private static void protegerArquivoLocal() {
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(SESSION_PATH, AclFileAttributeView.class);
            if (aclView == null) return;
            AclEntry ownerOnly = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(aclView.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            aclView.setAcl(Collections.singletonList(ownerOnly));
        } catch (Exception ignored) { /* O sistema de arquivos pode não oferecer ACLs POSIX/Windows. */ }
    }
}
