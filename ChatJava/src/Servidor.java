/**
 * Servidor de Chat con Sockets TCP/UDP
 * Implementa los tipos de comunicación: Broadcast, Unicast, Multicast y Anycast
 * Utiliza JSON como formato de intercambio de mensajes (Capa de Presentación)
 * 
 * @author Equipo de Desarrollo
 * @version 2.0
 */

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public class Servidor {
    
    // Constantes de configuración del servidor
    private static final int PUERTO_TCP = 9999;      // Puerto para comunicación TCP
    private static final int PUERTO_UDP = 9998;      // Puerto para comunicación UDP
    
    // Estructuras de datos para gestión de clientes y grupos
    private static List<InfoCliente> clientes = new CopyOnWriteArrayList<>();
    private static DatagramSocket socketUDP;
    private static Map<String, List<String>> gruposMulticast = new ConcurrentHashMap<>();
    private static Random random = new Random();
    
    /**
     * Clase interna que almacena la información de cada cliente conectado
     */
    static class InfoCliente {
        Socket socketTCP;
        String nombre;
        InetAddress direccionUDP;
        int puertoUDP;
        List<String> grupos;
        
        InfoCliente(Socket socketTCP, String nombre, InetAddress direccionUDP, int puertoUDP) {
            this.socketTCP = socketTCP;
            this.nombre = nombre;
            this.direccionUDP = direccionUDP;
            this.puertoUDP = puertoUDP;
            this.grupos = new CopyOnWriteArrayList<>();
        }
    }
    
    /**
     * Manejador de cliente en un hilo independiente
     * Gestiona la comunicación con un cliente específico
     */
    static class ManejadorCliente extends Thread {
        private Socket socket;
        private PrintWriter salida;
        private BufferedReader entrada;
        private InfoCliente miInfo;
        
        ManejadorCliente(Socket socket, InfoCliente info) {
            this.socket = socket;
            this.miInfo = info;
        }
        
        @Override
        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                
                String linea;
                while ((linea = entrada.readLine()) != null) {
                    procesarComando(linea);
                }
            } catch (IOException e) {
                System.out.println("[" + miInfo.nombre + "] Desconectado");
            } finally {
                // Limpiar recursos al desconectarse
                for (String grupo : miInfo.grupos) {
                    List<String> miembros = gruposMulticast.get(grupo);
                    if (miembros != null) {
                        miembros.remove(miInfo.nombre);
                        if (miembros.isEmpty()) {
                            gruposMulticast.remove(grupo);
                        }
                    }
                }
                clientes.remove(miInfo);
                for (InfoCliente c : clientes) {
                    try {
                        PrintWriter pw = new PrintWriter(new OutputStreamWriter(c.socketTCP.getOutputStream(), "UTF-8"), true);
                        pw.println("[SISTEMA] " + miInfo.nombre + " abandonó el chat");
                    } catch(IOException e) {}
                }
                try { socket.close(); } catch(IOException e) {}
                System.out.println("[ACTIVOS] Usuarios conectados: " + clientes.size());
            }
        }
        
        /**
         * Procesa los comandos enviados por el cliente
         * @param cmd Comando recibido
         */
        private void procesarComando(String cmd) {
            try {
                // Listar usuarios conectados
                if (cmd.equals("/list")) {
                    listarUsuarios();
                }
                // Listar grupos disponibles
                else if (cmd.equals("/ver_grupos")) {
                    listarGrupos();
                }
                // Broadcast: reenviar mensaje a todos los clientes excepto al emisor
                else if (cmd.startsWith("/broadcast_json ")) {
                    String jsonStr = cmd.substring(16);
                    JSONObject json = new JSONObject(jsonStr);
                    
                    for (InfoCliente c : clientes) {
                        if (c != miInfo) {
                            PrintWriter pw = new PrintWriter(new OutputStreamWriter(c.socketTCP.getOutputStream(), "UTF-8"), true);
                            pw.println("/broadcast_json " + json.toString());
                        }
                    }
                }
                // Unicast: enviar mensaje privado a un destinatario específico
                else if (cmd.startsWith("/unicast_json ")) {
                    String resto = cmd.substring(14);
                    int separatorPos = resto.indexOf("||");
                    if (separatorPos > 0) {
                        String destino = resto.substring(0, separatorPos).trim();
                        String jsonStr = resto.substring(separatorPos + 2);
                        
                        for (InfoCliente c : clientes) {
                            if (c.nombre.equals(destino)) {
                                PrintWriter pw = new PrintWriter(new OutputStreamWriter(c.socketTCP.getOutputStream(), "UTF-8"), true);
                                pw.println("/privado_json " + jsonStr);
                                return;
                            }
                        }
                        salida.println("[ERROR] Usuario '" + destino + "' no encontrado");
                    }
                }
                // Multicast: enviar mensaje a todos los miembros de un grupo
                else if (cmd.startsWith("/multicast_json ")) {
                    String resto = cmd.substring(16);
                    int separatorPos = resto.indexOf("||");
                    
                    if (separatorPos > 0) {
                        String grupo = resto.substring(0, separatorPos).trim();
                        String jsonStr = resto.substring(separatorPos + 2);
                        
                        if (!gruposMulticast.containsKey(grupo)) {
                            salida.println("[ERROR] Grupo '" + grupo + "' no existe");
                            return;
                        }
                        
                        List<String> miembros = gruposMulticast.get(grupo);
                        
                        // Reenviar a todos los miembros excepto al emisor
                        for (String miembro : miembros) {
                            if (miembro.equals(miInfo.nombre)) continue;
                            
                            for (InfoCliente c : clientes) {
                                if (c.nombre.equals(miembro)) {
                                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(c.socketTCP.getOutputStream(), "UTF-8"), true);
                                    pw.println("/multicast_json " + jsonStr);
                                    break;
                                }
                            }
                        }
                    }
                }
                // Anycast: enviar mensaje a un usuario aleatorio
                else if (cmd.startsWith("/anycast_json ")) {
                    String jsonStr = cmd.substring(14);
                    JSONObject json = new JSONObject(jsonStr);
                    json.put("anycast", true);  // Marcar como anycast
                    jsonStr = json.toString();
                    
                    List<InfoCliente> candidatos = new ArrayList<>();
                    for (InfoCliente c : clientes) {
                        if (c != miInfo) candidatos.add(c);
                    }
                    if (!candidatos.isEmpty()) {
                        InfoCliente elegido = candidatos.get(random.nextInt(candidatos.size()));
                        PrintWriter pw = new PrintWriter(new OutputStreamWriter(elegido.socketTCP.getOutputStream(), "UTF-8"), true);
                        pw.println("/privado_json " + jsonStr);
                        salida.println("/anycast_confirmado");
                    } else {
                        salida.println("[ERROR] No hay usuarios disponibles");
                    }
                }
                // Crear grupo con miembros específicos
                else if (cmd.startsWith("/crear_grupo_con_miembros ")) {
                    String resto = cmd.substring(25);
                    String[] parts = resto.split("\\|");
                    String nombre = parts[0].trim();
                    String miembrosStr = parts.length > 1 ? parts[1] : "";
                    
                    if (gruposMulticast.containsKey(nombre)) {
                        salida.println("[ERROR] El grupo ya existe");
                        return;
                    }
                    
                    List<String> miembros = new CopyOnWriteArrayList<>();
                    miembros.add(miInfo.nombre);  // Agregar al creador
                    
                    if (!miembrosStr.isEmpty()) {
                        String[] miembrosArray = miembrosStr.split(",");
                        for (String m : miembrosArray) {
                            String miembroTrim = m.trim();
                            if (!miembroTrim.isEmpty() && !miembros.contains(miembroTrim)) {
                                miembros.add(miembroTrim);
                            }
                        }
                    }
                    
                    gruposMulticast.put(nombre, miembros);
                    miInfo.grupos.add(nombre);
                    
                    // Notificar a todos los miembros del nuevo grupo
                    for (String miembro : miembros) {
                        for (InfoCliente c : clientes) {
                            if (c.nombre.equals(miembro)) {
                                if (!c.grupos.contains(nombre)) {
                                    c.grupos.add(nombre);
                                }
                                PrintWriter pw = new PrintWriter(new OutputStreamWriter(c.socketTCP.getOutputStream(), "UTF-8"), true);
                                pw.println("/grupo_creado " + nombre);
                                break;
                            }
                        }
                    }
                    
                    salida.println("/grupo_creado " + nombre);
                    System.out.println("[GRUPO] '" + nombre + "' creado por " + miInfo.nombre);
                }
            } catch(Exception e) {
                e.printStackTrace();
                salida.println("[ERROR] " + e.getMessage());
            }
        }
        
        /**
         * Envía al cliente la lista de usuarios conectados
         */
        private void listarUsuarios() {
            StringBuilder lista = new StringBuilder("/lista_usuarios ");
            for (int i = 0; i < clientes.size(); i++) {
                if (i > 0) lista.append(",");
                lista.append(clientes.get(i).nombre);
            }
            salida.println(lista.toString());
        }
        
        /**
         * Envía al cliente la lista de grupos a los que pertenece
         */
        private void listarGrupos() {
            for (String grupo : gruposMulticast.keySet()) {
                if (miInfo.grupos.contains(grupo)) {
                    salida.println("/grupo " + grupo);
                }
            }
        }
    }
    
    /**
     * Listener de comunicación UDP
     * Reenvía datagramas entre clientes
     */
    static class UDPListener extends Thread {
        @Override
        public void run() {
            try {
                socketUDP = new DatagramSocket(PUERTO_UDP);
                System.out.println("[UDP] Servidor UDP iniciado en puerto " + PUERTO_UDP);
                byte[] buffer = new byte[65536];
                
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socketUDP.receive(packet);
                    
                    // Reenviar el datagrama a todos los clientes excepto al emisor
                    for (InfoCliente c : clientes) {
                        if (!c.direccionUDP.equals(packet.getAddress()) || c.puertoUDP != packet.getPort()) {
                            DatagramPacket outPacket = new DatagramPacket(
                                packet.getData(), packet.getLength(),
                                c.direccionUDP, c.puertoUDP);
                            socketUDP.send(outPacket);
                        }
                    }
                }
            } catch(Exception e) {
                System.out.println("[UDP] Error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Obtiene la dirección IP local del servidor
     * @return Dirección IP local
     */
    private static String getLocalIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch(Exception e) {}
        return "127.0.0.1";
    }
    
    /**
     * Método principal - Punto de entrada del servidor
     * Inicia los servicios TCP y UDP
     */
    public static void main(String[] args) {
        System.out.println("================================================");
        System.out.println("     SERVIDOR CHAT - VERSIÓN JSON");
        System.out.println("================================================");
        System.out.println();
        
        String ipLocal = getLocalIP();
        System.out.println("[INFO] Servidor en IP: " + ipLocal);
        System.out.println("[INFO] Puerto TCP: " + PUERTO_TCP);
        System.out.println("[INFO] Puerto UDP: " + PUERTO_UDP);
        System.out.println();
        
        new UDPListener().start();
        
        try (ServerSocket server = new ServerSocket(PUERTO_TCP, 50, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("[OK] Servidor listo esperando conexiones...\n");
            
            // Bucle principal de aceptación de conexiones
            while (true) {
                Socket clientSocket = server.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
                
                String nombre = in.readLine();
                int puertoUDP = Integer.parseInt(in.readLine());
                InetAddress ipUDP = clientSocket.getInetAddress();
                
                System.out.println("[CONEXION] " + nombre + " se ha conectado");
                
                // Registrar nuevo cliente
                InfoCliente nuevo = new InfoCliente(clientSocket, nombre, ipUDP, puertoUDP);
                clientes.add(nuevo);
                
                // Notificar conexión exitosa
                PrintWriter pwNuevo = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
                pwNuevo.println("[SISTEMA] Conectado como " + nombre);
                
                // Notificar a otros usuarios
                for (InfoCliente c : clientes) {
                    if (c != nuevo) {
                        PrintWriter pw = new PrintWriter(new OutputStreamWriter(c.socketTCP.getOutputStream(), "UTF-8"), true);
                        pw.println("[SISTEMA] " + nombre + " se unió al chat");
                    }
                }
                
                // Iniciar hilo manejador para este cliente
                new ManejadorCliente(clientSocket, nuevo).start();
                System.out.println("[ACTIVOS] Usuarios: " + clientes.size());
            }
        } catch(Exception e) {
            System.err.println("[ERROR FATAL] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
