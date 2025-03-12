package p2p;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * SearchManager
 * - Handles searching for files in the P2P network
 * - Uses background threads to avoid freezing the UI
 * - Provides proper timeout handling for network operations
 * - Allows cancellation of ongoing searches
 */
public class SearchManager {
    // Constants for network timeouts
    private static final int CONNECT_TIMEOUT_MS = 1500;  // 1.5 second connection timeout
    private static final int READ_TIMEOUT_MS = 3000;     // 3 second read timeout
    
    // Reference to the main GUI
    private final P2PFileSharingGUI gui;
    
    // Thread pool for parallel searches
    private ExecutorService searchExecutor;
    
    // Progress tracking
    private volatile boolean searchInProgress = false;
    private final AtomicInteger completedPeerQueries = new AtomicInteger(0);
    private int totalPeersToQuery = 0;

    public SearchManager(P2PFileSharingGUI gui) {
        this.gui = gui;
    }
    
    /**
     * Perform the search operation in a background thread
     */
    public void performSearch() {
        // Prevent multiple concurrent searches
        if (searchInProgress) {
            gui.logMessage("Search already in progress, please wait...");
            return;
        }
        
        // Get the discovery service
        final DiscoveryService discoveryService = gui.getDiscoveryService();
        if (discoveryService == null) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(gui,
                    "Not connected to the network. Please Connect first.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            });
            return;
        }
        
        // Get peers from discovery service
        final Set<String> peers = discoveryService.getPeerAddresses();
        
        // Log the discovered peers for debugging
        gui.logMessage("Found " + (peers != null ? peers.size() : 0) + " peers in discovery service");
        if (peers != null && !peers.isEmpty()) {
            for (String peer : peers) {
                gui.logMessage("Known peer: " + peer);
            }
        }
        
        if (peers == null || peers.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(gui,
                    "No peers discovered yet. Try clicking 'Connect' again to send discovery requests.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            });
            
            // Force a new discovery attempt
            discoveryService.sendDiscoveryRequest(gui.getServerPort());
            gui.logMessage("Sent new discovery request. Try searching again in a few seconds.");
            return;
        }
        
        // Clear previous search results (in the EDT)
        SwingUtilities.invokeLater(() -> {
            gui.clearFoundFilesList();
            gui.logMessage("Searching for files from " + peers.size() + " peers...");
        });
        
        // Initialize search state
        searchInProgress = true;
        completedPeerQueries.set(0);
        totalPeersToQuery = peers.size();
        
        // Initialize thread pool
        searchExecutor = Executors.newFixedThreadPool(Math.min(peers.size(), 5));
        
        // Launch a background thread to manage the search
        new Thread(() -> {
            try {
                final List<String> allRemoteFiles = new ArrayList<>();
                
                // Submit a task for each peer
                for (String peerAddr : peers) {
                    searchExecutor.submit(() -> {
                        List<String> peerFiles = requestFileListFromPeerWithTimeout(peerAddr);
                        
                        // Add files to our result list in the EDT
                        if (!peerFiles.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                allRemoteFiles.addAll(peerFiles);
                                gui.updateFoundFilesList(allRemoteFiles);
                            });
                        }
                        
                        // Update progress
                        int completed = completedPeerQueries.incrementAndGet();
                        final int progressPercent = (completed * 100) / totalPeersToQuery;
                        
                        SwingUtilities.invokeLater(() -> {
                            gui.logMessage("Search progress: " + progressPercent + "% (" + completed + "/" + totalPeersToQuery + " peers)");
                        });
                    });
                }
                
                // Shutdown the executor and wait for completion
                searchExecutor.shutdown();
                boolean completed = searchExecutor.awaitTermination(30, TimeUnit.SECONDS);
                
                // Final update in the EDT
                SwingUtilities.invokeLater(() -> {
                    if (completed) {
                        gui.logMessage("Search completed. Found " + allRemoteFiles.size() + " files from peers.");
                    } else {
                        gui.logMessage("Search timed out. Found " + allRemoteFiles.size() + " files from responding peers.");
                    }
                    
                    // Handle empty results
                    if (allRemoteFiles.isEmpty()) {
                        gui.logMessage("No files found on any peer.");
                    }
                    
                    // Reset search button
                    gui.updateSearchButtonState(false);
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    gui.logMessage("Error during search: " + e.getMessage());
                    gui.updateSearchButtonState(false);
                });
            } finally {
                // Always clean up, even on error
                if (searchExecutor != null && !searchExecutor.isTerminated()) {
                    searchExecutor.shutdownNow();
                }
                searchInProgress = false;
            }
        }).start();
    }
    
    /**
     * Request file list from a peer with timeout handling
     */
    private List<String> requestFileListFromPeerWithTimeout(String peerAddr) {
        List<String> result = new ArrayList<>();
        Socket socket = null;
        
        try {
            String[] parts = peerAddr.split(":");
            if (parts.length != 2) return result;
            
            String peerIP = parts[0];
            int peerPort;
            try {
                peerPort = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return result;
            }
            
            gui.logMessage("Connecting to peer: " + peerIP + ":" + peerPort);
            
            // Create socket with connection timeout
            socket = new Socket();
            socket.connect(new InetSocketAddress(peerIP, peerPort), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            
            try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {
                
                // Request the file list
                dos.writeUTF("REQUEST_FILE_LIST");
                dos.flush();
                
                // Read the response
                gui.logMessage("Waiting for file list from: " + peerIP + ":" + peerPort);
                String marker = dis.readUTF();
                if (!"FILE_LIST".equals(marker)) {
                    gui.logMessage("Invalid marker received: " + marker);
                    return result;
                }
                
                int fileCount = dis.readInt();
                gui.logMessage("Peer " + peerIP + ":" + peerPort + " has " + fileCount + " files");
                
                for (int i = 0; i < fileCount; i++) {
                    String fileName = dis.readUTF();
                    // Store as "fileName:peerIP:peerPort"
                    result.add(fileName + ":" + peerIP + ":" + peerPort);
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                gui.logMessage("Failed to fetch file list from " + peerAddr + " -> " + e.getMessage());
            });
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        return result;
    }
    
    /**
     * Cancel any ongoing search
     */
    public void cancelSearch() {
        if (searchInProgress && searchExecutor != null) {
            searchExecutor.shutdownNow();
            searchInProgress = false;
            gui.logMessage("Search canceled.");
        }
    }
    
    /**
     * Check if a search is currently in progress
     */
    public boolean isSearchInProgress() {
        return searchInProgress;
    }
}