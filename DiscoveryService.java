package p2p;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * DiscoveryService
 * - Uses UDP multicast to announce (IP:port) presence, and to receive others' announcements.
 */
public class DiscoveryService extends Thread {
    public static final String MULTICAST_ADDRESS = "230.0.0.0";
    public static final int DISCOVERY_PORT = 8888;
    private volatile boolean running = true;
    private final int serverPort;
    private final P2PFileSharingGUI gui;
    private MulticastSocket socket;
    private InetAddress group;
    private final Set<String> peerAddresses =
        Collections.synchronizedSet(new HashSet<>());

    public DiscoveryService(int serverPort, P2PFileSharingGUI gui) {
        this.serverPort = serverPort;
        this.gui = gui;
    }

    @Override
    public void run() {
        try {
            group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket = new MulticastSocket(DISCOVERY_PORT);
            socket.joinGroup(group);
            socket.setTimeToLive(1);

            gui.logMessage("Discovery Service started on " + MULTICAST_ADDRESS
                           + ":" + DISCOVERY_PORT);

            // Thread to broadcast presence periodically
            new Thread(() -> {
                while (running) {
                    broadcastPresence();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }).start();

            // Main loop to receive announcements
            while (running) {
                try {
                    byte[] recvBuf = new byte[15000];
                    DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength()).trim();
                    if (message.startsWith("P2P_FILE_SHARING")) {
                        String[] parts = message.split(";");
                        if (parts.length == 3) {
                            String peerIP = parts[1];
                            int peerPort = Integer.parseInt(parts[2]);
                            String peerInfo = peerIP + ":" + peerPort;
                            String localIP = InetAddress.getLocalHost().getHostAddress();

                            // Avoid adding our own IP/port
                            if (!(peerIP.equals(localIP) && peerPort == this.serverPort)) {
                                if (!peerAddresses.contains(peerInfo)) {
                                    peerAddresses.add(peerInfo);
                                    gui.logMessage("Discovered peer: " + peerInfo);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        gui.logMessage("Discovery error: " + e.getMessage());
                    }
                } catch (NumberFormatException e) {
                    gui.logMessage("Invalid peer port: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            gui.logMessage("Failed to start Discovery Service: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void broadcastPresence() {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            String message = "P2P_FILE_SHARING;" + localIP + ";" + serverPort;
            byte[] sendData = message.getBytes();

            DatagramPacket sendPacket =
                new DatagramPacket(sendData, sendData.length, group, DISCOVERY_PORT);
            socket.send(sendPacket);
            gui.logMessage("Broadcasted presence to the network.");
        } catch (IOException e) {
            gui.logMessage("Failed to broadcast presence: " + e.getMessage());
        }
    }

    public Set<String> getPeerAddresses() {
        return peerAddresses;
    }

    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(group);
                socket.close();
                gui.logMessage("Discovery Service socket closed.");
            } catch (IOException e) {
                gui.logMessage("Error closing Discovery Service socket: " + e.getMessage());
            }
        }
    }
}
