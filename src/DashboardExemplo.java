import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Ponto de compatibilidade para o arquivo original DashboardExemplo.
 * Inicia a aplicação completa através da janela de autenticação Google (LoginFrame)
 * e direciona para o Dashboard de gerenciamento de vídeos após a autenticação.
 */
public class DashboardExemplo {
    public static void main(String[] args) {
        // Aplica o tema FlatDarkLaf
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Falha ao iniciar o tema FlatDarkLaf: " + ex.getMessage());
        }

        // Inicia a aplicação abrindo a tela de autenticação Google
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

}
