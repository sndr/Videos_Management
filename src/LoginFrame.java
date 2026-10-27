import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

/**
 * Janela moderna de Login com Autenticação Google REAL (OAuth 2.0).
 * Totalmente redimensionável com o mouse e com layout responsivo.
 * Conta com gaps confortáveis entre todos os rótulos, nomes e botões.
 */
public class LoginFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private JLabel lblStatus;
    private JProgressBar progressBar;
    private JCheckBox chkSalvarSessao;
    private JPanel savedSessionCard;
    private JButton btnGoogleBrowser;

    public LoginFrame() {
        super("Videos Management Studio - Login");
        initUI();
        verificarSessaoExistente();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Janela redimensionável com o mouse e com tamanho mínimo seguro
        setSize(1120, 760);
        setMinimumSize(new Dimension(560, 540));
        setResizable(true);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Divisão principal: Esquerda (Apresentação / Hero) | Direita (Autenticação)
        JPanel mainSplit = new JPanel(new BorderLayout());
        mainSplit.setBackground(new Color(13, 18, 30));
        JComponent presentation = criarPainelApresentacao();
        JComponent authentication = criarPainelAutenticacao();
        mainSplit.add(presentation, BorderLayout.WEST);
        mainSplit.add(authentication, BorderLayout.CENTER);

        add(mainSplit, BorderLayout.CENTER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean compact;
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                boolean shouldCollapse = getWidth() < 900;
                if (shouldCollapse != compact) {
                    compact = shouldCollapse;
                    if (compact) mainSplit.remove(presentation);
                    else mainSplit.add(presentation, BorderLayout.WEST);
                    mainSplit.revalidate();
                }
                if (!compact) {
                    Dimension target = new Dimension(Math.max(320, (int) (getWidth() * 0.38)), Math.max(1, getHeight()));
                    if (!target.equals(presentation.getPreferredSize())) {
                        presentation.setPreferredSize(target);
                        mainSplit.revalidate();
                    }
                }
            }
        });
    }

    // ==========================================
    // PAINEL ESQUERDO: APRESENTAÇÃO E RECURSOS
    // ==========================================
    private JComponent criarPainelApresentacao() {
        JPanel heroPanel = new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(12, 19, 35),
                        getWidth(), getHeight(), new Color(24, 42, 72)
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(new Color(51, 78, 119));
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g2.setColor(new Color(54, 118, 202, 36));
                g2.fillOval(getWidth() - 240, 90, 320, 320);
                g2.setColor(new Color(70, 190, 190, 24));
                g2.fillOval(getWidth() - 170, 155, 180, 180);
                g2.dispose();
            }
        };

        heroPanel.setPreferredSize(new Dimension(420, 700));
        heroPanel.setLayout(new BoxLayout(heroPanel, BoxLayout.Y_AXIS));
        heroPanel.setBorder(new EmptyBorder(48, 42, 40, 42));

        // 1. Badge superior
        JLabel lblTag = new JLabel("YOUTUBE CREATOR STUDIO");
        lblTag.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTag.setForeground(new Color(97, 175, 239));
        lblTag.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroPanel.add(lblTag);
        heroPanel.add(Box.createRigidArea(new Dimension(0, 16)));

        // 2. Logo e Nome do Produto com gaps adequados
        JLabel lblLogo = new JLabel("🎬");
        lblLogo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 42));
        lblLogo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblTitle = new JLabel("Videos Management");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 29));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSubtitle = new JLabel("<html><div style='width:260px'>Acesse seu canal e seus vídeos do YouTube em um só lugar.</div></html>");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblSubtitle.setForeground(new Color(185, 197, 216));
        lblSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        heroPanel.add(lblLogo);
        heroPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        heroPanel.add(lblTitle);
        heroPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        heroPanel.add(lblSubtitle);
        heroPanel.add(Box.createRigidArea(new Dimension(0, 38)));

        // 3. Recursos em Destaque com respiro generoso
        heroPanel.add(criarItemDestaque("⚡", "Autenticação Real Google OAuth 2.0", "Conexão oficial e direta com accounts.google.com."));
        heroPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        heroPanel.add(criarItemDestaque("📊", "YouTube Data API v3 Integrada", "Consulta inscritos, dados do canal e métricas do catálogo."));
        heroPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        heroPanel.add(criarItemDestaque("📁", "Gestão de Publicações & Uploads", "Organize status, links, durações e arquivos em lote."));

        heroPanel.add(Box.createVerticalGlue());

        // 4. Rodapé
        JLabel lblFooterEndorsement = new JLabel("<html><span style='color: #61afef;'>●</span> Conexão oficial com sua conta Google</html>");
        lblFooterEndorsement.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblFooterEndorsement.setForeground(new Color(140, 150, 165));
        lblFooterEndorsement.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblVersion = new JLabel("Seus dados vêm do YouTube Data API");
        lblVersion.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblVersion.setForeground(new Color(90, 100, 115));
        lblVersion.setAlignmentX(Component.LEFT_ALIGNMENT);

        heroPanel.add(lblFooterEndorsement);
        heroPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        heroPanel.add(lblVersion);

        JScrollPane scrollLeft = new JScrollPane(heroPanel);
        scrollLeft.setBorder(null);
        scrollLeft.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollLeft.getVerticalScrollBar().setUnitIncrement(14);
        return scrollLeft;
    }

    private JPanel criarItemDestaque(String icone, String titulo, String descricao) {
        JPanel item = new JPanel(new BorderLayout(14, 0));
        item.setOpaque(false);
        item.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblIcon = new JLabel(icone);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        lblIcon.setVerticalAlignment(SwingConstants.TOP);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel lblT = new JLabel(titulo);
        lblT.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblT.setForeground(new Color(225, 232, 242));
        lblT.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblD = new JLabel("<html>" + descricao + "</html>");
        lblD.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblD.setForeground(new Color(140, 150, 165));
        lblD.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(lblT);
        textPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        textPanel.add(lblD);

        item.add(lblIcon, BorderLayout.WEST);
        item.add(textPanel, BorderLayout.CENTER);
        return item;
    }

    // ==========================================
    // PAINEL DIREITO: AUTENTICAÇÃO REAL COM O GOOGLE
    // ==========================================
    private JComponent criarPainelAutenticacao() {
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setBackground(new Color(13, 18, 30));
        rightWrapper.setBorder(new EmptyBorder(28, 28, 28, 28));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel authCard = new JPanel();
        authCard.setLayout(new BoxLayout(authCard, BoxLayout.Y_AXIS));
        authCard.setBackground(new Color(25, 33, 48));
        authCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 66, 89), 1, true),
                new EmptyBorder(36, 38, 36, 38)
        ));
        authCard.setMinimumSize(new Dimension(0, 0));
        authCard.setMaximumSize(new Dimension(540, Integer.MAX_VALUE));

        // Título e Boas-vindas com gap generoso
        JLabel lblWelcome = new JLabel("Conecte sua conta");
        lblWelcome.setFont(new Font("Segoe UI", Font.BOLD, 25));
        lblWelcome.setForeground(Color.WHITE);
        lblWelcome.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblWelcomeSub = new JLabel("Acesse os vídeos vinculados ao seu canal do YouTube.");
        lblWelcomeSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblWelcomeSub.setForeground(new Color(164, 176, 198));
        lblWelcomeSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        authCard.add(lblWelcome);
        authCard.add(Box.createRigidArea(new Dimension(0, 8)));
        authCard.add(lblWelcomeSub);
        authCard.add(Box.createRigidArea(new Dimension(0, 22)));

        // Card de Sessão Anterior (se existir)
        savedSessionCard = new JPanel(new BorderLayout(12, 0));
        savedSessionCard.setBackground(new Color(28, 45, 69));
        savedSessionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(67, 135, 230), 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));
        savedSessionCard.setMaximumSize(new Dimension(400, 56));
        savedSessionCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        savedSessionCard.setVisible(false);
        authCard.add(savedSessionCard);
        authCard.add(Box.createRigidArea(new Dimension(0, 16)));

        // Botão Oficial Google (Design limpo, alto contraste e sombra suave)
        btnGoogleBrowser = new JButton("  Continuar com o Google", new GoogleIcon(22));
        btnGoogleBrowser.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnGoogleBrowser.setForeground(new Color(32, 33, 36));
        btnGoogleBrowser.setBackground(Color.WHITE);
        btnGoogleBrowser.setFocusPainted(false);
        btnGoogleBrowser.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnGoogleBrowser.setMaximumSize(new Dimension(440, 50));
        btnGoogleBrowser.setPreferredSize(new Dimension(400, 50));
        btnGoogleBrowser.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnGoogleBrowser.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 226, 235), 1, true),
                new EmptyBorder(10, 16, 10, 16)
        ));
        btnGoogleBrowser.addActionListener(e -> iniciarLoginGoogleReal());

        btnGoogleBrowser.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnGoogleBrowser.setBackground(new Color(245, 247, 250));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btnGoogleBrowser.setBackground(Color.WHITE);
            }
        });

        authCard.add(btnGoogleBrowser);
        authCard.add(Box.createRigidArea(new Dimension(0, 12)));

        JButton btnGuest = new JButton("Entrar como convidado · desenvolvimento");
        btnGuest.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnGuest.setForeground(new Color(188, 202, 222));
        btnGuest.setBackground(new Color(31, 42, 60));
        btnGuest.setFocusPainted(false);
        btnGuest.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnGuest.setMaximumSize(new Dimension(440, 44));
        btnGuest.setPreferredSize(new Dimension(400, 44));
        btnGuest.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnGuest.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(66, 83, 108), 1, true),
                new EmptyBorder(9, 14, 9, 14)
        ));
        btnGuest.addActionListener(e -> efetuarLogin(GoogleUser.createGuest()));
        authCard.add(btnGuest);
        authCard.add(Box.createRigidArea(new Dimension(0, 6)));

        JLabel lblGuestHint = new JLabel("<html><div style='text-align:center'>Acesso local sem Google; vídeos reais não serão carregados.</div></html>");
        lblGuestHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblGuestHint.setForeground(new Color(139, 153, 174));
        lblGuestHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        authCard.add(lblGuestHint);
        authCard.add(Box.createRigidArea(new Dimension(0, 10)));

        // Link para Configurar ou Alterar Credenciais do Google Cloud com gap adequado
        JButton btnConfigOAuth = new JButton("⚙️ Configurar Credenciais do Google Cloud Console");
        btnConfigOAuth.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnConfigOAuth.setForeground(new Color(97, 175, 239));
        btnConfigOAuth.setContentAreaFilled(false);
        btnConfigOAuth.setBorderPainted(false);
        btnConfigOAuth.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnConfigOAuth.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnConfigOAuth.addActionListener(e -> abrirDialogConfiguracaoOAuth(false));

        authCard.add(btnConfigOAuth);
        authCard.add(Box.createRigidArea(new Dimension(0, 18)));

        // Checkbox de sessão persistente com gap adequado
        chkSalvarSessao = new JCheckBox("Lembrar login Google (salvar token localmente)", true);
        chkSalvarSessao.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkSalvarSessao.setForeground(new Color(175, 185, 200));
        chkSalvarSessao.setOpaque(false);
        chkSalvarSessao.setFocusPainted(false);
        chkSalvarSessao.setAlignmentX(Component.CENTER_ALIGNMENT);
        authCard.add(chkSalvarSessao);
        authCard.add(Box.createRigidArea(new Dimension(0, 14)));

        // Barra de progresso e Status
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setMaximumSize(new Dimension(440, 4));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblStatus = new JLabel("Pronto para autenticar.");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblStatus.setForeground(new Color(130, 140, 155));
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        authCard.add(progressBar);
        authCard.add(Box.createRigidArea(new Dimension(0, 8)));
        authCard.add(lblStatus);

        rightWrapper.add(authCard, gbc);

        JScrollPane scroll = new JScrollPane(rightWrapper);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return scroll;
    }

    private JPanel criarSeparador(String texto) {
        JPanel sep = new JPanel(new GridBagLayout());
        sep.setOpaque(false);
        sep.setMaximumSize(new Dimension(400, 20));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1.0;

        JSeparator s1 = new JSeparator();
        s1.setForeground(new Color(55, 62, 75));
        sep.add(s1, g);

        JLabel label = new JLabel("  " + texto + "  ");
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(new Color(120, 130, 145));
        g.weightx = 0;
        sep.add(label, g);

        JSeparator s2 = new JSeparator();
        s2.setForeground(new Color(55, 62, 75));
        g.weightx = 1.0;
        sep.add(s2, g);

        return sep;
    }

    private JPanel criarCardUsuario(GoogleUser user) {
        JPanel card = new JPanel(new BorderLayout(14, 0));
        card.setMaximumSize(new Dimension(400, 50));
        card.setPreferredSize(new Dimension(400, 50));
        card.setBackground(new Color(42, 47, 58));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 62, 76), 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Avatar com iniciais
        JPanel avatar = new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(66, 133, 244));
                g2.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(user.getInitials())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(user.getInitials(), tx, ty);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(34, 34));
        avatar.setOpaque(false);

        // Informações com gap agradável de 4px entre nome e email
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel lblName = new JLabel(user.getName() + " (" + user.getChannelName() + ")");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(Color.WHITE);
        lblName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblEmail = new JLabel(user.getEmail() + " • " + user.getFormattedSubscribers() + " inscritos");
        lblEmail.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblEmail.setForeground(new Color(155, 165, 180));
        lblEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        info.add(lblName);
        info.add(Box.createRigidArea(new Dimension(0, 4)));
        info.add(lblEmail);

        JLabel lblArrow = new JLabel("➔");
        lblArrow.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblArrow.setForeground(new Color(110, 120, 135));

        card.add(avatar, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);
        card.add(lblArrow, BorderLayout.EAST);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(52, 59, 74));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(66, 133, 244), 1, true),
                        new EmptyBorder(8, 14, 8, 14)
                ));
                lblArrow.setForeground(new Color(97, 175, 239));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(new Color(42, 47, 58));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(55, 62, 76), 1, true),
                        new EmptyBorder(8, 14, 8, 14)
                ));
                lblArrow.setForeground(new Color(110, 120, 135));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                efetuarLogin(user);
            }
        });

        return card;
    }

    private void verificarSessaoExistente() {
        GoogleUser savedUser = SessionManager.loadSession();
        if (savedUser != null) {
            savedSessionCard.removeAll();

            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);

            JLabel lblSavedTitle = new JLabel("🔑 Sessão Ativa: " + savedUser.getName());
            lblSavedTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblSavedTitle.setForeground(new Color(97, 175, 239));
            lblSavedTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel lblSavedSub = new JLabel(savedUser.getEmail() + " • Token válido");
            lblSavedSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            lblSavedSub.setForeground(new Color(152, 195, 121));
            lblSavedSub.setAlignmentX(Component.LEFT_ALIGNMENT);

            left.add(lblSavedTitle);
            left.add(Box.createRigidArea(new Dimension(0, 3)));
            left.add(lblSavedSub);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            right.setOpaque(false);

            JButton btnRestaurar = new JButton("Entrar");
            btnRestaurar.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnRestaurar.setBackground(new Color(66, 133, 244));
            btnRestaurar.setForeground(Color.WHITE);
            btnRestaurar.setFocusPainted(false);
            btnRestaurar.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnRestaurar.addActionListener(e -> abrirDashboard(savedUser));

            JButton btnLimpar = new JButton("Limpar");
            btnLimpar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            btnLimpar.setBackground(new Color(47, 52, 63));
            btnLimpar.setForeground(new Color(224, 108, 117));
            btnLimpar.setFocusPainted(false);
            btnLimpar.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnLimpar.addActionListener(e -> {
                SessionManager.clearSession();
                savedSessionCard.setVisible(false);
                lblStatus.setText("Sessão anterior removida com sucesso.");
                revalidate();
                repaint();
            });

            right.add(btnRestaurar);
            right.add(btnLimpar);

            savedSessionCard.add(left, BorderLayout.CENTER);
            savedSessionCard.add(right, BorderLayout.EAST);
            savedSessionCard.setVisible(true);

            lblStatus.setText("Sessão salva detectada. Clique em 'Entrar' ou faça novo login.");
            revalidate();
            repaint();
        }
    }

    /**
     * Inicia a autenticação REAL com o Google via accounts.google.com
     */
    private void iniciarLoginGoogleReal() {
        if (!GoogleCredentialsManager.isConfigured()) {
            abrirDialogConfiguracaoOAuth(true);
            return;
        }

        executarFluxoOAuthReal();
    }

    private void executarFluxoOAuthReal() {
        progressBar.setVisible(true);
        lblStatus.setText("Abrindo navegador oficial do Google (accounts.google.com)...");
        lblStatus.setForeground(new Color(97, 175, 239));

        String clientId = GoogleCredentialsManager.getClientId();
        String clientSecret = GoogleCredentialsManager.getClientSecret();

        GoogleAuthService.startRealGoogleOAuth(clientId, clientSecret, new GoogleAuthService.AuthCallback() {
            @Override
            public void onSuccess(GoogleUser user) {
                efetuarLogin(user);
            }

            @Override
            public void onError(String message) {
                progressBar.setVisible(false);
                lblStatus.setText("Erro no Google OAuth: " + message);
                lblStatus.setForeground(new Color(224, 108, 117));
            }
        });
    }

    /**
     * Modal redimensionável com o mouse para configuração de credenciais do Google Cloud
     */
    private void abrirDialogConfiguracaoOAuth(boolean autoExecutarAposSalvar) {
        JDialog dialog = new JDialog(this, "Credenciais do Google Cloud OAuth 2.0 Real", true);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int dialogWidth = Math.min(640, Math.max(360, screen.width - 64));
        int dialogHeight = Math.min(680, Math.max(480, screen.height - 96));
        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setMinimumSize(new Dimension(Math.min(420, dialogWidth), Math.min(480, dialogHeight)));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(new Color(23, 30, 43));
        root.setBorder(new EmptyBorder(26, 30, 24, 30));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel lblTit = new JLabel("Conectar ao Google Cloud");
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTit.setForeground(Color.WHITE);
        lblTit.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextArea lblDesc = new JTextArea("Configure uma vez um cliente OAuth do tipo Desktop app no Google Cloud e importe o JSON para ativar o login.");
        lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDesc.setForeground(new Color(174, 185, 202));
        lblDesc.setOpaque(false);
        lblDesc.setEditable(false);
        lblDesc.setFocusable(false);
        lblDesc.setLineWrap(true);
        lblDesc.setWrapStyleWord(true);
        lblDesc.setRows(2);
        lblDesc.setColumns(42);
        lblDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(lblTit);
        header.add(Box.createRigidArea(new Dimension(0, 8)));
        header.add(lblDesc);
        root.add(header, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(true);
        fields.setBackground(new Color(23, 30, 43));
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 0;
        fieldConstraints.gridwidth = 2;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
        fieldConstraints.insets = new Insets(0, 0, 14, 0);

        JButton btnImportar = new JButton("Importar credenciais JSON do Google Cloud");
        btnImportar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnImportar.setBackground(new Color(38, 52, 73));
        btnImportar.setForeground(new Color(132, 190, 255));
        btnImportar.setFocusPainted(false);
        btnImportar.setPreferredSize(new Dimension(0, 42));
        btnImportar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtClientId = new JTextField(GoogleCredentialsManager.getClientId());
        txtClientId.setPreferredSize(new Dimension(300, 40));
        txtClientId.putClientProperty("JTextField.placeholderText", "Ex.: 123456789.apps.googleusercontent.com");

        JPasswordField txtClientSecret = new JPasswordField(GoogleCredentialsManager.getClientSecret());
        txtClientSecret.setPreferredSize(new Dimension(300, 40));
        txtClientSecret.putClientProperty("JTextField.placeholderText", "Client Secret");

        JLabel lblCId = new JLabel("Client ID");
        JLabel lblCSecret = new JLabel("Client Secret");
        for (JLabel label : new JLabel[]{lblCId, lblCSecret}) {
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));
            label.setForeground(new Color(198, 208, 224));
        }

        JTextArea redirectInfo = new JTextArea("Aplicativo para computador (Desktop app)\nO retorno usa 127.0.0.1 em uma porta local temporária.");
        redirectInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        redirectInfo.setForeground(new Color(156, 181, 215));
        redirectInfo.setOpaque(true);
        redirectInfo.setBackground(new Color(29, 40, 57));
        redirectInfo.setBorder(new EmptyBorder(12, 14, 12, 14));
        redirectInfo.setEditable(false);
        redirectInfo.setFocusable(false);
        redirectInfo.setLineWrap(true);
        redirectInfo.setWrapStyleWord(true);
        redirectInfo.setRows(2);

        fieldConstraints.gridy = 0;
        fields.add(btnImportar, fieldConstraints);
        fieldConstraints.gridy = 1;
        fields.add(lblCId, fieldConstraints);
        fieldConstraints.gridy = 2;
        fields.add(txtClientId, fieldConstraints);
        fieldConstraints.gridy = 3;
        fields.add(lblCSecret, fieldConstraints);
        fieldConstraints.gridy = 4;
        fields.add(txtClientSecret, fieldConstraints);
        fieldConstraints.gridy = 5;
        fieldConstraints.insets = new Insets(8, 0, 0, 0);
        fields.add(redirectInfo, fieldConstraints);
        JScrollPane fieldsScroll = new JScrollPane(fields);
        fieldsScroll.setBorder(null);
        fieldsScroll.setBackground(new Color(23, 30, 43));
        fieldsScroll.getViewport().setBackground(new Color(23, 30, 43));
        fieldsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fieldsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        fieldsScroll.getVerticalScrollBar().setUnitIncrement(14);
        root.add(fieldsScroll, BorderLayout.CENTER);

        btnImportar.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Selecione o arquivo client_secret_*.json");
            int ret = chooser.showOpenDialog(dialog);
            if (ret == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                try {
                    boolean ok = GoogleCredentialsManager.importFromJsonFile(f);
                    if (ok) {
                        txtClientId.setText(GoogleCredentialsManager.getClientId());
                        txtClientSecret.setText(GoogleCredentialsManager.getClientSecret());
                        JOptionPane.showMessageDialog(dialog, "Credenciais importadas com sucesso!", "Importação concluída", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(dialog, "Não foi possível encontrar client_id e client_secret no arquivo.", "Aviso", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Erro ao ler arquivo: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnRow.setOpaque(false);

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setPreferredSize(new Dimension(110, 40));
        btnCancelar.addActionListener(e -> dialog.dispose());

        JButton btnSalvar = new JButton("Salvar e Prosseguir");
        btnSalvar.setPreferredSize(new Dimension(180, 40));
        btnSalvar.setBackground(new Color(66, 133, 244));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSalvar.addActionListener(e -> {
            String cId = txtClientId.getText().trim();
            String cSec = new String(txtClientSecret.getPassword()).trim();

            if (cId.isEmpty() || cSec.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Client ID e Client Secret são obrigatórios.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                GoogleCredentialsManager.saveCredentials(cId, cSec);
                dialog.dispose();
                if (autoExecutarAposSalvar) {
                    executarFluxoOAuthReal();
                } else {
                    JOptionPane.showMessageDialog(this, "Credenciais salvas com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Falha ao salvar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRow.add(btnCancelar);
        btnRow.add(btnSalvar);
        root.add(btnRow, BorderLayout.SOUTH);
        dialog.add(root, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private void efetuarLogin(GoogleUser user) {
        progressBar.setVisible(true);
        if (user.isGuest()) {
            lblStatus.setText("Modo convidado de desenvolvimento. Abrindo o dashboard...");
            lblStatus.setForeground(new Color(245, 190, 92));
        } else {
            lblStatus.setText("✓ Autenticado com sucesso como " + user.getName() + "! Abrindo Dashboard...");
            lblStatus.setForeground(new Color(152, 195, 121));
        }

        boolean lembrar = chkSalvarSessao.isSelected();
        SessionManager.saveSession(user, lembrar);

        Timer timer = new Timer(500, e -> {
            ((Timer) e.getSource()).stop();
            abrirDashboard(user);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void abrirDashboard(GoogleUser user) {
        SwingUtilities.invokeLater(() -> {
            DashboardFrame dashboard = new DashboardFrame(user);
            dashboard.setVisible(true);
            dispose();
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("FlatLaf não pôde ser iniciado.");
        }

        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
