import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.JSONObject;

public class Cliente extends JFrame {

    // ==================== CONSTANTES ====================
    private static final int PUERTO_TCP = 9999;
    private static final int PUERTO_UDP = 9998;
    
    // ==================== ATRIBUTOS DE RED ====================
    private Socket socketTCP;
    private PrintWriter salidaTCP;
    private BufferedReader entradaTCP;
    private DatagramSocket socketUDP;
    private InetAddress servidorIP;
    private String nombreUsuario;

    // ==================== COMPONENTES GRÁFICOS ====================
    private JEditorPane areaChatGeneral;
    private JTextField campoMensaje;
    private JTabbedPane tabbedPane;
    private JLabel estadoLabel;
    
    // Controles de tipo de mensaje
    private JRadioButton radioTCP;
    private JRadioButton radioUDP;
    private String tipoSeleccionado = "broadcast";

    private JButton btnBroadcast;
    private JButton btnUnicast;
    private JButton btnMulticast;
    private JButton btnAnycast;
    private JComboBox<String> comboDestino;
    private JPanel destinoPanel;

    // Listas de usuarios y grupos
    private DefaultListModel<String> listaUsuariosModel;
    private JList<String> listaUsuarios;
    private DefaultListModel<String> listaGruposModel;
    private JList<String> listaGrupos;
    
    // Gestión de chats y notificaciones
    private Map<String, JEditorPane> chatsPrivados;
    private Map<String, JEditorPane> chatsGrupales;
    private Map<String, Integer> notificaciones;
    
    private Map<String, StringBuilder> historialPrivados;
    private Map<String, StringBuilder> historialGrupales;
    
    private StringBuilder htmlContentGeneral;
    
    // ==================== ESQUEMA DE COLORES ====================
    private final Color BG_MAIN = new Color(18, 18, 24);      
    private final Color BG_SIDEBAR = new Color(22, 22, 30);  
    private final Color BG_CHAT = new Color(26, 26, 34);     
    private final Color BG_INPUT = new Color(35, 35, 45);    
    private final Color BORDER = new Color(45, 45, 55);      
    private final Color TEXT = new Color(235, 235, 245);     
    private final Color TEXT_SEC = new Color(150, 150, 170);  
    private final Color ACCENT = new Color(140, 110, 180);   
    
    // Colores para cada tipo de mensaje
    private final Color COLOR_BROADCAST = new Color(170, 130, 210);  
    private final Color COLOR_UNICAST = new Color(100, 180, 220);   
    private final Color COLOR_MULTICAST = new Color(220, 170, 110);  
    private final Color COLOR_ANYCAST = new Color(200, 140, 170);   
    
    // Colores de burbujas de chat
    private final Color BG_MIS_PROPIOS = new Color(55, 75, 95);
    private final Color BG_OTROS = new Color(48, 48, 58);
    private final Color COLOR_MIS_NOMBRE = new Color(140, 200, 240);
    private final Color COLOR_OTRO_NOMBRE = new Color(200, 170, 230);
    
    // Colores para pestañas (seleccionada vs no seleccionada)
    private final Color TAB_SELECTED_BG = new Color(173, 216, 230);   
    private final Color TAB_SELECTED_FG = new Color(100, 100, 120);   
    private final Color TAB_NORMAL_BG = new Color(25, 35, 55);         
    private final Color TAB_NORMAL_FG = Color.WHITE;               
    
