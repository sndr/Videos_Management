import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gerenciador de Credenciais Reais do Google Cloud OAuth 2.0.
 * Permite persistir o Client ID e Client Secret em arquivo local seguro
 * ou importar diretamente o arquivo client_secret_*.json do Google Cloud Console.
 */
public class GoogleCredentialsManager {

    private static final String CREDENTIALS_FILE = ".google_oauth_credentials.properties";
    private static final Path CREDENTIALS_PATH = Paths.get(System.getProperty("user.home"), CREDENTIALS_FILE);

    private static String cachedClientId = null;
    private static String cachedClientSecret = null;

    static {
        loadCredentials();
    }

    public static boolean isConfigured() {
        return cachedClientId != null && !cachedClientId.trim().isEmpty()
                && cachedClientSecret != null && !cachedClientSecret.trim().isEmpty();
    }

    public static String getClientId() {
        return cachedClientId != null ? cachedClientId.trim() : "";
    }

    public static String getClientSecret() {
        return cachedClientSecret != null ? cachedClientSecret.trim() : "";
    }

    public static void saveCredentials(String clientId, String clientSecret) throws IOException {
        cachedClientId = clientId != null ? clientId.trim() : "";
        cachedClientSecret = clientSecret != null ? clientSecret.trim() : "";

        Properties props = new Properties();
        props.setProperty("google.client_id", cachedClientId);
        props.setProperty("google.client_secret", cachedClientSecret);

        try (OutputStream out = Files.newOutputStream(CREDENTIALS_PATH)) {
            props.store(out, "Videos Management - Real Google Cloud OAuth Credentials");
        }
        protegerArquivoLocal();
    }

    public static void loadCredentials() {
        if (!Files.exists(CREDENTIALS_PATH)) {
            return;
        }

        try (InputStream in = Files.newInputStream(CREDENTIALS_PATH)) {
            Properties props = new Properties();
            props.load(in);
            cachedClientId = props.getProperty("google.client_id");
            cachedClientSecret = props.getProperty("google.client_secret");
        } catch (Exception ex) {
            System.err.println("Aviso: Falha ao carregar credenciais do Google: " + ex.getMessage());
        }
    }

    /**
     * Importa o arquivo client_secret_*.json baixado diretamente do Google Cloud Console.
     */
    public static boolean importFromJsonFile(File jsonFile) throws IOException {
        String content = Files.readString(jsonFile.toPath());

        String clientId = extractRegex(content, "client_id");
        String clientSecret = extractRegex(content, "client_secret");

        if (clientId != null && clientSecret != null) {
            saveCredentials(clientId, clientSecret);
            return true;
        }
        return false;
    }

    public static void clearCredentials() {
        cachedClientId = null;
        cachedClientSecret = null;
        try {
            Files.deleteIfExists(CREDENTIALS_PATH);
        } catch (IOException ignored) {}
    }

    public static String getCredentialsFilePath() {
        return CREDENTIALS_PATH.toAbsolutePath().toString();
    }

    private static String extractRegex(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void protegerArquivoLocal() {
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(CREDENTIALS_PATH, AclFileAttributeView.class);
            if (aclView == null) return;
            AclEntry ownerOnly = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(aclView.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            aclView.setAcl(Collections.singletonList(ownerOnly));
        } catch (Exception ignored) { /* Alguns sistemas de arquivos não oferecem ACLs. */ }
    }
}
