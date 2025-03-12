package p2p;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * NetworkUtils
 * - Provides utilities for cross-platform network operations
 * - Handles OS-specific networking differences (macOS, Linux, Windows)
 * - Helps find optimal network interfaces for P2P communication
 */
public class NetworkUtils {

    /**
     * Get the local IP address that is most likely to communicate with other peers.
     * Prefers non-loopback IPv4 addresses.
     * 
     * @return The most suitable local IP address, or null if none found
     */
    public static InetAddress getBestLocalAddress() {
        try {
            // Try to get the primary network interface
            List<InetAddress> candidates = new ArrayList<>();
            
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                // Skip loopback, virtual, and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp() || 
                    networkInterface.isVirtual() || networkInterface.isPointToPoint()) {
                    continue;
                }
                
                // Get addresses for this interface
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // Prefer IPv4 addresses
                    if (address instanceof Inet4Address) {
                        candidates.add(address);
                    }
                }
            }
            
            // Prioritize addresses: non-loopback IPv4 > IPv4 > any
            for (InetAddress addr : candidates) {
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr;
                }
            }
            
            // Fall back to any IPv4 address
            for (InetAddress addr : candidates) {
                if (addr instanceof Inet4Address) {
                    return addr;
                }
            }
            
            // Last resort: any address in our candidates
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
            
            // If all else fails, get the local host address
            return InetAddress.getLocalHost();
            
        } catch (Exception e) {
            System.err.println("Error finding best local address: " + e.getMessage());
            
            // Fall back to localhost
            try {
                return InetAddress.getLocalHost();
            } catch (UnknownHostException ex) {
                return null;
            }
        }
    }
    
    /**
     * Configure optimal system networking properties based on the current OS
     */
    public static void configureNetworkProperties() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        // macOS-specific settings
        if (osName.contains("mac")) {
            // These settings are important for multicast/broadcast to work properly on macOS
            System.setProperty("java.net.preferIPv4Stack", "true");
        }
        
        // Windows-specific settings
        else if (osName.contains("windows")) {
            // No special settings needed currently
        }
        
        // Linux-specific settings
        else if (osName.contains("linux")) {
            // No special settings needed currently
        }
    }
    
    /**
     * Create a DatagramSocket optimized for the current platform
     * 
     * @param port The port to bind to
     * @return A configured DatagramSocket
     * @throws SocketException If there was an error creating the socket
     */
    public static DatagramSocket createOptimizedDatagramSocket(int port) throws SocketException {
        DatagramSocket socket = new DatagramSocket(null); // Create unbound
        
        // Set socket options before binding
        socket.setReuseAddress(true);
        
        // Bind to all interfaces
        socket.bind(new InetSocketAddress(port));
        
        return socket;
    }
    
    /**
     * Check if the current network supports UDP broadcast/multicast
     * 
     * @return true if UDP broadcast is supported, false otherwise
     */
    public static boolean isBroadcastSupported() {
        try {
            // Create a test socket and try to set broadcast
            try (DatagramSocket testSocket = new DatagramSocket()) {
                testSocket.setBroadcast(true);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Broadcast not supported: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Find a usable broadcast address for the current network
     * 
     * @return The broadcast address to use, or null if none found
     */
    public static InetAddress getUsableBroadcastAddress() {
        try {
            // Try standard broadcast address first
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            
            // If on macOS, we might need a more specific address
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("mac")) {
                // Try to find a more specific broadcast address for the network interface
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                        List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                        for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                            InetAddress broadcast = interfaceAddress.getBroadcast();
                            if (broadcast != null) {
                                return broadcast;
                            }
                        }
                    }
                }
            }
            
            return broadcastAddr;
        } catch (Exception e) {
            System.err.println("Error finding broadcast address: " + e.getMessage());
            return null;
        }
    }
}