import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Janela principal de Gerenciamento de Vídeos (Dashboard).
 * Totalmente redimensionável com o mouse e responsiva em todas as telas e diálogos modais.
 * Conta com respiros (gaps) generosos entre todos os nomes, rótulos e controles interativos.
 */
public class DashboardFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final GoogleUser currentUser;
    private final List<Video> videoList = new ArrayList<>();

    // Layout e Navegação
    private CardLayout cardLayout;
    private JPanel contentContainer;
    private JPanel analyticsPanel;

    // Métricas
    private JLabel lblTotalVideosValor;
    private JLabel lblTotalViewsValor;
    private JLabel lblTotalLikesValor;
    private JLabel lblVideosPublicadosValor;

    // Tabela e Filtros
    private JTable videoTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField txtPesquisa;
    private JComboBox<String> cbFiltroStatus;
    private JComboBox<String> cbFiltroCategoria;

    // Botões de Menu da Sidebar para controle de estado visual
    private JButton btnNavVideos;
    private JButton btnNavNovo;
    private JButton btnNavAnalytics;
    private JButton btnNavConfig;
    private JButton btnNavClips;

    public DashboardFrame(GoogleUser user) {
        super("Videos Management Studio - " + user.getName());
        this.currentUser = user;

        initUI();
        recalcularMetricas();
        if (!currentUser.isGuest()) carregarVideosDaConta();
    }

    private void inicializarDadosIniciais() {
        // Sem dados de demonstração: a lista vem exclusivamente da YouTube Data API.
    }

    private void carregarVideosDaConta() {
        new SwingWorker<List<Video>, Void>() {
            @Override protected List<Video> doInBackground() throws Exception { return GoogleAuthService.getMyVideos(currentUser.getToken()); }
            @Override protected void done() {
                try {
                    videoList.clear(); videoList.addAll(get()); atualizarTabela(); recalcularMetricas();
                    contentContainer.remove(analyticsPanel);
                    analyticsPanel = criarPainelAnalytics();
                    contentContainer.add(analyticsPanel, "ANALYTICS");
                    contentContainer.revalidate(); contentContainer.repaint();
                    if (videoList.isEmpty()) JOptionPane.showMessageDialog(DashboardFrame.this,
                            "A conta autenticada não possui vídeos na playlist de uploads.", "YouTube", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    videoList.clear(); atualizarTabela(); recalcularMetricas();
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(DashboardFrame.this, cause.getMessage(), "Falha ao carregar vídeos do YouTube", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void initUI() {
        // Janela principal 100% redimensionável com o mouse
        setSize(1180, 760);
        setMinimumSize(new Dimension(940, 580));
        setResizable(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. HEADER SUPERIOR COM GAPS REFINADOS
        add(criarHeader(), BorderLayout.NORTH);

        // 2. SIDEBAR COM GAPS E ALINHAMENTO RIGOROSO
        add(criarSidebar(), BorderLayout.WEST);

        // 3. CONTAINER PRINCIPAL (CARD LAYOUT COM SUPORTE A ROLAGEM)
        cardLayout = new CardLayout();
        contentContainer = new JPanel(cardLayout);
        contentContainer.setBackground(new Color(36, 40, 48));

        contentContainer.add(criarPainelGerenciarVideos(), "VIDEOS");
        contentContainer.add(criarPainelNovoVideo(), "NOVO_VIDEO");
        contentContainer.add(new ClipEditorPanel(this), "CLIP_EDITOR");
        analyticsPanel = criarPainelAnalytics();
        contentContainer.add(analyticsPanel, "ANALYTICS");
        contentContainer.add(criarPainelConfiguracoes(), "CONFIGURACOES");

        add(contentContainer, BorderLayout.CENTER);
    }

    // ==========================================
    // 1. HEADER (TOPO)
    // ==========================================
    private JPanel criarHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(23, 26, 32));
        header.setPreferredSize(new Dimension(1180, 64));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(42, 47, 56)),
                new EmptyBorder(0, 26, 0, 26)
        ));

        // Esquerda: Logo + Título + Status da Conexão com gap de 14px
        JPanel brandBox = new JPanel(new GridBagLayout());
        brandBox.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 0, 0, 12);

        JLabel lblLogo = new JLabel("🎬");
        lblLogo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        brandBox.add(lblLogo, gbc);

        JLabel lblTitle = new JLabel("VIDEOS MANAGEMENT STUDIO");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(Color.WHITE);
        brandBox.add(lblTitle, gbc);

        JLabel lblBadge = new JLabel(currentUser.isGuest() ? " ● Modo convidado " : " ● Conectado ao Google ");
        lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblBadge.setForeground(currentUser.isGuest() ? new Color(245, 190, 92) : new Color(52, 168, 83));
        lblBadge.setBackground(currentUser.isGuest() ? new Color(55, 45, 28) : new Color(28, 44, 34));
        lblBadge.setOpaque(true);
        lblBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(40, 90, 55), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));
        gbc.insets = new Insets(0, 6, 0, 0);
        brandBox.add(lblBadge, gbc);

        // Direita: Perfil do Criador e Logout com gaps confortáveis
        JPanel userBox = new JPanel(new GridBagLayout());
        userBox.setOpaque(false);

        GridBagConstraints gbcU = new GridBagConstraints();
        gbcU.gridy = 0;
        gbcU.anchor = GridBagConstraints.CENTER;
        gbcU.insets = new Insets(0, 0, 0, 14);

        // Avatar Circular
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
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(currentUser.getInitials())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(currentUser.getInitials(), tx, ty);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(36, 36));
        avatar.setOpaque(false);
        userBox.add(avatar, gbcU);

        // Informações com gap de 4px entre nome e email
        JPanel infoText = new JPanel();
        infoText.setLayout(new BoxLayout(infoText, BoxLayout.Y_AXIS));
        infoText.setOpaque(false);

        JLabel lblUserName = new JLabel(currentUser.getName());
        lblUserName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUserName.setForeground(Color.WHITE);
        lblUserName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblUserEmail = new JLabel(currentUser.getEmail());
        lblUserEmail.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblUserEmail.setForeground(new Color(155, 165, 180));
        lblUserEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoText.add(lblUserName);
        infoText.add(Box.createRigidArea(new Dimension(0, 4)));
        infoText.add(lblUserEmail);
        userBox.add(infoText, gbcU);

        // Botão Logout com gap de 12px
        JButton btnLogout = new JButton("Desconectar");
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLogout.setPreferredSize(new Dimension(105, 32));
        btnLogout.setBackground(new Color(42, 47, 56));
        btnLogout.setForeground(new Color(224, 108, 117));
        btnLogout.setFocusPainted(false);
        btnLogout.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogout.setBorder(BorderFactory.createLineBorder(new Color(60, 66, 78), 1, true));
        btnLogout.addActionListener(e -> logout());
        gbcU.insets = new Insets(0, 10, 0, 0);
        userBox.add(btnLogout, gbcU);

        header.add(brandBox, BorderLayout.WEST);
        header.add(userBox, BorderLayout.EAST);
        return header;
    }

    // ==========================================
    // 2. SIDEBAR (BARRA LATERAL)
    // ==========================================
    private JPanel criarSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(28, 32, 39));
        sidebar.setPreferredSize(new Dimension(230, 680));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(26, 16, 26, 16));

        // Título de Seção com gap de 16px abaixo
        JLabel lblMenu = new JLabel("MENU PRINCIPAL");
        lblMenu.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblMenu.setForeground(new Color(115, 125, 140));
        lblMenu.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(lblMenu);
        sidebar.add(Box.createRigidArea(new Dimension(0, 16)));

        // Botões com gap de 8px entre si
        btnNavVideos = criarBotaoMenuModerno("📹", "Meus Vídeos", true);
        btnNavNovo = criarBotaoMenuModerno("➕", "Novo Vídeo", false);
        btnNavAnalytics = criarBotaoMenuModerno("📊", "Analytics & Métricas", false);
        btnNavConfig = criarBotaoMenuModerno("⚙️", "Configurações", false);
        btnNavClips = criarBotaoMenuModerno("✂", "Editor de Clipes", false);

        btnNavVideos.addActionListener(e -> {
            atualizarSelecaoMenu(btnNavVideos);
            cardLayout.show(contentContainer, "VIDEOS");
        });

        btnNavNovo.addActionListener(e -> {
            atualizarSelecaoMenu(btnNavNovo);
            cardLayout.show(contentContainer, "NOVO_VIDEO");
        });

        btnNavAnalytics.addActionListener(e -> {
            atualizarSelecaoMenu(btnNavAnalytics);
            cardLayout.show(contentContainer, "ANALYTICS");
        });

        btnNavClips.addActionListener(e -> {
            atualizarSelecaoMenu(btnNavClips);
            cardLayout.show(contentContainer, "CLIP_EDITOR");
        });

        btnNavConfig.addActionListener(e -> {
            atualizarSelecaoMenu(btnNavConfig);
            cardLayout.show(contentContainer, "CONFIGURACOES");
        });

        sidebar.add(btnNavVideos);
        sidebar.add(btnNavClips);
        sidebar.add(btnNavAnalytics);
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebar.add(btnNavConfig);

        sidebar.add(Box.createVerticalGlue());

        // Card do Canal do YouTube com respiros adequados
        JPanel channelBox = new JPanel();
        channelBox.setLayout(new BoxLayout(channelBox, BoxLayout.Y_AXIS));
        channelBox.setBackground(new Color(35, 40, 50));
        channelBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(48, 55, 68), 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));
        channelBox.setMaximumSize(new Dimension(200, 90));
        channelBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblTag = new JLabel(currentUser.isGuest() ? "MODO DE DESENVOLVIMENTO" : "CANAL VINCULADO");
        lblTag.setFont(new Font("Segoe UI", Font.BOLD, 9));
        lblTag.setForeground(new Color(97, 175, 239));
        lblTag.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblCanal = new JLabel(currentUser.isGuest() ? "Sem conta vinculada" : currentUser.getChannelName());
        lblCanal.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCanal.setForeground(Color.WHITE);
        lblCanal.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSubs = new JLabel(currentUser.isGuest() ? "Dados reais desativados" : currentUser.getFormattedSubscribers() + " inscritos");
        lblSubs.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSubs.setForeground(new Color(152, 195, 121));
        lblSubs.setAlignmentX(Component.LEFT_ALIGNMENT);

        channelBox.add(lblTag);
        channelBox.add(Box.createRigidArea(new Dimension(0, 6)));
        channelBox.add(lblCanal);
        channelBox.add(Box.createRigidArea(new Dimension(0, 4)));
        channelBox.add(lblSubs);

        sidebar.add(channelBox);
        return sidebar;
    }

    private JButton criarBotaoMenuModerno(String icone, String texto, boolean ativo) {
        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(12, 0));
        btn.setMaximumSize(new Dimension(200, 42));
        btn.setPreferredSize(new Dimension(200, 42));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(0, 14, 0, 14));

        JLabel lblIcon = new JLabel(icone);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        lblIcon.setPreferredSize(new Dimension(24, 42));

        JLabel lblText = new JLabel(texto);
        lblText.setFont(new Font("Segoe UI", Font.BOLD, 12));

        btn.add(lblIcon, BorderLayout.WEST);
        btn.add(lblText, BorderLayout.CENTER);

        aplicarEstiloBotao(btn, lblText, ativo);
        return btn;
    }

    private void aplicarEstiloBotao(JButton btn, JLabel lblText, boolean ativo) {
        if (ativo) {
            btn.setBackground(new Color(66, 133, 244));
            lblText.setForeground(Color.WHITE);
        } else {
            btn.setBackground(new Color(35, 40, 50));
            lblText.setForeground(new Color(185, 195, 210));
        }
    }

    private void atualizarSelecaoMenu(JButton selecionado) {
        JButton[] buttons = {btnNavVideos, btnNavNovo, btnNavClips, btnNavAnalytics, btnNavConfig};
        for (JButton b : buttons) {
            JLabel txt = (JLabel) ((BorderLayout) b.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            aplicarEstiloBotao(b, txt, b == selecionado);
        }
    }

    // ==========================================
    // 3. PAINEL: GERENCIAR VÍDEOS
    // ==========================================
    private JPanel criarPainelGerenciarVideos() {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setBackground(new Color(36, 40, 48));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));

        // 1. CARDS DE MÉTRICAS COM GAPS PROPORCIONAIS (18px)
        JPanel cardsRow = new JPanel(new GridLayout(1, 4, 18, 0));
        cardsRow.setOpaque(false);
        cardsRow.setPreferredSize(new Dimension(800, 110));

        lblTotalVideosValor = new JLabel("0");
        lblTotalViewsValor = new JLabel("0");
        lblTotalLikesValor = new JLabel("0");
        lblVideosPublicadosValor = new JLabel("0");

        cardsRow.add(criarCardMetrica("Total de Vídeos", lblTotalVideosValor, "Acervo cadastrado", new Color(74, 144, 226)));
        cardsRow.add(criarCardMetrica("Visualizações Totais", lblTotalViewsValor, "+14.2% este mês", new Color(46, 204, 113)));
        cardsRow.add(criarCardMetrica("Total de Curtidas", lblTotalLikesValor, "Engajamento ativo", new Color(155, 89, 182)));
        cardsRow.add(criarCardMetrica("Vídeos no Ar", lblVideosPublicadosValor, "Públicos no YouTube", new Color(243, 156, 18)));

        panel.add(cardsRow, BorderLayout.NORTH);

        // 2. CONTEÚDO CENTRAL: TOOLBAR E TABELA
        JPanel centerPanel = new JPanel(new BorderLayout(0, 16));
        centerPanel.setOpaque(false);

        // Barra de Ações com altura padronizada de 36px para todos os elementos
        JPanel toolbar = new JPanel(new BorderLayout(14, 0));
        toolbar.setOpaque(false);
        toolbar.setPreferredSize(new Dimension(800, 38));

        // Filtros (Esquerda) com gap de 12px entre si
        JPanel filtersBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filtersBox.setOpaque(false);

        txtPesquisa = new JTextField(18);
        txtPesquisa.setPreferredSize(new Dimension(220, 36));
        txtPesquisa.putClientProperty("JTextField.placeholderText", "🔍  Pesquisar por título...");
        txtPesquisa.putClientProperty("JTextField.showClearButton", true);
        txtPesquisa.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                aplicarFiltros();
            }
        });

        cbFiltroStatus = new JComboBox<>(new String[]{"Todos os Status", "Publicado", "Rascunho", "Privado", "Não Listado"});
        cbFiltroStatus.setPreferredSize(new Dimension(150, 36));
        cbFiltroStatus.addActionListener(e -> aplicarFiltros());

        cbFiltroCategoria = new JComboBox<>(new String[]{"Todas as Categorias", "Tutoriais", "Desenvolvimento", "Vlogs", "Marketing", "Games", "Educação"});
        cbFiltroCategoria.setPreferredSize(new Dimension(165, 36));
        cbFiltroCategoria.addActionListener(e -> aplicarFiltros());

        filtersBox.add(txtPesquisa);
        filtersBox.add(cbFiltroStatus);
        filtersBox.add(cbFiltroCategoria);

        // Botões CRUD (Direita) com gap de 10px entre si
        JPanel actionsBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionsBox.setOpaque(false);

        JButton btnAdd = new JButton("➕ Novo Vídeo");
        btnAdd.setPreferredSize(new Dimension(125, 36));
        btnAdd.setBackground(new Color(66, 133, 244));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAdd.addActionListener(e -> abrirModalNovoVideo());

        JButton btnEdit = new JButton("✏️ Editar");
        btnEdit.setPreferredSize(new Dimension(95, 36));
        btnEdit.addActionListener(e -> editarVideoSelecionado());

        JButton btnDelete = new JButton("🗑️ Excluir");
        btnDelete.setPreferredSize(new Dimension(95, 36));
        btnDelete.addActionListener(e -> excluirVideoSelecionado());

        JButton btnView = new JButton("▶️ Detalhes");
        btnView.setPreferredSize(new Dimension(110, 36));
        btnView.addActionListener(e -> visualizarDetalhesVideo());

        actionsBox.add(btnAdd);
        actionsBox.add(btnEdit);
        actionsBox.add(btnDelete);
        actionsBox.add(btnView);

        toolbar.add(filtersBox, BorderLayout.WEST);
        toolbar.add(actionsBox, BorderLayout.EAST);
        centerPanel.add(toolbar, BorderLayout.NORTH);

        // Tabela com auto-redimensionamento fluido
        String[] colunas = {"ID", "Título do Vídeo", "Categoria", "Duração", "Visualizações", "Likes", "Status", "Data"};
        tableModel = new DefaultTableModel(colunas, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        videoTable = new JTable(tableModel);
        rowSorter = new TableRowSorter<>(tableModel);
        videoTable.setRowSorter(rowSorter);
        videoTable.setRowHeight(40);
        videoTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        videoTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        videoTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        videoTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Larguras ajustadas
        videoTable.getColumnModel().getColumn(0).setPreferredWidth(75);   // ID
        videoTable.getColumnModel().getColumn(1).setPreferredWidth(340);  // Título
        videoTable.getColumnModel().getColumn(2).setPreferredWidth(130);  // Categoria
        videoTable.getColumnModel().getColumn(3).setPreferredWidth(85);   // Duração
        videoTable.getColumnModel().getColumn(4).setPreferredWidth(115);  // Views
        videoTable.getColumnModel().getColumn(5).setPreferredWidth(95);   // Likes
        videoTable.getColumnModel().getColumn(6).setPreferredWidth(115);  // Status
        videoTable.getColumnModel().getColumn(7).setPreferredWidth(100);  // Data

        // Renderizadores de Alinhamento com padding interno
        DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
        centerRender.setHorizontalAlignment(SwingConstants.CENTER);

        DefaultTableCellRenderer rightRender = new DefaultTableCellRenderer();
        rightRender.setHorizontalAlignment(SwingConstants.RIGHT);
        rightRender.setBorder(new EmptyBorder(0, 0, 0, 16));

        DefaultTableCellRenderer leftPaddedRender = new DefaultTableCellRenderer();
        leftPaddedRender.setHorizontalAlignment(SwingConstants.LEFT);
        leftPaddedRender.setBorder(new EmptyBorder(0, 14, 0, 0));

        videoTable.getColumnModel().getColumn(0).setCellRenderer(centerRender);
        videoTable.getColumnModel().getColumn(1).setCellRenderer(leftPaddedRender);
        videoTable.getColumnModel().getColumn(2).setCellRenderer(leftPaddedRender);
        videoTable.getColumnModel().getColumn(3).setCellRenderer(centerRender);
        videoTable.getColumnModel().getColumn(4).setCellRenderer(rightRender);
        videoTable.getColumnModel().getColumn(5).setCellRenderer(rightRender);
        videoTable.getColumnModel().getColumn(6).setCellRenderer(new StatusBadgeRenderer());
        videoTable.getColumnModel().getColumn(7).setCellRenderer(centerRender);

        // Atalhos de Duplo Clique e Teclado
        videoTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && videoTable.getSelectedRow() >= 0) {
                    visualizarDetalhesVideo();
                }
            }
        });

        videoTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    excluirVideoSelecionado();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    visualizarDetalhesVideo();
                }
            }
        });

        atualizarTabela();

        JScrollPane scrollPane = new JScrollPane(videoTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(48, 54, 66)));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel criarCardMetrica(String titulo, JLabel lblValor, String subtitulo, Color corDestaque) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(44, 49, 60));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, corDestaque),
                new EmptyBorder(16, 18, 16, 18)
        ));

        JLabel lblTit = new JLabel(titulo);
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTit.setForeground(new Color(155, 165, 180));
        lblTit.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblValor.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblValor.setForeground(Color.WHITE);
        lblValor.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel(subtitulo);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblSub.setForeground(corDestaque);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(lblTit);
        card.add(Box.createRigidArea(new Dimension(0, 8))); // Gap generoso de 8px
        card.add(lblValor);
        card.add(Box.createRigidArea(new Dimension(0, 6))); // Gap generoso de 6px
        card.add(lblSub);

        return card;
    }

    private void atualizarTabela() {
        tableModel.setRowCount(0);
        for (Video v : videoList) {
            tableModel.addRow(new Object[]{
                    v.getId(),
                    v.getTitle(),
                    v.getCategory(),
                    v.getDuration(),
                    v.getFormattedViews(),
                    v.getFormattedLikes(),
                    v.getStatus(),
                    v.getUploadDate()
            });
        }
    }

    private void recalcularMetricas() {
        int total = videoList.size();
        long totalViews = 0;
        long totalLikes = 0;
        int publicados = 0;

        for (Video v : videoList) {
            totalViews += v.getViews();
            totalLikes += v.getLikes();
            if ("Publicado".equalsIgnoreCase(v.getStatus())) {
                publicados++;
            }
        }

        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("pt-BR"));
        lblTotalVideosValor.setText(total + " vídeos");
        lblTotalViewsValor.setText(nf.format(totalViews));
        lblTotalLikesValor.setText(nf.format(totalLikes));
        lblVideosPublicadosValor.setText(publicados + " ativos");
    }

    private void aplicarFiltros() {
        String texto = txtPesquisa.getText().trim().toLowerCase();
        String status = (String) cbFiltroStatus.getSelectedItem();
        String categoria = (String) cbFiltroCategoria.getSelectedItem();

        RowFilter<DefaultTableModel, Object> rf = new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ?> entry) {
                String title = entry.getStringValue(1).toLowerCase();
                String cat = entry.getStringValue(2);
                String stat = entry.getStringValue(6);

                boolean matchText = texto.isEmpty() || title.contains(texto);
                boolean matchStatus = "Todos os Status".equals(status) || stat.equalsIgnoreCase(status);
                boolean matchCategoria = "Todas as Categorias".equals(categoria) || cat.equalsIgnoreCase(categoria);

                return matchText && matchStatus && matchCategoria;
            }
        };
        rowSorter.setRowFilter(rf);
    }

    /**
     * Modal redimensionável com o mouse para adicionar novo vídeo
     */
    private void abrirModalNovoVideo() {
        JDialog dialog = new JDialog(this, "Cadastrar Novo Vídeo", true);
        dialog.setSize(580, 650);
        dialog.setMinimumSize(new Dimension(460, 480));
        dialog.setResizable(true); // Redimensionável com o mouse!
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(new Color(36, 40, 48));
        form.setBorder(new EmptyBorder(26, 30, 26, 30));

        JTextField txtTitulo = new JTextField();
        txtTitulo.setMaximumSize(new Dimension(1000, 36));
        txtTitulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> cbCategoria = new JComboBox<>(new String[]{"Tutoriais", "Desenvolvimento", "Vlogs", "Marketing", "Games", "Educação", "Música"});
        cbCategoria.setMaximumSize(new Dimension(1000, 36));
        cbCategoria.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtDuracao = new JTextField("12:00");
        txtDuracao.setMaximumSize(new Dimension(1000, 36));
        txtDuracao.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> cbStatus = new JComboBox<>(new String[]{"Publicado", "Rascunho", "Privado", "Não Listado"});
        cbStatus.setMaximumSize(new Dimension(1000, 36));
        cbStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        fileRow.setOpaque(false);
        fileRow.setMaximumSize(new Dimension(1000, 36));
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtUrl = new JTextField("https://youtube.com/watch?v=...");
        JButton btnBrowse = new JButton("📁 Arquivo...");
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int r = chooser.showOpenDialog(dialog);
            if (r == JFileChooser.APPROVE_OPTION) {
                txtUrl.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        fileRow.add(txtUrl, BorderLayout.CENTER);
        fileRow.add(btnBrowse, BorderLayout.EAST);

        JTextArea txtDesc = new JTextArea(4, 25);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);
        JScrollPane scrollDesc = new JScrollPane(txtDesc);
        scrollDesc.setMaximumSize(new Dimension(1000, 90));
        scrollDesc.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Inserção com gaps de 8px entre rótulos e campos
        form.add(criarLabelCampo("Título do Vídeo:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(txtTitulo);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Categoria:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(cbCategoria);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Duração (ex: 15:30):"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(txtDuracao);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Status de Publicação:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(cbStatus);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Link do YouTube ou Caminho do Arquivo:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(fileRow);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Descrição do Vídeo:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(scrollDesc);
        form.add(Box.createRigidArea(new Dimension(0, 24)));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setPreferredSize(new Dimension(100, 36));
        btnCancelar.addActionListener(e -> dialog.dispose());

        JButton btnSalvar = new JButton("Salvar Vídeo");
        btnSalvar.setPreferredSize(new Dimension(130, 36));
        btnSalvar.setBackground(new Color(66, 133, 244));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSalvar.addActionListener(e -> {
            String titulo = txtTitulo.getText().trim();
            if (titulo.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "O título do vídeo é obrigatório.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String id = "VID-" + String.format("%03d", videoList.size() + 1);
            Video novo = new Video(
                    id,
                    titulo,
                    (String) cbCategoria.getSelectedItem(),
                    txtDuracao.getText().trim(),
                    0,
                    0,
                    (String) cbStatus.getSelectedItem(),
                    txtDesc.getText().trim(),
                    txtUrl.getText().trim()
            );

            videoList.add(novo);
            atualizarTabela();
            recalcularMetricas();
            dialog.dispose();
            JOptionPane.showMessageDialog(this, "Vídeo cadastrado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        });

        btnRow.add(btnCancelar);
        btnRow.add(btnSalvar);
        form.add(btnRow);

        JScrollPane scrollDialog = new JScrollPane(form);
        scrollDialog.setBorder(null);
        dialog.add(scrollDialog, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    /**
     * Modal redimensionável com o mouse para editar vídeo existente
     */
    private void editarVideoSelecionado() {
        int selectedRow = videoTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um vídeo na tabela para editar.", "Atenção", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = videoTable.convertRowIndexToModel(selectedRow);
        Video video = videoList.get(modelRow);

        JDialog dialog = new JDialog(this, "Editar Vídeo - " + video.getId(), true);
        dialog.setSize(580, 650);
        dialog.setMinimumSize(new Dimension(460, 480));
        dialog.setResizable(true); // Redimensionável com o mouse!
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(new Color(36, 40, 48));
        form.setBorder(new EmptyBorder(26, 30, 26, 30));

        JTextField txtTitulo = new JTextField(video.getTitle());
        txtTitulo.setMaximumSize(new Dimension(1000, 36));
        txtTitulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> cbCategoria = new JComboBox<>(new String[]{"Tutoriais", "Desenvolvimento", "Vlogs", "Marketing", "Games", "Educação", "Música"});
        cbCategoria.setSelectedItem(video.getCategory());
        cbCategoria.setMaximumSize(new Dimension(1000, 36));
        cbCategoria.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtDuracao = new JTextField(video.getDuration());
        txtDuracao.setMaximumSize(new Dimension(1000, 36));
        txtDuracao.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> cbStatus = new JComboBox<>(new String[]{"Publicado", "Rascunho", "Privado", "Não Listado"});
        cbStatus.setSelectedItem(video.getStatus());
        cbStatus.setMaximumSize(new Dimension(1000, 36));
        cbStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        boolean realYouTubeVideo = !currentUser.isGuest() && isYouTubeVideoId(video.getId());
        cbStatus.setEnabled(!realYouTubeVideo);

        JTextField txtUrl = new JTextField(video.getVideoUrl());
        txtUrl.setMaximumSize(new Dimension(1000, 36));
        txtUrl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea txtDesc = new JTextArea(video.getDescription(), 4, 25);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);
        JScrollPane scrollDesc = new JScrollPane(txtDesc);
        scrollDesc.setMaximumSize(new Dimension(1000, 90));
        scrollDesc.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(criarLabelCampo("Título do Vídeo:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(txtTitulo);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Categoria:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(cbCategoria);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Duração:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(txtDuracao);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo(realYouTubeVideo ? "Visibilidade (altere em Detalhes do vídeo):" : "Status:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(cbStatus);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Link / URL:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(txtUrl);
        form.add(Box.createRigidArea(new Dimension(0, 16)));

        form.add(criarLabelCampo("Descrição:"));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(scrollDesc);
        form.add(Box.createRigidArea(new Dimension(0, 24)));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setPreferredSize(new Dimension(100, 36));
        btnCancelar.addActionListener(e -> dialog.dispose());

        JButton btnSalvar = new JButton("Salvar Alterações");
        btnSalvar.setPreferredSize(new Dimension(150, 36));
        btnSalvar.setBackground(new Color(66, 133, 244));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSalvar.addActionListener(e -> {
            video.setTitle(txtTitulo.getText().trim());
            video.setCategory((String) cbCategoria.getSelectedItem());
            video.setDuration(txtDuracao.getText().trim());
            video.setStatus((String) cbStatus.getSelectedItem());
            video.setVideoUrl(txtUrl.getText().trim());
            video.setDescription(txtDesc.getText().trim());

            atualizarTabela();
            recalcularMetricas();
            dialog.dispose();
        });

        btnRow.add(btnCancelar);
        btnRow.add(btnSalvar);
        form.add(btnRow);

        JScrollPane scrollDialog = new JScrollPane(form);
        scrollDialog.setBorder(null);
        dialog.add(scrollDialog, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private void excluirVideoSelecionado() {
        int selectedRow = videoTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um vídeo para excluir.", "Atenção", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = videoTable.convertRowIndexToModel(selectedRow);
        Video video = videoList.get(modelRow);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Tem certeza que deseja excluir o vídeo:\n\"" + video.getTitle() + "\"?",
                "Confirmar Exclusão",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            videoList.remove(modelRow);
            atualizarTabela();
            recalcularMetricas();
        }
    }

    /**
     * Modal redimensionável com o mouse para exibir detalhes completos do vídeo
     */
    private void visualizarDetalhesVideo() {
        int selectedRow = videoTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um vídeo para visualizar detalhes.", "Atenção", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = videoTable.convertRowIndexToModel(selectedRow);
        Video v = videoList.get(modelRow);

        JDialog dialog = new JDialog(this, "Detalhes do Vídeo - " + v.getId(), true);
        dialog.setSize(560, 520);
        dialog.setMinimumSize(new Dimension(440, 400));
        dialog.setResizable(true); // Redimensionável com o mouse!
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel pnl = new JPanel();
        pnl.setLayout(new BoxLayout(pnl, BoxLayout.Y_AXIS));
        pnl.setBackground(new Color(36, 40, 48));
        pnl.setBorder(new EmptyBorder(26, 32, 26, 32));

        JLabel lblTitle = new JLabel();
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblMeta = new JLabel("<html><b>ID:</b> " + v.getId() + "<br/><b>Data de Envio:</b> " + v.getUploadDate() +
                "<br/><b>Categoria:</b> " + v.getCategory() + " &nbsp;|&nbsp; <b>Duração:</b> " + v.getDuration() +
                "<br/><b>Status:</b> " + v.getStatus() +
                "<br/><b>Visualizações:</b> " + v.getFormattedViews() + " &nbsp;|&nbsp; <b>Curtidas:</b> " + v.getFormattedLikes() + "</html>");
        lblMeta.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMeta.setForeground(new Color(185, 195, 210));
        lblMeta.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblLinkTit = new JLabel("Link / Caminho do Arquivo:");
        lblLinkTit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLinkTit.setForeground(new Color(155, 165, 180));
        lblLinkTit.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtLink = new JTextField(v.getVideoUrl().isEmpty() ? "Não informado" : v.getVideoUrl());
        txtLink.setEditable(false);
        txtLink.setMaximumSize(new Dimension(1000, 36));
        txtLink.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblDescTit = new JLabel("Descrição do Vídeo:");
        lblDescTit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDescTit.setForeground(new Color(155, 165, 180));
        lblDescTit.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea txtDesc = new JTextArea(v.getDescription().isEmpty() ? "Sem descrição cadastrada." : v.getDescription(), 5, 25);
        txtDesc.setEditable(false);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);
        JScrollPane scrollDesc = new JScrollPane(txtDesc);
        scrollDesc.setMaximumSize(new Dimension(1000, 110));
        scrollDesc.setAlignmentX(Component.LEFT_ALIGNMENT);

        pnl.add(lblTitle);
        pnl.add(Box.createRigidArea(new Dimension(0, 14)));
        pnl.add(lblMeta);
        pnl.add(Box.createRigidArea(new Dimension(0, 16)));
        pnl.add(lblLinkTit);
        pnl.add(Box.createRigidArea(new Dimension(0, 6)));
        pnl.add(txtLink);
        pnl.add(Box.createRigidArea(new Dimension(0, 16)));
        pnl.add(lblDescTit);
        pnl.add(Box.createRigidArea(new Dimension(0, 6)));
        pnl.add(scrollDesc);
        pnl.add(Box.createRigidArea(new Dimension(0, 24)));

        JPanel btnRow = new JPanel();
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(1000, 84));

        JButton btnFechar = new JButton("Fechar");
        btnFechar.setPreferredSize(new Dimension(95, 36));
        btnFechar.addActionListener(e -> dialog.dispose());

        JButton btnAbrir = new JButton("▶️ Abrir Vídeo / Link");
        btnAbrir.setPreferredSize(new Dimension(170, 36));
        btnAbrir.setBackground(new Color(66, 133, 244));
        btnAbrir.setForeground(Color.WHITE);
        btnAbrir.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAbrir.setEnabled(!v.getVideoUrl().isEmpty());
        btnAbrir.addActionListener(e -> {
            try {
                String link = v.getVideoUrl().trim();
                File f = new File(link);
                if (f.exists() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(f);
                } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    if (!link.startsWith("http://") && !link.startsWith("https://")) {
                        link = "https://" + link;
                    }
                    Desktop.getDesktop().browse(new URI(link));
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Não foi possível abrir o link: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton btnVisibilidade = null;
        if (!currentUser.isGuest() && isYouTubeVideoId(v.getId())) {
            boolean privado = "Privado".equals(v.getStatus());
            JButton privacyButton = new JButton(privado ? "Tornar p\u00fablico" : "Tornar privado");
            btnVisibilidade = privacyButton;
            privacyButton.setPreferredSize(new Dimension(145, 36));
            privacyButton.setBackground(privado ? new Color(46, 145, 86) : new Color(177, 92, 55));
            privacyButton.setForeground(Color.WHITE);
            privacyButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            privacyButton.addActionListener(e -> alterarPrivacidadeVideo(v, dialog, privacyButton));
        }

        final JButton visibilityAction = btnVisibilidade;
        final boolean[] footerStacked = {false};
        Runnable reorganizarBotoes = () -> {
            boolean stacked = visibilityAction != null && btnRow.getWidth() < 455;
            if (stacked == footerStacked[0] && btnRow.getComponentCount() > 0) return;
            footerStacked[0] = stacked;
            btnRow.removeAll();
            if (stacked) {
                btnRow.setLayout(new GridLayout(2, 1, 0, 8));
                JPanel privacyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                privacyRow.setOpaque(false);
                privacyRow.add(visibilityAction);
                JPanel navigationRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
                navigationRow.setOpaque(false);
                navigationRow.add(btnFechar);
                navigationRow.add(btnAbrir);
                btnRow.add(privacyRow);
                btnRow.add(navigationRow);
            } else {
                btnRow.setLayout(new FlowLayout(FlowLayout.RIGHT, 12, 0));
                if (visibilityAction != null) btnRow.add(visibilityAction);
                btnRow.add(btnFechar);
                btnRow.add(btnAbrir);
            }
            btnRow.revalidate();
            btnRow.repaint();
        };
        btnRow.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { reorganizarBotoes.run(); }
        });
        btnRow.setLayout(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        if (visibilityAction != null) btnRow.add(visibilityAction);
        btnRow.add(btnFechar);
        btnRow.add(btnAbrir);
        pnl.add(btnRow);

        JScrollPane scrollPnl = new JScrollPane(pnl);
        scrollPnl.setBorder(null);
        scrollPnl.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                int availableWidth = Math.max(180, scrollPnl.getViewport().getExtentSize().width - 24);
                String wrappedTitle = wrapHtmlText(v.getTitle(), lblTitle.getFontMetrics(lblTitle.getFont()), availableWidth);
                lblTitle.setText("<html><div style='font-size:16px; font-weight:bold; color:#61afef;'>" + wrappedTitle + "</div></html>");
                pnl.setPreferredSize(new Dimension(Math.max(200, scrollPnl.getViewport().getExtentSize().width), pnl.getPreferredSize().height));
                pnl.revalidate();
            }
        });
        dialog.add(scrollPnl, BorderLayout.CENTER);
        SwingUtilities.invokeLater(reorganizarBotoes);
        dialog.setVisible(true);
    }

    private static String wrapHtmlText(String text, FontMetrics metrics, int maxWidth) {
        StringBuilder result = new StringBuilder();
        int lineWidth = 0;
        for (String word : text.trim().split("\\s+")) {
            int wordWidth = metrics.stringWidth(word);
            int spaceWidth = metrics.charWidth(' ');
            if (lineWidth > 0 && lineWidth + spaceWidth + wordWidth > maxWidth) {
                result.append("<br/>");
                lineWidth = 0;
            } else if (lineWidth > 0) {
                result.append(' ');
                lineWidth += spaceWidth;
            }
            result.append(escapeHtml(word));
            lineWidth += wordWidth;
        }
        return result.toString();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void alterarPrivacidadeVideo(Video video, JDialog dialog, JButton actionButton) {
        boolean tornarPublico = "Privado".equals(video.getStatus());
        String privacyStatus = tornarPublico ? "public" : "private";
        String destino = tornarPublico ? "público" : "privado";
        String aviso = tornarPublico
                ? "Qualquer pessoa poderá encontrar e assistir a este vídeo no YouTube. Deseja torná-lo público?"
                : "O vídeo deixará de ser acessível publicamente. Deseja torná-lo privado?";
        int confirm = JOptionPane.showConfirmDialog(dialog, aviso, "Confirmar alteração de visibilidade",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        actionButton.setEnabled(false);
        actionButton.setText("Salvando...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return GoogleAuthService.updateVideoPrivacy(currentUser.getToken(), video.getId(), privacyStatus);
            }

            @Override protected void done() {
                try {
                    get();
                    video.setStatus(tornarPublico ? "Publicado" : "Privado");
                    atualizarTabela();
                    recalcularMetricas();
                    JOptionPane.showMessageDialog(dialog, "O vídeo agora está " + destino + " no YouTube.",
                            "Visibilidade atualizada", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(dialog, cause.getMessage(), "Não foi possível alterar a visibilidade", JOptionPane.ERROR_MESSAGE);
                    actionButton.setEnabled(true);
                    actionButton.setText(tornarPublico ? "Tornar público" : "Tornar privado");
                }
            }
        }.execute();
    }

    private static boolean isYouTubeVideoId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    // ==========================================
    // 4. PAINEL: NOVO VÍDEO COMPLETO
    // ==========================================
    private JPanel criarPainelNovoVideo() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(36, 40, 48));
        panel.setBorder(new EmptyBorder(30, 40, 30, 40));

        JPanel formCard = new JPanel();
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBackground(new Color(44, 49, 60));
        formCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 62, 75), 1, true),
                new EmptyBorder(26, 32, 26, 32)
        ));

        JLabel lblTitle = new JLabel("➕ Cadastrar e Publicar Novo Vídeo");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(lblTitle);
        formCard.add(Box.createRigidArea(new Dimension(0, 18)));

        JTextField txtTitle = new JTextField();
        txtTitle.setMaximumSize(new Dimension(800, 36));
        txtTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> cbCat = new JComboBox<>(new String[]{"Tutoriais", "Desenvolvimento", "Vlogs", "Marketing", "Games", "Educação"});
        cbCat.setMaximumSize(new Dimension(800, 36));
        cbCat.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtDuration = new JTextField("10:00");
        txtDuration.setMaximumSize(new Dimension(800, 36));
        txtDuration.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> cbStat = new JComboBox<>(new String[]{"Publicado", "Rascunho", "Privado", "Não Listado"});
        cbStat.setMaximumSize(new Dimension(800, 36));
        cbStat.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filePanel = new JPanel(new BorderLayout(8, 0));
        filePanel.setOpaque(false);
        filePanel.setMaximumSize(new Dimension(800, 36));
        filePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField txtFilePath = new JTextField();
        JButton btnBrowse = new JButton("📁 Procurar Arquivo...");
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int ret = chooser.showOpenDialog(this);
            if (ret == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                txtFilePath.setText(file.getAbsolutePath());
            }
        });
        filePanel.add(txtFilePath, BorderLayout.CENTER);
        filePanel.add(btnBrowse, BorderLayout.EAST);

        JTextArea txtDescricao = new JTextArea(4, 20);
        txtDescricao.setLineWrap(true);
        JScrollPane scrollDesc = new JScrollPane(txtDescricao);
        scrollDesc.setMaximumSize(new Dimension(800, 90));
        scrollDesc.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Gaps generosos entre labels e inputs para layout limpo e agradável
        formCard.add(criarLabelCampo("Título do Vídeo:"));
        formCard.add(Box.createRigidArea(new Dimension(0, 8)));
        formCard.add(txtTitle);
        formCard.add(Box.createRigidArea(new Dimension(0, 16)));

        formCard.add(criarLabelCampo("Categoria:"));
        formCard.add(Box.createRigidArea(new Dimension(0, 8)));
        formCard.add(cbCat);
        formCard.add(Box.createRigidArea(new Dimension(0, 16)));

        formCard.add(criarLabelCampo("Duração estimada:"));
        formCard.add(Box.createRigidArea(new Dimension(0, 8)));
        formCard.add(txtDuration);
        formCard.add(Box.createRigidArea(new Dimension(0, 16)));

        formCard.add(criarLabelCampo("Status inicial:"));
        formCard.add(Box.createRigidArea(new Dimension(0, 8)));
        formCard.add(cbStat);
        formCard.add(Box.createRigidArea(new Dimension(0, 16)));

        formCard.add(criarLabelCampo("Arquivo local do vídeo ou URL:"));
        formCard.add(Box.createRigidArea(new Dimension(0, 8)));
        formCard.add(filePanel);
        formCard.add(Box.createRigidArea(new Dimension(0, 16)));

        formCard.add(criarLabelCampo("Descrição do Vídeo:"));
        formCard.add(Box.createRigidArea(new Dimension(0, 8)));
        formCard.add(scrollDesc);
        formCard.add(Box.createRigidArea(new Dimension(0, 24)));

        JButton btnSalvar = new JButton("💾 Salvar e Adicionar ao Dashboard");
        btnSalvar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSalvar.setBackground(new Color(66, 133, 244));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setMaximumSize(new Dimension(280, 42));
        btnSalvar.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSalvar.addActionListener(e -> {
            String t = txtTitle.getText().trim();
            if (t.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Informe o título do vídeo.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String id = "VID-" + String.format("%03d", videoList.size() + 1);
            Video novo = new Video(
                    id,
                    t,
                    (String) cbCat.getSelectedItem(),
                    txtDuration.getText().trim(),
                    0,
                    0,
                    (String) cbStat.getSelectedItem(),
                    txtDescricao.getText().trim(),
                    txtFilePath.getText().trim()
            );

            videoList.add(novo);
            atualizarTabela();
            recalcularMetricas();

            txtTitle.setText("");
            txtFilePath.setText("");
            txtDescricao.setText("");

            JOptionPane.showMessageDialog(this, "Vídeo cadastrado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            atualizarSelecaoMenu(btnNavVideos);
            cardLayout.show(contentContainer, "VIDEOS");
        });

        formCard.add(btnSalvar);
        panel.add(formCard, BorderLayout.CENTER);
        return panel;
    }

    private JLabel criarLabelCampo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(175, 185, 200));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    // ==========================================
    // 5. PAINEL: ANALYTICS & MÉTRICAS
    // ==========================================
    private JPanel criarPainelAnalytics() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(36, 40, 48));
        panel.setBorder(new EmptyBorder(30, 40, 30, 40));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(44, 49, 60));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 62, 75), 1, true),
                new EmptyBorder(26, 32, 26, 32)
        ));

        JLabel lblTitle = new JLabel(currentUser.isGuest() ? "Prévia de métricas" : "Relatório e performance do canal");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lblTitle);
        card.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel lblDesc = new JLabel(currentUser.isGuest()
                ? "<html>Modo convidado: conecte uma conta Google para carregar métricas reais do canal.</html>"
                : "<html>Dados consolidados da conta vinculada: <b>" + currentUser.getChannelName() + "</b></html>");
        lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDesc.setForeground(new Color(165, 175, 190));
        lblDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lblDesc);
        card.add(Box.createRigidArea(new Dimension(0, 22)));

        JPanel statsGrid = new JPanel(new GridLayout(2, 2, 18, 18));
        statsGrid.setOpaque(false);
        statsGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        statsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        long views = videoList.stream().mapToLong(Video::getViews).sum();
        long likes = videoList.stream().mapToLong(Video::getLikes).sum();
        String average = videoList.isEmpty() ? "—" : NumberFormat.getInstance(Locale.forLanguageTag("pt-BR")).format(views / videoList.size());
        String engagement = views == 0 ? "—" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", likes * 100.0 / views);
        String dataSource = currentUser.isGuest() ? "Disponível após conectar o YouTube" : "YouTube Data API";
        statsGrid.add(criarBlocoAnalytics(currentUser.isGuest() ? "Vídeos nesta sessão" : "Vídeos carregados do canal", String.valueOf(videoList.size()),
                currentUser.isGuest() ? "Sem dados de demonstração" : dataSource, new Color(46, 204, 113)));
        statsGrid.add(criarBlocoAnalytics("Média de visualizações", average,
                currentUser.isGuest() ? dataSource : "Com base nos vídeos carregados", new Color(74, 144, 226)));
        statsGrid.add(criarBlocoAnalytics("Curtidas / visualizações", engagement,
                currentUser.isGuest() ? dataSource : "Com base nos dados disponíveis", new Color(155, 89, 182)));
        statsGrid.add(criarBlocoAnalytics("Inscritos do canal", currentUser.getFormattedSubscribers(), dataSource, new Color(243, 156, 18)));

        card.add(statsGrid);
        statsGrid.addComponentListener(new java.awt.event.ComponentAdapter() {
            private int columns = 2;
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int width = statsGrid.getWidth();
                int nextColumns = width >= 1120 ? 4 : width >= 620 ? 2 : 1;
                if (nextColumns != columns) {
                    columns = nextColumns;
                    statsGrid.setLayout(new GridLayout(0, columns, 18, 18));
                    int rows = (4 + columns - 1) / columns;
                    statsGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * 116 + (rows - 1) * 18));
                    statsGrid.revalidate();
                }
            }
        });
        panel.add(card, BorderLayout.NORTH);
        return panel;
    }

    private JPanel criarBlocoAnalytics(String titulo, String valor, String sub, Color cor) {
        JPanel b = new JPanel();
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
        b.setBackground(new Color(34, 38, 47));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, cor),
                new EmptyBorder(14, 16, 14, 16)
        ));
        b.setMinimumSize(new Dimension(190, 116));
        b.setPreferredSize(new Dimension(260, 116));

        JLabel l1 = new JLabel(titulo);
        l1.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l1.setForeground(new Color(150, 160, 175));
        l1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l2 = new JLabel(valor);
        l2.setFont(new Font("Segoe UI", Font.BOLD, 20));
        l2.setForeground(Color.WHITE);
        l2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l3 = new JLabel(sub);
        l3.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        l3.setForeground(cor);
        l3.setAlignmentX(Component.LEFT_ALIGNMENT);

        b.add(l1);
        b.add(Box.createRigidArea(new Dimension(0, 6)));
        b.add(l2);
        b.add(Box.createRigidArea(new Dimension(0, 4)));
        b.add(l3);
        return b;
    }

    // ==========================================
    // 6. PAINEL: CONFIGURAÇÕES E CONTA GOOGLE
    // ==========================================
    private JPanel criarPainelConfiguracoes() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(36, 40, 48));
        panel.setBorder(new EmptyBorder(30, 40, 30, 40));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(44, 49, 60));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 62, 75), 1, true),
                new EmptyBorder(26, 32, 26, 32)
        ));

        JLabel lblTitle = new JLabel("⚙️ Configurações da Sessão & Conta Google");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lblTitle);
        card.add(Box.createRigidArea(new Dimension(0, 22)));

        // Grid com alinhamento rigoroso via GridBagLayout em duas colunas padronizadas com gap de 28px
        JPanel infoGrid = new JPanel(new GridBagLayout());
        infoGrid.setOpaque(false);
        infoGrid.setMaximumSize(new Dimension(800, 240));
        infoGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        adicionarLinhaConfig(infoGrid, 0, "Nome do Usuário Google:", currentUser.getName());
        adicionarLinhaConfig(infoGrid, 1, "E-mail Google:", currentUser.getEmail());
        adicionarLinhaConfig(infoGrid, 2, "Canal Conectado:", currentUser.getChannelName());
        adicionarLinhaConfig(infoGrid, 3, "Token OAuth:", currentUser.isGuest() ? "Não utilizado nesta sessão" : "Armazenado localmente até expirar (aprox. 55 min)");
        adicionarLinhaConfig(infoGrid, 4, "Arquivo de Sessão Local:", currentUser.isGuest() ? "Sessão convidado não persistida" : SessionManager.getSessionFilePath());
        adicionarLinhaConfig(infoGrid, 5, "Status da Sessão:", currentUser.isGuest() ? "Modo convidado (sem conexão Google)" : "● Ativo & Autenticado (OAuth 2.0)");

        card.add(infoGrid);
        card.add(Box.createRigidArea(new Dimension(0, 28)));

        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        actionsRow.setOpaque(false);
        actionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnTrocarConta = new JButton("Alterar / Trocar Conta Google");
        btnTrocarConta.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnTrocarConta.setPreferredSize(new Dimension(240, 38));
        btnTrocarConta.setBackground(new Color(66, 133, 244));
        btnTrocarConta.setForeground(Color.WHITE);
        btnTrocarConta.addActionListener(e -> logout());

        JButton btnLimparSessao = new JButton("Limpar Token e Sair");
        btnLimparSessao.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLimparSessao.setPreferredSize(new Dimension(170, 38));
        btnLimparSessao.setBackground(new Color(224, 108, 117));
        btnLimparSessao.setForeground(Color.WHITE);
        btnLimparSessao.addActionListener(e -> {
            if (!currentUser.isGuest()) SessionManager.clearSession();
            logout();
        });

        actionsRow.add(btnTrocarConta);
        actionsRow.add(btnLimparSessao);
        card.add(actionsRow);

        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    private void adicionarLinhaConfig(JPanel grid, int row, String rotulo, String valor) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row;
        g.insets = new Insets(8, 0, 8, 28); // 28px de gap horizontal entre rótulo e valor
        g.anchor = GridBagConstraints.WEST;

        // Rótulo
        g.gridx = 0;
        g.weightx = 0.0;
        JLabel lblR = new JLabel(rotulo);
        lblR.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblR.setForeground(new Color(155, 165, 180));
        grid.add(lblR, g);

        // Valor
        g.gridx = 1;
        g.weightx = 1.0;
        JLabel lblV = new JLabel(valor);
        lblV.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        if (valor.startsWith("●")) {
            lblV.setForeground(new Color(52, 168, 83));
            lblV.setFont(new Font("Segoe UI", Font.BOLD, 12));
        } else {
            lblV.setForeground(Color.WHITE);
        }
        grid.add(lblV, g);
    }

    private void logout() {
        int resp = JOptionPane.showConfirmDialog(this,
                "Deseja realmente desconectar da conta Google?",
                "Desconectar",
                JOptionPane.YES_NO_OPTION);

        if (resp == JOptionPane.YES_OPTION) {
            if (!currentUser.isGuest()) SessionManager.clearSession();
            GoogleAuthService.stopServer();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    /**
     * Renderizador customizado para exibir os status como badges/pills coloridos na tabela.
     */
    private static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String status = value != null ? value.toString() : "";
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(new Font("Segoe UI", Font.BOLD, 11));

            if (!isSelected) {
                switch (status) {
                    case "Publicado":
                        label.setForeground(new Color(46, 204, 113));
                        label.setBackground(new Color(28, 44, 34));
                        break;
                    case "Rascunho":
                        label.setForeground(new Color(243, 156, 18));
                        label.setBackground(new Color(44, 38, 25));
                        break;
                    case "Privado":
                        label.setForeground(new Color(231, 76, 60));
                        label.setBackground(new Color(45, 28, 28));
                        break;
                    case "Não Listado":
                        label.setForeground(new Color(74, 144, 226));
                        label.setBackground(new Color(26, 36, 48));
                        break;
                    default:
                        label.setForeground(Color.WHITE);
                        label.setBackground(table.getBackground());
                }
            }
            return label;
        }
    }

}
