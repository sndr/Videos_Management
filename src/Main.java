import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Ponto de entrada principal da aplicação Videos Management.
 * Inicializa o tema FlatDarkLaf e verifica a existência de cookies/tokens de sessão:
 * - Se houver sessão ativa válida: entra diretamente no Dashboard.
 * - Se não houver sessão/token: exibe a tela de login ampliada e configurável.
 */
public class Main {
    public static void main(String[] args) {
        // Inicializa o tema escuro FlatLaf
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Falha ao inicializar Look and Feel FlatDarkLaf: " + ex.getMessage());
        }

        // Verifica token/cookie de sessão persistido
        GoogleUser savedUser = SessionManager.loadSession();

        SwingUtilities.invokeLater(() -> {
            if (savedUser != null) {
                // Sessão existente e válida: abre o Dashboard diretamente
                DashboardFrame dashboard = new DashboardFrame(savedUser);
                dashboard.setVisible(true);
            } else {
                // Sem sessão ou token expirado: abre a janela de Login
                LoginFrame login = new LoginFrame();
                login.setVisible(true);
            }
        });
    }
}






























































