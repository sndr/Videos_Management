import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Ponto de entrada principal da aplicação Videos Management.
 * Inicializa o tema FlatDarkLaf e abre a tela de entrada. A tela oferece
 * login Google, retomada de sessão salva ou acesso convidado para desenvolvimento.
 */
public class Main {
    public static void main(String[] args) {
        // Inicializa o tema escuro FlatLaf
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Falha ao inicializar Look and Feel FlatDarkLaf: " + ex.getMessage());
        }

        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}






























































