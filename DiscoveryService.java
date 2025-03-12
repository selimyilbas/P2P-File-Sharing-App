package p2p;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DiscoveryService - Handles peer discovery via UDP broadcast
 * - Uses limited scope flooding with TTL to prevent network overwhelming
 * - Cross-platform compatible (macOS, Linux, Windows)
 * - Uses heartbeats to maintain peer list
 * - Provides automatic peer cleanup
 */
public class DiscoveryService extends Thread {

    public static final int DISCOVERY_PORT = 8888;
    private static final int DEFAULT_TTL = 3; // Time-to-live for packets
    private static final int SOCKET_TIMEOUT_MS = 3000; // Socket timeout
    private static final long CLEANUP_INTERVAL_MS = 60000; // 1 minute
    private static final long PEER_TIMEOUT_MS = 300000; // 5 minutes
    private static final long HEARTBEAT_INTERVAL_MS = 60000; // 1 minute
    
    private volatile boolean running = true;
    private DatagramSocket socket;
    private final P2PFileSharingGUI gui;
    
    // Track known peers by "ip:port" -> timestamp
    private final ConcurrentHashMap<String, Long> peerLastSeen = new ConcurrentHashMap<>();
    
    // Track message IDs to prevent loops
    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>(100));
    
    // Scheduled executor for maintenance tasks
    private ScheduledExecutorService scheduler;

    public DiscoveryService(P2PFileSharingGUI gui) {
        this.gui = gui;
        
        // Configure network for cross-platform compatibility
        NetworkUtils.configureNetworkProperties();
    }

    @Override
    public void run() {
        try {
            // Create socket with platform-specific optimizations
            socket = NetworkUtils.createOptimizedDatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            gui.logMessage("Discovery Service started on port " + DISCOVERY_PORT);
            
            // Start maintenance tasks
            startMaintenanceTasks();
            
            // Main receive loop
            while (running) {
                try {
                    // Receive packet
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    // Process in a separate thread to keep receive loop responsive
                    final DatagramPacket receivedPacket = packet;
                    new Thread(() -> {
                        try {
                            processPacket(receivedPacket);
                        } catch (Exception e) {
                            if (running) {
                                gui.logMessage("Error processing packet: " + e.getMessage());
                            }
                        }
                    }).start();
                    
                } catch (SocketTimeoutException ste) {
                    // This is normal, just continue
                } catch (IOException e) {
                    if (running) {
                        gui.logMessage("Socket error in discovery service: " + e.getMessage());
                        
                        // Try to recover
                        try {
                            if (socket != null && !socket.isClosed()) {
                                socket.close();
                            }
                            
                            // Recreate socket
                            socket = NetworkUtils.createOptimizedDatagramSocket(DISCOVERY_PORT);
                            socket.setBroadcast(true);
                            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                            
                            gui.logMessage("Successfully recovered discovery service");
                        } catch (Exception ex) {
                            gui.logMessage("Failed to recover discovery service: " + ex.getMessage());
                            break; // Exit the loop if we can't recover
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                gui.logMessage("Fatal error in discovery service: " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }
    
    /**
     * Start scheduled maintenance tasks
     */
    private void startMaintenanceTasks() {
        scheduler = Executors.newScheduledThreadPool(2);
        
        // Schedule peer cleanup
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupStaleEntries();
            } catch (Exception e) {
                gui.logMessage("Error in cleanup task: " + e.getMessage());
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        // Schedule heartbeat
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                gui.logMessage("Error in heartbeat task: " + e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Process a received packet
     */
    private void processPacket(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());
        gui.logMessage("Received packet: " + message + " from " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
        
        String[] parts = message.split(";");
        
        if (parts.length < 3) {
            gui.logMessage("Invalid packet format: " + message);
            return; // Invalid format
        }
        
        String type = parts[0];
        
        if (type.equals("DISCOVER_P2P")) {
            handleDiscoveryRequest(packet, parts);
        } else if (type.equals("P2P_FILE_SHARING")) {
            handleDiscoveryResponse(parts);
        } else if (type.equals("P2P_HEARTBEAT")) {
            handleHeartbeat(parts);
        } else {
            gui.logMessage("Unknown packet type: " + type);
        }
    }
    
    /**
     * Handle a discovery request from another peer
     */
    private void handleDiscoveryRequest(DatagramPacket packet, String[] parts) {
        try {
            if (parts.length < 5) {
                gui.logMessage("Invalid discovery request format");
                return; // Not enough data
            }
            
            String messageId = parts[1];
            int ttl = Integer.parseInt(parts[2]);
            String senderIP = parts[3];
            int senderPort = Integer.parseInt(parts[4]);
            
            gui.logMessage("Processing discovery request from " + senderIP + ":" + senderPort + " (TTL=" + ttl + ")");
            
            // Check if we've already processed this message
            if (processedMessageIds.contains(messageId)) {
                gui.logMessage("Already processed this message, skipping");
                return; // Prevent loops
            }
            
            // Add to processed IDs
            processedMessageIds.add(messageId);
            
            // Trim processed IDs list if it gets too large
            synchronized (processedMessageIds) {
                if (processedMessageIds.size() > 100) {
                    Iterator<String> it = processedMessageIds.iterator();
                    it.next();
                    it.remove();
                }
            }
            
            // Send our information back to this peer
            sendDirectResponse(senderIP, senderPort, messageId);
            
            // Forward with decremented TTL if TTL > 1
            if (ttl > 1) {
                gui.logMessage("Forwarding discovery request with reduced TTL: " + (ttl-1));
                forwardDiscoveryRequest(messageId, ttl - 1, senderIP, senderPort);
            }
            
        } catch (Exception e) {
            gui.logMessage("Error handling discovery request: " + e.getMessage());
        }
    }
    
    /**
     * Handle a response to our discovery request
     */
    private void handleDiscoveryResponse(String[] parts) {
        try {
            if (parts.length < 5) {
                gui.logMessage("Invalid discovery response format");
                return; // Not enough data
            }
            
            String peerIP = parts[3];
            int peerPort = Integer.parseInt(parts[4]);
            
            gui.logMessage("Processing discovery response from " + peerIP + ":" + peerPort);
            
            // Add to our peer list (or update timestamp)
            updatePeer(peerIP, peerPort);
            
        } catch (Exception e) {
            gui.logMessage("Error handling discovery response: " + e.getMessage());
        }
    }
    
    /**
     * Handle a heartbeat from another peer
     */
    private void handleHeartbeat(String[] parts) {
        try {
            if (parts.length < 3) {
                gui.logMessage("Invalid heartbeat format");
                return; // Not enough data
            }
            
            String peerIP = parts[1];
            int peerPort = Integer.parseInt(parts[2]);
            
            gui.logMessage("Received heartbeat from " + peerIP + ":" + peerPort);
            
            // Update peer's last seen timestamp
            updatePeer(peerIP, peerPort);
            
        } catch (Exception e) {
            gui.logMessage("Error handling heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Send a direct response to a peer
     */
    private void sendDirectResponse(String targetIP, int targetPort, String messageId) {
        try {
            InetAddress localAddr = NetworkUtils.getBestLocalAddress();
            if (localAddr == null) {
                localAddr = InetAddress.getLocalHost();
            }
            
            String localIP = localAddr.getHostAddress();
            int myServerPort = gui.getServerPort();
            
            gui.logMessage("Sending direct response to " + targetIP + ":" + targetPort + 
                          " with my address: " + localIP + ":" + myServerPort);
            
            String response = "P2P_FILE_SHARING;" + messageId + ";" + DEFAULT_TTL + ";" + 
                              localIP + ";" + myServerPort;
                              
            byte[] sendData = response.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, 
                    sendData.length, 
                    InetAddress.getByName(targetIP), 
                    targetPort);
                    
            socket.send(sendPacket);
            
            gui.logMessage("Sent direct response to " + targetIP + ":" + targetPort);
            
        } catch (Exception e) {
            gui.logMessage("Error sending direct response: " + e.getMessage());
        }
    }
    
    /**
     * Forward a discovery request with decremented TTL
     */
    private void forwardDiscoveryRequest(String messageId, int ttl, String originalIP, int originalPort) {
        try {
            InetAddress localAddr = NetworkUtils.getBestLocalAddress();
            if (localAddr == null) {
                localAddr = InetAddress.getLocalHost();
            }
            
            String localIP = localAddr.getHostAddress();
            int myServerPort = gui.getServerPort();
            
            String forwardMessage = "DISCOVER_P2P;" + messageId + ";" + ttl + ";" + 
                                   originalIP + ";" + originalPort;
                                   
            byte[] sendData = forwardMessage.getBytes();
            
            // Get broadcast address
            InetAddress broadcastAddr = NetworkUtils.getUsableBroadcastAddress();
            if (broadcastAddr == null) {
                broadcastAddr = InetAddress.getByName("255.255.255.255");
            }
            
            DatagramPacket forwardPacket = new DatagramPacket(
                    sendData, 
                    sendData.length, 
                    broadcastAddr, 
                    DISCOVERY_PORT);
                    
            socket.send(forwardPacket);
            gui.logMessage("Forwarded discovery request to broadcast address");
            
        } catch (Exception e) {
            gui.logMessage("Error forwarding discovery request: " + e.getMessage());
        }
    }
    
    /**
     * Update a peer's last seen timestamp
     */
    private void updatePeer(String peerIP, int peerPort) {
        try {
            // Skip our own address
            boolean isOwnIP = false;
            try {
                InetAddress localAddr = NetworkUtils.getBestLocalAddress();
                if (localAddr != null && peerIP.equals(localAddr.getHostAddress()) && 
                    peerPort == gui.getServerPort()) {
                    isOwnIP = true;
                }
            } catch (Exception e) {
                // Fall back to simple check
                String localIP = InetAddress.getLocalHost().getHostAddress();
                isOwnIP = peerIP.equals(localIP) && peerPort == gui.getServerPort();
            }
            
            if (isOwnIP) {
                gui.logMessage("Ignoring own address: " + peerIP + ":" + peerPort);
                return;
            }
            
            String peerKey = peerIP + ":" + peerPort;
            long now = System.currentTimeMillis();
            
            // Update timestamp or add new entry
            Long prevTimestamp = peerLastSeen.put(peerKey, now);
            
            // Log new peer discovery
            if (prevTimestamp == null) {
                gui.logMessage("Added new peer to known peers list: " + peerKey);
            } else {
                gui.logMessage("Updated timestamp for peer: " + peerKey);
            }
            
        } catch (Exception e) {
            gui.logMessage("Error updating peer: " + e.getMessage());
        }
    }
    
    /**
     * Send heartbeat to all known peers
     */
    private void sendHeartbeat() {
        try {
            InetAddress localAddr = NetworkUtils.getBestLocalAddress();
            if (localAddr == null) {
                localAddr = InetAddress.getLocalHost();
            }
            
            String localIP = localAddr.getHostAddress();
            int myServerPort = gui.getServerPort();
            
            String heartbeat = "P2P_HEARTBEAT;" + localIP + ";" + myServerPort;
            byte[] sendData = heartbeat.getBytes();
            
            // Send to all known peers
            for (String peerKey : peerLastSeen.keySet()) {
                try {
                    String[] parts = peerKey.split(":");
                    if (parts.length != 2) continue;
                    
                    String peerIP = parts[0];
                    int peerPort = Integer.parseInt(parts[1]);
                    
                    DatagramPacket packet = new DatagramPacket(
                            sendData, 
                            sendData.length, 
                            InetAddress.getByName(peerIP), 
                            DISCOVERY_PORT); // Send to discovery port
                            
                    socket.send(packet);
                    gui.logMessage("Sent heartbeat to " + peerKey);
                    
                } catch (Exception e) {
                    gui.logMessage("Error sending heartbeat to " + peerKey + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            gui.logMessage("Error sending heartbeats: " + e.getMessage());
        }
    }
    
    /**
     * Remove stale peers that haven't been seen recently
     */
    private void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        List<String> peersToRemove = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : peerLastSeen.entrySet()) {
            if (now - entry.getValue() > PEER_TIMEOUT_MS) {
                peersToRemove.add(entry.getKey());
            }
        }
        
        for (String peerKey : peersToRemove) {
            peerLastSeen.remove(peerKey);
            gui.logMessage("Removed inactive peer: " + peerKey);
        }
    }

    /**
     * Send a discovery request to find peers
     */
    public void sendDiscoveryRequest(int serverPort) {
        try {
            InetAddress localAddr = NetworkUtils.getBestLocalAddress();
            if (localAddr == null) {
                localAddr = InetAddress.getLocalHost();
            }
            
            String localIP = localAddr.getHostAddress();
            gui.logMessage("My local IP is: " + localIP);
            
            // Create a unique message ID
            String messageId = UUID.randomUUID().toString();
            
            String message = "DISCOVER_P2P;" + messageId + ";" + DEFAULT_TTL + ";" + 
                             localIP + ";" + serverPort;
                             
            byte[] sendData = message.getBytes();
            
            // Add to processed IDs
            processedMessageIds.add(messageId);
            
            // Get broadcast address
            InetAddress broadcastAddr = NetworkUtils.getUsableBroadcastAddress();
            if (broadcastAddr == null) {
                broadcastAddr = InetAddress.getByName("255.255.255.255");
            }
            
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData,
                    sendData.length,
                    broadcastAddr,
                    DISCOVERY_PORT);

            // Ensure socket is ready
            if (socket == null || socket.isClosed()) {
                gui.logMessage("Socket not open. Cannot send discovery request.");
                return;
            }
            
            socket.setBroadcast(true);
            socket.send(sendPacket);

            gui.logMessage("Broadcast discovery request: " + message);
            
        } catch (IOException e) {
            gui.logMessage("Failed to broadcast discovery request: " + e.getMessage());
        }
    }

    /**
     * Get all known peer addresses
     */
    public Set<String> getPeerAddresses() {
        return peerLastSeen.keySet();
    }
    
    /**
     * Clean up resources
     */
    private void cleanup() {
        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        
        // Close socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        // Clear data structures
        peerLastSeen.clear();
        processedMessageIds.clear();
        
        gui.logMessage("Discovery service cleaned up");
    }
    
    /**
     * Shutdown the discovery service
     */
    public void shutdown() {
        running = false;
        cleanup();
        gui.logMessage("Discovery Service stopped.");
    }
    
    /**
     * Manually add a peer to the known peers list
     */
    public void manuallyAddPeer(String ip, int port) {
        String peerKey = ip + ":" + port;
        peerLastSeen.put(peerKey, System.currentTimeMillis());
        gui.logMessage("Manually added peer: " + peerKey);
    }

    /**
     * Get the local IP and port
     */
    public String getLocalInfo() {
        try {
            InetAddress localAddr = NetworkUtils.getBestLocalAddress();
            if (localAddr == null) {
                localAddr = InetAddress.getLocalHost();
            }
            String localIP = localAddr.getHostAddress();
            int serverPort = gui.getServerPort();
            return localIP + ":" + serverPort;
        } catch (Exception e) {
            gui.logMessage("Error getting local info: " + e.getMessage());
            return "unknown";
        }
    }
}