    // Constructor principal - Inicializa la interfaz y la conexión
    public Cliente() {
        setTitle("Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 800);
        setLocationRelativeTo(null);
        
        // Inicializar estructuras de datos
        chatsPrivados = new HashMap<>();
        chatsGrupales = new HashMap<>();
        notificaciones = new HashMap<>();
        historialPrivados = new HashMap<>();
        historialGrupales = new HashMap<>();
        htmlContentGeneral = new StringBuilder();
        
        // Configurar panel de pestañas
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(BG_SIDEBAR);
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        // Listener para manejar notificaciones visuales
        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx != -1) {
                String title = tabbedPane.getTitleAt(idx);
                if (title.contains(" ●")) {
                    String cleanTitle = title.substring(0, title.indexOf(" ●"));
                    tabbedPane.setTitleAt(idx, cleanTitle);
                    String key = cleanTitle.substring(cleanTitle.indexOf(": ") + 2);
                    notificaciones.remove(key);
                }
            }
            actualizarColoresPestanas();
        });
        
        // Mostrar ventana de conexión
        if (!mostrarVentanaConexion()) System.exit(0);
        
        setTitle("Chat • " + nombreUsuario);
        crearInterfaz();
        conectarAlServidor();
    }
    
    /**
     * Actualiza los colores de las pestañas según su estado (seleccionada o no)
     */
    private void actualizarColoresPestanas() {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (i == tabbedPane.getSelectedIndex()) {
                // Pestaña seleccionada: fondo azul claro, texto gris
                tabbedPane.setBackgroundAt(i, TAB_SELECTED_BG);
                tabbedPane.setForegroundAt(i, TAB_SELECTED_FG);
            } else {
                // Pestaña no seleccionada: fondo azul marino, texto blanco
                tabbedPane.setBackgroundAt(i, TAB_NORMAL_BG);
                tabbedPane.setForegroundAt(i, TAB_NORMAL_FG);
            }
        }
    }
   
    private String crearMensajeJSON(String tipo, String destino, String contenido, String protocolo) {
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        JSONObject json = new JSONObject();
        json.put("tipo", tipo);
        json.put("emisor", nombreUsuario);
        json.put("timestamp", hora);
        json.put("protocolo", protocolo);
        json.put("contenido", contenido);
        if (destino != null && !destino.isEmpty()) {
            json.put("destino", destino);
        }
        return json.toString();
    }
   

    private Map<String, String> parsearJSON(String jsonStr) {
        Map<String, String> datos = new HashMap<>();
        try {
            JSONObject json = new JSONObject(jsonStr);
            datos.put("tipo", json.optString("tipo", ""));
            datos.put("emisor", json.optString("emisor", ""));
            datos.put("contenido", json.optString("contenido", ""));
            datos.put("protocolo", json.optString("protocolo", ""));
            datos.put("timestamp", json.optString("timestamp", ""));
            datos.put("destino", json.optString("destino", ""));
            datos.put("anycast", json.optString("anycast", "false"));
        } catch(Exception e) {}
        return datos;
    }
    
    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
    private String getHtmlHeader() {
        return "<html><body style='font-family:Segoe UI, Helvetica, sans-serif; background-color:" + colorToHex(BG_CHAT) + 
               "; color:" + colorToHex(TEXT) + "; padding:12px; font-size:14px; margin:0;'>";
    }
    
    private String getHtmlFooter() {
        return "</body></html>";
    }
    

    private void actualizarPanelHTML(JEditorPane panel, StringBuilder contenido) {
        String htmlCompleto = getHtmlHeader() + contenido.toString() + getHtmlFooter();
        panel.setText(htmlCompleto);
        panel.setCaretPosition(panel.getDocument().getLength());
    }
    
    private boolean mostrarVentanaConexion() {
        JDialog dialog = new JDialog(this, "Conectar", true);
        dialog.setSize(420, 480);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        dialog.getContentPane().setBackground(BG_MAIN);
        
        JPanel panel = new JPanel();
        panel.setBackground(BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 35, 40, 35));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        JLabel titulo = new JLabel("CHAT");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titulo.setForeground(ACCENT);
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titulo);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        JLabel sub = new JLabel("Broadcast · Unicast · Multicast · Anycast");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(TEXT_SEC);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(sub);
        panel.add(Box.createRigidArea(new Dimension(0, 40)));
        
        // Campo IP del servidor
        JLabel ipLabel = new JLabel("SERVIDOR IP");
        ipLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        ipLabel.setForeground(TEXT_SEC);
        ipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(ipLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        
        JTextField campoIP = new JTextField("127.0.0.1");
        campoIP.setBackground(BG_INPUT);
        campoIP.setForeground(TEXT);
        campoIP.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        campoIP.setMaximumSize(new Dimension(280, 40));
        campoIP.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(campoIP);
        panel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // Campo nombre de usuario
        JLabel nombreLabel = new JLabel("TU NOMBRE");
        nombreLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        nombreLabel.setForeground(TEXT_SEC);
        nombreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(nombreLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        
        JTextField campoNombre = new JTextField();
        campoNombre.setBackground(BG_INPUT);
        campoNombre.setForeground(TEXT);
        campoNombre.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        campoNombre.setMaximumSize(new Dimension(280, 40));
        campoNombre.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(campoNombre);
        panel.add(Box.createRigidArea(new Dimension(0, 35)));
        
        // Botón conectar
        JButton btn = new JButton("✦ CONECTAR ✦");
        btn.setBackground(ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(12, 25, 12, 25));
        btn.setMaximumSize(new Dimension(280, 45));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            String ip = campoIP.getText().trim();
            String nombre = campoNombre.getText().trim();
            if (nombre.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Por favor, ingresa tu nombre");
                return;
            }
            try {
                servidorIP = InetAddress.getByName(ip);
                nombreUsuario = nombre;
                dialog.dispose();
            } catch(Exception ex) {
                JOptionPane.showMessageDialog(dialog, "IP inválida");
            }
        });
        
        panel.add(btn);
        dialog.add(panel);
        dialog.setVisible(true);
        return true;
    }
    
    private void crearInterfaz() {
        setLayout(new BorderLayout());
        
        // ==================== BARRA SUPERIOR ====================
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_SIDEBAR);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            BorderFactory.createEmptyBorder(12, 20, 12, 20)));
        
        JLabel userLabel = new JLabel("◉ " + nombreUsuario);
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userLabel.setForeground(TEXT);
        
        estadoLabel = new JLabel("● Conectado");
        estadoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        estadoLabel.setForeground(new Color(80, 200, 120));
        
        topBar.add(userLabel, BorderLayout.WEST);
        topBar.add(estadoLabel, BorderLayout.EAST);
        
        // ==================== PANEL DIVIDIDO ====================
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.22);
        split.setBorder(null);
        split.setBackground(BG_MAIN);
        
        // ==================== BARRA LATERAL ====================
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        
        // Panel de usuarios conectados
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBackground(BG_SIDEBAR);
        usersPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER),
            "✦ USUARIOS ✦", TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 10), TEXT_SEC));
        
        listaUsuariosModel = new DefaultListModel<>();
        listaUsuarios = new JList<>(listaUsuariosModel);
        listaUsuarios.setBackground(BG_INPUT);
        listaUsuarios.setForeground(TEXT);
        listaUsuarios.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        listaUsuarios.setSelectionBackground(new Color(140, 110, 180, 80));
        listaUsuarios.setFixedCellHeight(34);
        listaUsuarios.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        // Doble clic para abrir chat privado
        listaUsuarios.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String user = listaUsuarios.getSelectedValue();
                    if (user != null && !user.equals(nombreUsuario)) {
                        tipoSeleccionado = "unicast";
                        actualizarSeleccion();
                        comboDestino.setSelectedItem(user);
                        abrirChatPrivado(user);
                        campoMensaje.requestFocus();
                    }
                }
            }
        });
        
        JScrollPane usersScroll = new JScrollPane(listaUsuarios);
        usersScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        usersScroll.getViewport().setBackground(BG_INPUT);
        usersPanel.add(usersScroll, BorderLayout.CENTER);
        
        // Panel de grupos
        JPanel groupsPanel = new JPanel(new BorderLayout());
        groupsPanel.setBackground(BG_SIDEBAR);
        groupsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER),
            "✦ GRUPOS ✦", TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 10), TEXT_SEC));
        
        listaGruposModel = new DefaultListModel<>();
        listaGrupos = new JList<>(listaGruposModel);
        listaGrupos.setBackground(BG_INPUT);
        listaGrupos.setForeground(TEXT);
        listaGrupos.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        listaGrupos.setSelectionBackground(new Color(140, 110, 180, 80));
        listaGrupos.setFixedCellHeight(34);
        listaGrupos.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        // Doble clic para abrir chat grupal
        listaGrupos.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String grupo = listaGrupos.getSelectedValue();
                    if (grupo != null) {
                        tipoSeleccionado = "multicast";
                        actualizarSeleccion();
                        comboDestino.setSelectedItem(grupo);
                        abrirChatGrupal(grupo);
                        campoMensaje.requestFocus();
                    }
                }
            }
        });
        
        JScrollPane groupsScroll = new JScrollPane(listaGrupos);
        groupsScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        groupsScroll.getViewport().setBackground(BG_INPUT);
        groupsPanel.add(groupsScroll, BorderLayout.CENTER);
        
        // Botón crear grupo
        JPanel groupBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 8));
        groupBtns.setBackground(BG_SIDEBAR);
        
        JButton crearBtn = new JButton("+ Crear grupo");
        crearBtn.setBackground(ACCENT);
        crearBtn.setForeground(Color.WHITE);
        crearBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        crearBtn.setFocusPainted(false);
        crearBtn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        crearBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        crearBtn.addActionListener(e -> abrirDialogoCrearGrupo());
        
        groupBtns.add(crearBtn);
        groupsPanel.add(groupBtns, BorderLayout.SOUTH);
        
        sidebar.add(usersPanel, BorderLayout.NORTH);
        sidebar.add(groupsPanel, BorderLayout.CENTER);
        sidebar.setPreferredSize(new Dimension(260, 0));
        
        // ==================== ÁREA DE CHAT GENERAL ====================
        JPanel generalPanel = new JPanel(new BorderLayout());
        generalPanel.setBackground(BG_CHAT);
        generalPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        
        areaChatGeneral = new JEditorPane("text/html", getHtmlHeader() + getHtmlFooter());
        areaChatGeneral.setEditable(false);
        areaChatGeneral.setBackground(BG_CHAT);
        areaChatGeneral.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        
        JScrollPane scroll = new JScrollPane(areaChatGeneral);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(BG_CHAT);
        generalPanel.add(scroll, BorderLayout.CENTER);
        
        tabbedPane.addTab("GENERAL", generalPanel);
        
        // ==================== PANEL INFERIOR ====================
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(BG_SIDEBAR);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(12, 15, 14, 15)));
        
        // Fila de tipos de mensaje
        JPanel tiposRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tiposRow.setBackground(BG_SIDEBAR);
        
        btnBroadcast = crearBotonTipo("Broadcast", COLOR_BROADCAST);
        btnUnicast = crearBotonTipo("Unicast", COLOR_UNICAST);
        btnMulticast = crearBotonTipo("Multicast", COLOR_MULTICAST);
        btnAnycast = crearBotonTipo("Anycast", COLOR_ANYCAST);
        
        btnBroadcast.addActionListener(e -> { tipoSeleccionado = "broadcast"; actualizarSeleccion(); });
        btnUnicast.addActionListener(e -> { tipoSeleccionado = "unicast"; actualizarSeleccion(); });
        btnMulticast.addActionListener(e -> { tipoSeleccionado = "multicast"; actualizarSeleccion(); });
        btnAnycast.addActionListener(e -> { tipoSeleccionado = "anycast"; actualizarSeleccion(); });
        
        tiposRow.add(btnBroadcast);
        tiposRow.add(btnUnicast);
        tiposRow.add(btnMulticast);
        tiposRow.add(btnAnycast);
        
        // Selector de destino (para unicast y multicast)
        destinoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        destinoPanel.setBackground(BG_SIDEBAR);
        JLabel destinoLabel = new JLabel("→");
        destinoLabel.setForeground(TEXT_SEC);
        destinoPanel.add(destinoLabel);
        
        comboDestino = new JComboBox<>();
        comboDestino.setBackground(BG_INPUT);
        comboDestino.setForeground(TEXT);
        comboDestino.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboDestino.setPreferredSize(new Dimension(130, 30));
        comboDestino.setBorder(BorderFactory.createLineBorder(BORDER));
        destinoPanel.add(comboDestino);
        destinoPanel.setVisible(false);
        
        tiposRow.add(destinoPanel);
        tiposRow.add(Box.createRigidArea(new Dimension(15, 0)));
        
        // Selector de protocolo
        JLabel protoLabel = new JLabel("Protocolo:");
        protoLabel.setForeground(TEXT_SEC);
        protoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tiposRow.add(protoLabel);
        
        radioTCP = new JRadioButton("TCP", true);
        radioUDP = new JRadioButton("UDP");
        radioTCP.setBackground(BG_SIDEBAR);
        radioUDP.setBackground(BG_SIDEBAR);
        radioTCP.setForeground(new Color(100, 200, 255));
        radioUDP.setForeground(new Color(255, 180, 100));
        radioTCP.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        radioUDP.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ButtonGroup bg = new ButtonGroup();
        bg.add(radioTCP);
        bg.add(radioUDP);
        tiposRow.add(radioTCP);
        tiposRow.add(radioUDP);
        
        bottomPanel.add(tiposRow, BorderLayout.NORTH);
        
        // Fila de entrada de mensaje
        JPanel sendRow = new JPanel(new BorderLayout(10, 0));
        sendRow.setBackground(BG_SIDEBAR);
        sendRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        campoMensaje = new JTextField();
        campoMensaje.setBackground(BG_INPUT);
        campoMensaje.setForeground(TEXT);
        campoMensaje.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        campoMensaje.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        campoMensaje.addActionListener(e -> enviarMensaje());
        
        JButton enviarBtn = new JButton("✦ ENVIAR");
        enviarBtn.setBackground(ACCENT);
        enviarBtn.setForeground(Color.WHITE);
        enviarBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        enviarBtn.setFocusPainted(false);
        enviarBtn.setPreferredSize(new Dimension(90, 42));
        enviarBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        enviarBtn.addActionListener(e -> enviarMensaje());
        
        sendRow.add(campoMensaje, BorderLayout.CENTER);
        sendRow.add(enviarBtn, BorderLayout.EAST);
        
        bottomPanel.add(sendRow, BorderLayout.CENTER);
        
        // Ensamblar componentes principales
        split.setLeftComponent(sidebar);
        split.setRightComponent(tabbedPane);
        
        add(topBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        actualizarSeleccion();
        campoMensaje.requestFocus();
        actualizarColoresPestanas();
    }
    
    private JButton crearBotonTipo(String texto, Color color) {
        JButton btn = new JButton(texto);
        btn.setBackground(BG_INPUT);
        btn.setForeground(color);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(color, 1));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(90, 30));
        return btn;
    }
    
    // Actualiza la interfaz según el tipo de mensaje seleccionado
    private void actualizarSeleccion() {
        btnBroadcast.setBorder(BorderFactory.createLineBorder(COLOR_BROADCAST, 
            tipoSeleccionado.equals("broadcast") ? 2 : 1));
        btnUnicast.setBorder(BorderFactory.createLineBorder(COLOR_UNICAST, 
            tipoSeleccionado.equals("unicast") ? 2 : 1));
        btnMulticast.setBorder(BorderFactory.createLineBorder(COLOR_MULTICAST, 
            tipoSeleccionado.equals("multicast") ? 2 : 1));
        btnAnycast.setBorder(BorderFactory.createLineBorder(COLOR_ANYCAST, 
            tipoSeleccionado.equals("anycast") ? 2 : 1));
        
        destinoPanel.setVisible(tipoSeleccionado.equals("unicast") || tipoSeleccionado.equals("multicast"));
        
        comboDestino.removeAllItems();
        if (tipoSeleccionado.equals("unicast")) {
            for (int i = 0; i < listaUsuariosModel.size(); i++) {
                String u = listaUsuariosModel.get(i);
                if (!u.equals(nombreUsuario)) comboDestino.addItem(u);
            }
            if (comboDestino.getItemCount() == 0) comboDestino.addItem("(Sin usuarios)");
        } else if (tipoSeleccionado.equals("multicast")) {
            for (int i = 0; i < listaGruposModel.size(); i++) {
                comboDestino.addItem(listaGruposModel.get(i));
            }
            if (comboDestino.getItemCount() == 0) comboDestino.addItem("(Sin grupos)");
        }
    }
    
    // Diálogo para crear un nuevo grupo
    private void abrirDialogoCrearGrupo() {
        JDialog dialog = new JDialog(this, "Crear Grupo", true);
        dialog.setSize(500, 550);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG_MAIN);
        
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBackground(BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        
        // Campo nombre del grupo
        JPanel nombrePanel = new JPanel(new BorderLayout());
        nombrePanel.setBackground(BG_MAIN);
        JLabel nombreLabel = new JLabel("NOMBRE DEL GRUPO");
        nombreLabel.setForeground(TEXT);
        nombreLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        nombreLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        nombrePanel.add(nombreLabel, BorderLayout.NORTH);
        
        JTextField nombreGrupo = new JTextField();
        nombreGrupo.setBackground(BG_INPUT);
        nombreGrupo.setForeground(TEXT);
        nombreGrupo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nombreGrupo.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        nombrePanel.add(nombreGrupo, BorderLayout.CENTER);
        
        // Selección de miembros
        JPanel miembrosPanel = new JPanel(new BorderLayout());
        miembrosPanel.setBackground(BG_MAIN);
        miembrosPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER),
            "MIEMBROS", TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 10), TEXT_SEC));
        
        DefaultListModel<String> disponiblesModel = new DefaultListModel<>();
        for (int i = 0; i < listaUsuariosModel.size(); i++) {
            String u = listaUsuariosModel.get(i);
            if (!u.equals(nombreUsuario)) disponiblesModel.addElement(u);
        }
        
        JList<String> listaDisponibles = new JList<>(disponiblesModel);
        listaDisponibles.setBackground(BG_INPUT);
        listaDisponibles.setForeground(TEXT);
        listaDisponibles.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        DefaultListModel<String> seleccionadosModel = new DefaultListModel<>();
        JList<String> listaSeleccionados = new JList<>(seleccionadosModel);
        listaSeleccionados.setBackground(BG_INPUT);
        listaSeleccionados.setForeground(TEXT);
        listaSeleccionados.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        // Botones mover entre listas
        JPanel botonesMov = new JPanel(new GridLayout(2, 1, 5, 15));
        botonesMov.setBackground(BG_MAIN);
        botonesMov.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        
        JButton btnAgregar = new JButton("→ Agregar →");
        btnAgregar.setBackground(ACCENT);
        btnAgregar.setForeground(Color.WHITE);
        btnAgregar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnAgregar.setFocusPainted(false);
        btnAgregar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAgregar.addActionListener(e -> {
            String selected = listaDisponibles.getSelectedValue();
            if (selected != null && !seleccionadosModel.contains(selected)) {
                seleccionadosModel.addElement(selected);
                disponiblesModel.removeElement(selected);
            }
        });
        
        JButton btnQuitar = new JButton("← Quitar ←");
        btnQuitar.setBackground(new Color(70, 70, 85));
        btnQuitar.setForeground(TEXT);
        btnQuitar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnQuitar.setFocusPainted(false);
        btnQuitar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnQuitar.addActionListener(e -> {
            String selected = listaSeleccionados.getSelectedValue();
            if (selected != null) {
                disponiblesModel.addElement(selected);
                seleccionadosModel.removeElement(selected);
            }
        });
        
        botonesMov.add(btnAgregar);
        botonesMov.add(btnQuitar);
        
        JPanel listasPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        listasPanel.setBackground(BG_MAIN);
        listasPanel.add(new JScrollPane(listaDisponibles));
        listasPanel.add(botonesMov);
        listasPanel.add(new JScrollPane(listaSeleccionados));
        
        miembrosPanel.add(listasPanel, BorderLayout.CENTER);
        
        JLabel infoLabel = new JLabel("Tú serás agregado automáticamente");
        infoLabel.setForeground(TEXT_SEC);
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        miembrosPanel.add(infoLabel, BorderLayout.SOUTH);
        
        // Botones de acción
        JPanel botonesPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 20));
        botonesPanel.setBackground(BG_MAIN);
        
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setBackground(new Color(60, 60, 75));
        btnCancelar.setForeground(TEXT);
        btnCancelar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancelar.setFocusPainted(false);
        btnCancelar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelar.addActionListener(e -> dialog.dispose());
        
        JButton btnCrear = new JButton("✦ Crear grupo ✦");
        btnCrear.setBackground(ACCENT);
        btnCrear.setForeground(Color.WHITE);
        btnCrear.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCrear.setFocusPainted(false);
        btnCrear.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCrear.addActionListener(e -> {
            String nombre = nombreGrupo.getText().trim();
            if (nombre.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Ingresa un nombre para el grupo");
                return;
            }
            
            StringBuilder miembros = new StringBuilder();
            for (int i = 0; i < seleccionadosModel.size(); i++) {
                if (miembros.length() > 0) miembros.append(",");
                miembros.append(seleccionadosModel.get(i));
            }
            
            salidaTCP.println("/crear_grupo_con_miembros " + nombre + "|" + miembros.toString());
            dialog.dispose();
        });
        
        botonesPanel.add(btnCancelar);
        botonesPanel.add(btnCrear);
        
        panel.add(nombrePanel, BorderLayout.NORTH);
        panel.add(miembrosPanel, BorderLayout.CENTER);
        panel.add(botonesPanel, BorderLayout.SOUTH);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    private void conectarAlServidor() {
        try {
            socketTCP = new Socket(servidorIP, PUERTO_TCP);
            salidaTCP = new PrintWriter(new OutputStreamWriter(socketTCP.getOutputStream(), "UTF-8"), true);
            entradaTCP = new BufferedReader(new InputStreamReader(socketTCP.getInputStream(), "UTF-8"));
            
            socketUDP = new DatagramSocket();
            salidaTCP.println(nombreUsuario);
            salidaTCP.println(socketUDP.getLocalPort());
            
            // Hilo receptor de mensajes
            new Thread(() -> {
                try { 
                    String line; 
                    while ((line = entradaTCP.readLine()) != null) {
                        procesarMensaje(line);
                    }
                } catch(IOException e) { 
                    agregarMensajeSistema("Desconectado del servidor");
                }
            }).start();
            
            // Hilo para actualizar listas periódicamente
            new Thread(() -> { 
                while (true) { 
                    try { 
                        Thread.sleep(5000); 
                    } catch(Exception e) {}
                    if (salidaTCP != null) { 
                        salidaTCP.println("/list"); 
                        salidaTCP.println("/ver_grupos"); 
                    } 
                } 
            }).start();
            
        } catch(Exception e) {
            agregarMensajeSistema("Error de conexión: " + e.getMessage());
        }
    }
    
    private void procesarMensaje(String linea) {
        // Confirmación de anycast
        if (linea.startsWith("/anycast_confirmado")) {
            agregarMensajeAnycastConfirmado();
            return;
        }
        
        // Mensaje privado
        if (linea.startsWith("/privado_json ")) {
            String jsonStr = linea.substring(14);
            Map<String, String> datos = parsearJSON(jsonStr);
            String emisor = datos.get("emisor");
            String contenido = datos.get("contenido");
            String protocolo = datos.get("protocolo");
            String anycast = datos.get("anycast");
            
            if (!contenido.isEmpty() && !emisor.equals(nombreUsuario)) {
                if ("true".equals(anycast)) {
                    agregarMensajePrivadoRecibidoAnycast(emisor, contenido, protocolo);
                } else {
                    agregarMensajePrivadoRecibido(emisor, contenido, protocolo);
                }
            }
            return;
        }
        
        // Mensaje broadcast
        if (linea.startsWith("/broadcast_json ")) {
            String jsonStr = linea.substring(16);
            Map<String, String> datos = parsearJSON(jsonStr);
            String emisor = datos.get("emisor");
            String contenido = datos.get("contenido");
            String protocolo = datos.get("protocolo");
            
            if (!contenido.isEmpty() && !emisor.equals(nombreUsuario)) {
                agregarMensajeBroadcast(contenido, emisor, protocolo);
            }
            return;
        }
        
        // Mensaje multicast
        if (linea.startsWith("/multicast_json ")) {
            String jsonStr = linea.substring(16);
            Map<String, String> datos = parsearJSON(jsonStr);
            String emisor = datos.get("emisor");
            String contenido = datos.get("contenido");
            String protocolo = datos.get("protocolo");
            String grupo = datos.get("destino");
            
            if (!contenido.isEmpty() && !emisor.equals(nombreUsuario)) {
                agregarMensajeGrupalRecibido(grupo, emisor, contenido, protocolo);
            }
            return;
        }
        
        // Lista de usuarios
        if (linea.startsWith("/lista_usuarios ")) {
            String[] parts = linea.substring(16).split(",");
            SwingUtilities.invokeLater(() -> { 
                listaUsuariosModel.clear();
                for (String u : parts) if (!u.trim().isEmpty()) listaUsuariosModel.addElement(u.trim());
                if (tipoSeleccionado.equals("unicast")) actualizarSeleccion();
            });
            return;
        }
        
        // Nuevo grupo
        if (linea.startsWith("/grupo ")) {
            String grupo = linea.substring(6).trim();
            SwingUtilities.invokeLater(() -> { 
                if (!listaGruposModel.contains(grupo)) listaGruposModel.addElement(grupo);
                if (tipoSeleccionado.equals("multicast")) actualizarSeleccion();
            });
            return;
        }
        
        // Grupo creado
        if (linea.startsWith("/grupo_creado ")) {
            final String grupo = linea.substring(13).trim();
            SwingUtilities.invokeLater(() -> {
                if (!listaGruposModel.contains(grupo)) listaGruposModel.addElement(grupo);
                if (tipoSeleccionado.equals("multicast")) actualizarSeleccion();
                actualizarColoresPestanas();
            });
            return;
        }
        
        // Mensajes del sistema
        if (linea.startsWith("[SISTEMA] ")) {
            agregarMensajeSistema(linea.substring(10));
            return;
        }
        
        // Mensajes de error
        if (linea.startsWith("[ERROR] ")) {
            agregarMensajeSistema("⚠ " + linea.substring(8));
            return;
        }
    }
    
    // ==================== MÉTODOS DE VISUALIZACIÓN DE MENSAJES ====================
    
    private void agregarMensajeAnycastConfirmado() {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String htmlMsg = String.format(
                "<div style='text-align:center; margin:10px 0;'>" +
                "  <div style='display:inline-block; background-color:%s; border-radius:20px; padding:6px 16px;'>" +
                "    <span style='color:%s; font-size:11px; letter-spacing:1px;'>✧ [%s] MENSAJE ANYCAST ENVIADO ✧</span>" +
                "  </div>" +
                "</div>",
                colorToHex(new Color(45, 35, 55)), colorToHex(COLOR_ANYCAST), hora
            );
            
            htmlContentGeneral.append(htmlMsg);
            actualizarPanelHTML(areaChatGeneral, htmlContentGeneral);
        });
    }
    
    private void agregarMensajeBroadcast(String mensaje, String emisor, String protocolo) {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            boolean esPropio = emisor.equals(nombreUsuario);
            
            Color bgColor = esPropio ? BG_MIS_PROPIOS : BG_OTROS;
            Color nombreColor = esPropio ? COLOR_MIS_NOMBRE : COLOR_OTRO_NOMBRE;
            String alineacion = esPropio ? "right" : "left";
            String borderRadius = esPropio ? "18px 18px 4px 18px" : "18px 18px 18px 4px";
            
            String htmlMsg = String.format(
                "<div style='text-align:%s; margin:12px 0;'>" +
                "  <div style='display:inline-block; background-color:%s; border-radius:%s; padding:10px 16px; max-width:75%%; box-shadow:0 1px 3px rgba(0,0,0,0.3);'>" +
                "    <span style='color:%s; font-weight:bold; font-size:13px;'>%s</span><br>" +
                "    <span style='color:#f0f0f0; font-size:14px; line-height:1.5;'>%s</span><br>" +
                "    <span style='color:#aaa; font-size:10px;'>[%s] %s</span>" +
                "  </div>" +
                "</div>",
                alineacion, colorToHex(bgColor), borderRadius, colorToHex(nombreColor), 
                emisor, mensaje, protocolo, hora
            );
            
            htmlContentGeneral.append(htmlMsg);
            actualizarPanelHTML(areaChatGeneral, htmlContentGeneral);
        });
    }
    
    private void agregarMensajePrivadoRecibido(String emisor, String mensaje, String protocolo) {
        agregarMensajePrivadoRecibidoConMarca(emisor, mensaje, protocolo, false);
    }
    
    private void agregarMensajePrivadoRecibidoAnycast(String emisor, String mensaje, String protocolo) {
        agregarMensajePrivadoRecibidoConMarca(emisor, mensaje, protocolo, true);
    }
    
    private void agregarMensajePrivadoRecibidoConMarca(String emisor, String mensaje, String protocolo, boolean esAnycast) {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String tabId = emisor;
            
            StringBuilder historial = historialPrivados.get(emisor);
            if (historial == null) {
                historial = new StringBuilder();
                historialPrivados.put(emisor, historial);
            }
            
            JEditorPane area = chatsPrivados.get(emisor);
            
            if (area == null) {
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBackground(BG_CHAT);
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                
                area = new JEditorPane("text/html", getHtmlHeader() + getHtmlFooter());
                area.setEditable(false);
                area.setBackground(BG_CHAT);
                
                JScrollPane scroll = new JScrollPane(area);
                scroll.setBorder(BorderFactory.createLineBorder(BORDER));
                panel.add(scroll, BorderLayout.CENTER);
                
                chatsPrivados.put(emisor, area);
                tabbedPane.addTab(tabId, panel);
                actualizarColoresPestanas();
            }
            
            String etiqueta = esAnycast ? " [anycast]" : "";
            Color colorNombre = esAnycast ? COLOR_ANYCAST : COLOR_OTRO_NOMBRE;
            
            String htmlMsg = String.format(
                "<div style='text-align:left; margin:12px 0;'>" +
                "  <div style='display:inline-block; background-color:%s; border-radius:18px 18px 18px 4px; padding:10px 16px; max-width:75%%; box-shadow:0 1px 3px rgba(0,0,0,0.3);'>" +
                "    <span style='color:%s; font-weight:bold; font-size:13px;'>%s%s</span><br>" +
                "    <span style='color:#f0f0f0; font-size:14px; line-height:1.5;'>%s</span><br>" +
                "    <span style='color:#aaa; font-size:10px;'>[%s] %s</span>" +
                "  </div>" +
                "</div>",
                colorToHex(BG_OTROS), colorToHex(colorNombre), emisor, etiqueta, mensaje, protocolo, hora
            );
            
            historial.append(htmlMsg);
            actualizarPanelHTML(area, historial);
            
            int idx = tabbedPane.indexOfTab(tabId);
            if (idx != -1 && tabbedPane.getSelectedIndex() != idx) {
                int count = notificaciones.getOrDefault(emisor, 0) + 1;
                notificaciones.put(emisor, count);
                tabbedPane.setTitleAt(idx, tabId + " ● " + count);
            }
        });
    }
    
    private void agregarMensajePrivadoEnviado(String destino, String mensaje, String protocolo) {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String tabId = destino;
            
            StringBuilder historial = historialPrivados.get(destino);
            if (historial == null) {
                historial = new StringBuilder();
                historialPrivados.put(destino, historial);
            }
            
            JEditorPane area = chatsPrivados.get(destino);
            
            if (area == null) {
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBackground(BG_CHAT);
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                
                area = new JEditorPane("text/html", getHtmlHeader() + getHtmlFooter());
                area.setEditable(false);
                area.setBackground(BG_CHAT);
                
                JScrollPane scroll = new JScrollPane(area);
                scroll.setBorder(BorderFactory.createLineBorder(BORDER));
                panel.add(scroll, BorderLayout.CENTER);
                
                chatsPrivados.put(destino, area);
                tabbedPane.addTab(tabId, panel);
                actualizarColoresPestanas();
            }
            
            String htmlMsg = String.format(
                "<div style='text-align:right; margin:12px 0;'>" +
                "  <div style='display:inline-block; background-color:%s; border-radius:18px 18px 4px 18px; padding:10px 16px; max-width:75%%; box-shadow:0 1px 3px rgba(0,0,0,0.3);'>" +
                "    <span style='color:%s; font-weight:bold; font-size:13px;'>Tú</span><br>" +
                "    <span style='color:#f0f0f0; font-size:14px; line-height:1.5;'>%s</span><br>" +
                "    <span style='color:#aaa; font-size:10px;'>[%s] %s</span>" +
                "  </div>" +
                "</div>",
                colorToHex(BG_MIS_PROPIOS), colorToHex(COLOR_MIS_NOMBRE), mensaje, protocolo, hora
            );
            
            historial.append(htmlMsg);
            actualizarPanelHTML(area, historial);
        });
    }
    
    private void agregarMensajeGrupalRecibido(String grupo, String emisor, String mensaje, String protocolo) {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String tabId = grupo;
            
            StringBuilder historial = historialGrupales.get(grupo);
            if (historial == null) {
                historial = new StringBuilder();
                historialGrupales.put(grupo, historial);
            }
            
            JEditorPane area = chatsGrupales.get(grupo);
            
            if (area == null) {
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBackground(BG_CHAT);
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                
                area = new JEditorPane("text/html", getHtmlHeader() + getHtmlFooter());
                area.setEditable(false);
                area.setBackground(BG_CHAT);
                
                JScrollPane scroll = new JScrollPane(area);
                scroll.setBorder(BorderFactory.createLineBorder(BORDER));
                panel.add(scroll, BorderLayout.CENTER);
                
                chatsGrupales.put(grupo, area);
                tabbedPane.addTab(tabId, panel);
                actualizarColoresPestanas();
            }
            
            String htmlMsg = String.format(
                "<div style='text-align:left; margin:12px 0;'>" +
                "  <div style='display:inline-block; background-color:%s; border-radius:18px 18px 18px 4px; padding:10px 16px; max-width:75%%; box-shadow:0 1px 3px rgba(0,0,0,0.3);'>" +
                "    <span style='color:%s; font-weight:bold; font-size:13px;'>%s</span><br>" +
                "    <span style='color:#f0f0f0; font-size:14px; line-height:1.5;'>%s</span><br>" +
                "    <span style='color:#aaa; font-size:10px;'>[%s] %s</span>" +
                "  </div>" +
                "</div>",
                colorToHex(BG_OTROS), colorToHex(COLOR_OTRO_NOMBRE), emisor, mensaje, protocolo, hora
            );
            
            historial.append(htmlMsg);
            actualizarPanelHTML(area, historial);
            
            int idx = tabbedPane.indexOfTab(tabId);
            if (idx != -1 && tabbedPane.getSelectedIndex() != idx) {
                int count = notificaciones.getOrDefault(grupo, 0) + 1;
                notificaciones.put(grupo, count);
                tabbedPane.setTitleAt(idx, tabId + " ● " + count);
            }
        });
    }
    
    private void agregarMensajeGrupalEnviado(String grupo, String mensaje, String protocolo) {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String tabId = grupo;
            
            StringBuilder historial = historialGrupales.get(grupo);
            if (historial == null) {
                historial = new StringBuilder();
                historialGrupales.put(grupo, historial);
            }
            
            JEditorPane area = chatsGrupales.get(grupo);
            
            if (area == null) {
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBackground(BG_CHAT);
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                
                area = new JEditorPane("text/html", getHtmlHeader() + getHtmlFooter());
                area.setEditable(false);
                area.setBackground(BG_CHAT);
                
                JScrollPane scroll = new JScrollPane(area);
                scroll.setBorder(BorderFactory.createLineBorder(BORDER));
                panel.add(scroll, BorderLayout.CENTER);
                
                chatsGrupales.put(grupo, area);
                tabbedPane.addTab(tabId, panel);
                actualizarColoresPestanas();
            }
            
            String htmlMsg = String.format(
                "<div style='text-align:right; margin:12px 0;'>" +
                "  <div style='display:inline-block; background-color:%s; border-radius:18px 18px 4px 18px; padding:10px 16px; max-width:75%%; box-shadow:0 1px 3px rgba(0,0,0,0.3);'>" +
                "    <span style='color:%s; font-weight:bold; font-size:13px;'>Tú</span><br>" +
                "    <span style='color:#f0f0f0; font-size:14px; line-height:1.5;'>%s</span><br>" +
                "    <span style='color:#aaa; font-size:10px;'>[%s] %s</span>" +
                "  </div>" +
                "</div>",
                colorToHex(BG_MIS_PROPIOS), colorToHex(COLOR_MIS_NOMBRE), mensaje, protocolo, hora
            );
            
            historial.append(htmlMsg);
            actualizarPanelHTML(area, historial);
        });
    }
    
    private void agregarMensajeSistema(String msg) {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String htmlMsg = String.format(
                "<div style='text-align:center; margin:10px 0;'>" +
                "  <div style='display:inline-block; background-color:%s; border-radius:16px; padding:4px 12px;'>" +
                "    <span style='color:%s; font-size:11px;'>[%s] %s</span>" +
                "  </div>" +
                "</div>",
                colorToHex(BG_OTROS), colorToHex(TEXT_SEC), hora, msg
            );
            
            htmlContentGeneral.append(htmlMsg);
            actualizarPanelHTML(areaChatGeneral, htmlContentGeneral);
        });
    }
    
    // ==================== MÉTODOS DE GESTIÓN DE CHATS ====================
    
    private void abrirChatPrivado(String usuario) {
        if (usuario.equals(nombreUsuario)) return;
        String tabId = usuario;
        
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            String title = tabbedPane.getTitleAt(i);
            if (title.equals(tabId) || title.startsWith(tabId + " ●")) {
                tabbedPane.setSelectedIndex(i);
                notificaciones.remove(usuario);
                tabbedPane.setTitleAt(i, tabId);
                actualizarColoresPestanas();
                return;
            }
        }
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_CHAT);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JEditorPane area = new JEditorPane("text/html", getHtmlHeader() + getHtmlFooter());
        area.setEditable(false);
        area.setBackground(BG_CHAT);
        
        StringBuilder historial = historialPrivados.get(usuario);
        if (historial != null && historial.length() > 0) {
            actualizarPanelHTML(area, historial);
        }
        
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        
        chatsPrivados.put(usuario, area);
        tabbedPane.addTab(tabId, panel);
        tabbedPane.setSelectedComponent(panel);
        actualizarColoresPestanas();
    }
    
    private void abrirChatGrupal(String grupo) {
        String tabId = grupo;
        
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            String title = tabbedPane.getTitleAt(i);
            if (title.equals(tabId) || title.startsWith(tabId + " ●")) {
                tabbedPane.setSelectedIndex(i);
                notificaciones.remove(grupo);
                tabbedPane.setTitleAt(i, tabId);
                actualizarColoresPestanas();
                return;
            }
        }
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_CHAT);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JEditorPane area = new JEditorPane("text/html", getHtmlHeader() + getHtmlFooter());
        area.setEditable(false);
        area.setBackground(BG_CHAT);
        
        StringBuilder historial = historialGrupales.get(grupo);
        if (historial != null && historial.length() > 0) {
            actualizarPanelHTML(area, historial);
        }
        
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        
        chatsGrupales.put(grupo, area);
        tabbedPane.addTab(tabId, panel);
        tabbedPane.setSelectedComponent(panel);
        actualizarColoresPestanas();
    }
    
    // Envía un mensaje al servidor según el tipo seleccionado
    private void enviarMensaje() {
        String texto = campoMensaje.getText().trim();
        if (texto.isEmpty()) return;
        
        boolean usarUDP = radioUDP.isSelected();
        String protocolo = usarUDP ? "UDP" : "TCP";
        
        switch(tipoSeleccionado) {
            case "broadcast":
                String jsonBroadcast = crearMensajeJSON("broadcast", null, texto, protocolo);
                salidaTCP.println("/broadcast_json " + jsonBroadcast);
                agregarMensajeBroadcast(texto, nombreUsuario, protocolo);
                break;
                
            case "unicast":
                String destino = (String) comboDestino.getSelectedItem();
                if (destino == null || destino.isEmpty() || destino.equals("(Sin usuarios)")) {
                    agregarMensajeSistema("Selecciona un usuario");
                    return;
                }
                String jsonUnicast = crearMensajeJSON("unicast", destino, texto, protocolo);
                salidaTCP.println("/unicast_json " + destino + "||" + jsonUnicast);
                abrirChatPrivado(destino);
                agregarMensajePrivadoEnviado(destino, texto, protocolo);
                break;
                
            case "multicast":
                String grupo = (String) comboDestino.getSelectedItem();
                if (grupo == null || grupo.isEmpty() || grupo.equals("(Sin grupos)")) {
                    agregarMensajeSistema("Selecciona un grupo");
                    return;
                }
                String jsonMulticast = crearMensajeJSON("multicast", grupo, texto, protocolo);
                salidaTCP.println("/multicast_json " + grupo + "||" + jsonMulticast);
                abrirChatGrupal(grupo);
                agregarMensajeGrupalEnviado(grupo, texto, protocolo);
                break;
                
            case "anycast":
                String jsonAnycast = crearMensajeJSON("anycast", null, texto, protocolo);
                salidaTCP.println("/anycast_json " + jsonAnycast);
                break;
        }
        
        campoMensaje.setText("");
    }
    
    // Método principal - Punto de entrada del cliente
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch(Exception e) {}
        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }
}
