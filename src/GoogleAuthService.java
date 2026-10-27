import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço de Autenticação Real do Google OAuth 2.0.
 * Realiza o fluxo completo oficial de autorização via navegador,
 * troca do Authorization Code por Access Token via endpoints da API do Google,
 * e consulta os dados reais do perfil (UserInfo) e do YouTube (YouTube Data API v3).
 */
public class GoogleAuthService {

    public interface AuthCallback {
        void onSuccess(GoogleUser user);
        void onError(String message);
    }

    private static final int OAUTH_PORT = 8989;
    private static final String REDIRECT_URI = "http://127.0.0.1:" + OAUTH_PORT + "/oauth2callback";
    private static HttpServer activeServer = null;

    /**
     * Inicia o fluxo oficial e real de autenticação com o Google OAuth 2.0.
     */
    public static void startRealGoogleOAuth(String clientId, String clientSecret, AuthCallback callback) {
        new Thread(() -> {
            try {
                stopServer();
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                new SecureRandom().nextBytes(random);
                String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

                // 1. Inicia o servidor HTTP local para receber o redirect do Google
                activeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", OAUTH_PORT), 0);
                activeServer.createContext("/oauth2callback", new GoogleOAuthCallbackHandler(clientId, clientSecret, callback, state, verifier));
                activeServer.setExecutor(null);
                activeServer.start();

                // 2. Constrói a URL oficial de consentimento do Google
                String scopes = "openid profile email https://www.googleapis.com/auth/youtube.readonly";
                String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                        "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                        "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                        "&response_type=code" +
                        "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8) +
                        "&code_challenge=" + URLEncoder.encode(challenge, StandardCharsets.UTF_8) +
                        "&code_challenge_method=S256" +
                        "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8) +
                        "&access_type=offline" +
                        "&prompt=consent";

                // 3. Abre o navegador padrão do sistema
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(authUrl));
                } else {
                    stopServer();
                    SwingUtilities.invokeLater(() -> callback.onError("Não foi possível abrir o navegador padrão do sistema."));
                }
            } catch (Exception ex) {
                stopServer();
                SwingUtilities.invokeLater(() -> callback.onError("Falha ao iniciar autenticação real: " + ex.getMessage()));
            }
        }).start();
    }

    public static void stopServer() {
        if (activeServer != null) {
            try {
                activeServer.stop(0);
                activeServer = null;
            } catch (Exception ignored) {}
        }
    }

    /** Carrega os uploads reais do canal associado ao token OAuth atual. */
    public static List<Video> getMyVideos(String accessToken) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        String channel = youtubeGet(client, "channels?part=contentDetails&mine=true", accessToken);
        String uploads = jsonField(channel, "uploads");
        if (uploads == null) throw new IOException("Não foi encontrado um canal do YouTube na conta autenticada.");
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        do {
            String playlistUrl = "playlistItems?part=snippet,contentDetails&maxResults=50&playlistId=" + URLEncoder.encode(uploads, StandardCharsets.UTF_8)
                    + (pageToken == null ? "" : "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
            String playlist = youtubeGet(client, playlistUrl, accessToken);
            Matcher idsMatcher = Pattern.compile("\\\"videoId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(playlist);
            while (idsMatcher.find()) ids.add(idsMatcher.group(1));
            pageToken = jsonField(playlist, "nextPageToken");
        } while (pageToken != null && !pageToken.isEmpty());
        List<Video> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 50) {
            String idList = String.join(",", ids.subList(start, Math.min(start + 50, ids.size())));
            String details = youtubeGet(client, "videos?part=snippet,contentDetails,statistics,status&id=" + URLEncoder.encode(idList, StandardCharsets.UTF_8), accessToken);
            Matcher object = Pattern.compile("\\{\\s*\\\"kind\\\"\\s*:\\s*\\\"youtube#video\\\".*?(?=,\\s*\\\"kind\\\"|\\s*]\\s*})", Pattern.DOTALL).matcher(details);
            while (object.find()) {
                String item = object.group();
                String id = jsonField(item, "id");
                String title = nestedField(item, "snippet", "title");
                if (id == null || title == null) continue;
                String privacy = nestedField(item, "status", "privacyStatus");
                String status = "public".equals(privacy) ? "Publicado" : "private".equals(privacy) ? "Privado" : "unlisted".equals(privacy) ? "Não Listado" : "Desconhecido";
                result.add(new Video(id, title, "YouTube", value(nestedField(item, "contentDetails", "duration")),
                    number(nestedField(item, "statistics", "viewCount")), number(nestedField(item, "statistics", "likeCount")), status,
                    value(nestedField(item, "snippet", "publishedAt")), value(nestedField(item, "snippet", "description")),
                    "https://www.youtube.com/watch?v=" + id));
            }
        }
        return result;
    }

    private static String youtubeGet(HttpClient client, String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://www.googleapis.com/youtube/v3/" + path))
                .timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("YouTube Data API: HTTP " + response.statusCode() + ". Confira se a API está habilitada e se o escopo youtube.readonly foi concedido.");
        return response.body();
    }
    private static String jsonField(String json, String key) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\"\\\\])*)\\\"").matcher(json);
        if (!m.find()) return null;
        return m.group(1).replace("\\\\\"", "\"").replace("\\\\n", "\n").replace("\\\\r", "\r").replace("\\\\t", "\t").replace("\\\\\\\\", "\\\\");
    }
    private static String nestedField(String json, String parent, String key) {
        Matcher p = Pattern.compile("\\\"" + Pattern.quote(parent) + "\\\"\\s*:\\s*\\{").matcher(json);
        if (!p.find()) return null;
        int start = p.end(), depth = 1;
        boolean quoted = false, escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; continue; }
            if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return jsonField(json.substring(start, i), key);
        }
        return null;
    }
    private static String value(String s) { return s == null ? "" : s; }
    private static long number(String s) { try { return s == null ? 0 : Long.parseLong(s); } catch (NumberFormatException e) { return 0; } }

    /**
     * Manipulador do retorno HTTP oficial do Google (/oauth2callback).
     */
    private static class GoogleOAuthCallbackHandler implements HttpHandler {
        private final String clientId;
        private final String clientSecret;
        private final AuthCallback callback;
        private final String expectedState;
        private final String codeVerifier;

        public GoogleOAuthCallbackHandler(String clientId, String clientSecret, AuthCallback callback, String expectedState, String codeVerifier) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.callback = callback;
            this.expectedState = expectedState;
            this.codeVerifier = codeVerifier;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);

            String code = params.get("code");
            String error = params.get("error");

            if (!expectedState.equals(params.get("state"))) {
                enviarRespostaHtml(exchange, 400, "Resposta inválida", "A verificação de segurança OAuth falhou. Inicie o login novamente.");
                stopServer();
                SwingUtilities.invokeLater(() -> callback.onError("Falha na validação state do OAuth."));
                return;
            }

            if (error != null) {
                enviarRespostaHtml(exchange, 400, "Acesso Negado", "A autorização foi cancelada no Google (" + error + ").");
                stopServer();
                SwingUtilities.invokeLater(() -> callback.onError("Autorização cancelada no Google: " + error));
                return;
            }

            if (code == null || code.trim().isEmpty()) {
                enviarRespostaHtml(exchange, 400, "Código Ausente", "O código de autorização não foi recebido do Google.");
                stopServer();
                SwingUtilities.invokeLater(() -> callback.onError("Código de autorização não encontrado."));
                return;
            }

            // Exibe página de sucesso no navegador imediatamente
            enviarRespostaHtml(exchange, 200, "Código recebido",
                    "O Google retornou o código de autorização. O aplicativo está validando sua conta; você já pode fechar esta janela.");
            stopServer();

            // Processa a troca do código pelo token real em segundo plano
            new Thread(() -> {
                try {
                    GoogleUser realUser = trocarCodigoPorUsuarioReal(code, clientId, clientSecret, codeVerifier);
                    SwingUtilities.invokeLater(() -> callback.onSuccess(realUser));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> callback.onError("Erro ao obter dados reais do Google: " + ex.getMessage()));
                }
            }).start();
        }

        private GoogleUser trocarCodigoPorUsuarioReal(String code, String clientId, String clientSecret, String codeVerifier) throws Exception {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // 1. POST para trocar Authorization Code por Access Token
            String tokenUrl = "https://oauth2.googleapis.com/token";
            String formBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                    "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&grant_type=authorization_code";
            formBody += "&code_verifier=" + URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            if (tokenResponse.statusCode() != 200) {
                String errorMsg = extractJsonString(tokenResponse.body(), "error_description");
                if (errorMsg == null) errorMsg = "HTTP " + tokenResponse.statusCode() + ": " + tokenResponse.body();
                throw new IOException("Falha no token endpoint do Google: " + errorMsg);
            }

            String accessToken = extractJsonString(tokenResponse.body(), "access_token");
            if (accessToken == null) {
                throw new IOException("Access Token não retornado pelo Google.");
            }

            // 2. GET para consultar o Perfil do Usuário Real (UserInfo)
            HttpRequest userInfoRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> userResponse = client.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
            String userJson = userResponse.body();

            if (userResponse.statusCode() != 200) throw new IOException("Falha ao consultar perfil Google: HTTP " + userResponse.statusCode());
            String realName = extractJsonString(userJson, "name");
            String realEmail = extractJsonString(userJson, "email");
            if (realName == null || realEmail == null) throw new IOException("O Google não retornou nome e e-mail da conta.");

            // 3. GET para consultar Canal do YouTube Real (YouTube Data API v3)
            String channelTitle;
            int subscribers;
            {
                HttpRequest ytRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true"))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();

                HttpResponse<String> ytResponse = client.send(ytRequest, HttpResponse.BodyHandlers.ofString());
                if (ytResponse.statusCode() != 200) throw new IOException("Falha na YouTube Data API (HTTP " + ytResponse.statusCode() + "). Habilite a API no Google Cloud e confira o escopo youtube.readonly.");
                String ytJson = ytResponse.body();
                channelTitle = extractNestedJsonString(ytJson, "snippet", "title");
                String subs = extractNestedJsonString(ytJson, "statistics", "subscriberCount");
                if (channelTitle == null) throw new IOException("A conta Google não possui um canal do YouTube acessível.");
                try { subscribers = Integer.parseInt(subs); } catch (Exception ignored) { subscribers = -1; }
            }

            return new GoogleUser(realName, realEmail, channelTitle, subscribers, accessToken);
        }

        private void enviarRespostaHtml(HttpExchange exchange, int statusCode, String titulo, String mensagem) throws IOException {
            String html = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">" +
                    "<title>Videos Management - Autenticação</title><style>" +
                    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #161922; color: #fff; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }" +
                    ".card { background: #222733; border: 1px solid #333d4f; border-radius: 12px; padding: 40px; text-align: center; max-width: 440px; box-shadow: 0 12px 32px rgba(0,0,0,0.5); }" +
                    ".icon { width: 64px; height: 64px; line-height: 64px; border-radius: 50%; background: #34A853; font-size: 34px; margin: 0 auto 20px; font-weight: bold; }" +
                    "h2 { margin: 0 0 12px; font-size: 22px; }" +
                    "p { color: #9da8be; font-size: 14px; line-height: 1.6; margin-bottom: 24px; }" +
                    ".btn { background: #4285F4; color: white; padding: 10px 24px; border-radius: 6px; text-decoration: none; font-weight: bold; font-size: 14px; display: inline-block; }" +
                    "</style></head><body><div class=\"card\">" +
                    "<div class=\"icon\">" + (statusCode == 200 ? "✓" : "!") + "</div>" +
                    "<h2>" + titulo + "</h2>" +
                    "<p>" + mensagem + "</p>" +
                    "<a class=\"btn\" href=\"javascript:window.close()\">Fechar Aba</a>" +
                    "</div></body></html>";

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private Map<String, String> parseQuery(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null || query.isEmpty()) return result;
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    try {
                        String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                        result.put(key, value);
                    } catch (Exception ignored) {}
                }
            }
            return result;
        }

        private String extractJsonString(String json, String key) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1).replace("\\\"", "\"");
            }
            return null;
        }

        private String extractNestedJsonString(String json, String parent, String key) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(parent) + "\"\\s*:\\s*\\{[^}]*\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?([^\",\\}]*)\"?");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            return null;
        }
    }
}
