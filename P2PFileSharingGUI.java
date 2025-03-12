package p2p;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * P2PFileSharingGUI:
 * - Main graphical interface for the P2P system
 * - Fixed to work across Mac, Linux, and Windows
 * - Improved search functionality to prevent UI freezing
 */
public class P2PFileSharingGUI extends JFrame {
    // Menus
    private JMenuBar menuBar;
    private JMenu filesMenu, helpMenu;
    private JMenuItem connectItem, disconnectItem, exitItem, aboutItem;

    // Folder pickers
    private JButton setSharedFolderButton, setDestinationFolderButton;
    private JLabel serverPortLabel;

    // Optional exclusions
    private JCheckBox checkNewFilesCheckbox;
    private DefaultListModel<String> folderExclusionListModel, fileMaskListModel;
    private JList<String> folderExclusionList, fileMaskList;
    private JButton addFolderExclusionButton, deleteFolderExclusionButton;
    private JButton addFileMaskButton, deleteFileMaskButton;

    // Table: (File, Progress)
    private JTable downloadingFilesTable;
    private DefaultTableModel downloadingFilesModel;

    // Found files list + actions
    private JList<String> foundFilesList;
    private JButton searchButton, downloadButton, debugButton;

    // Networking references
    private DiscoveryService discoveryService;
    private ServerSocketThread serverThread;

    // Folders chosen
    private File sharedFolder;
    private File destinationFolder;

    // Manages file downloads
    private FileTransferManager fileTransferManager;
    
    // Search manager for non-blocking search
    private SearchManager searchManager;

    public P2PFileSharingGUI() {
        setTitle("P2P File Sharing Application");
        setSize(900, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Initialize search manager
        searchManager = new SearchManager(this);

        initializeComponents();
        layoutComponents();
        addEventHandlers();
    }

    private void initializeComponents() {
        // Menu setup
        menuBar = new JMenuBar();
        filesMenu = new JMenu("Files");
        helpMenu = new JMenu("Help");
        connectItem = new JMenuItem("Connect");
        disconnectItem = new JMenuItem("Disconnect");
        exitItem = new JMenuItem("Exit");
        aboutItem = new JMenuItem("About");

        // Folder selection
        setSharedFolderButton = new JButton("Set Shared Folder");
        setDestinationFolderButton = new JButton("Set Destination Folder");
        serverPortLabel = new JLabel("Assigned Port: Not Connected");

        // Exclusions
        checkNewFilesCheckbox = new JCheckBox("Check new files only in the root", true);
        folderExclusionListModel = new DefaultListModel<>();
        folderExclusionList = new JList<>(folderExclusionListModel);
        addFolderExclusionButton = new JButton("Add");
        deleteFolderExclusionButton = new JButton("Del");
        fileMaskListModel = new DefaultListModel<>();
        fileMaskList = new JList<>(fileMaskListModel);
        addFileMaskButton = new JButton("Add");
        deleteFileMaskButton = new JButton("Del");

        // (File, Progress) table
        downloadingFilesModel = new DefaultTableModel(new Object[]{"File", "Progress"}, 0);
        downloadingFilesTable = new JTable(downloadingFilesModel);
        downloadingFilesTable.setFillsViewportHeight(true);
        downloadingFilesTable.getColumnModel().getColumn(1).setCellRenderer(new ProgressBarRenderer());

        // Found files
        foundFilesList = new JList<>(new DefaultListModel<>());
        searchButton = new JButton("Search");
        downloadButton = new JButton("Download Selected File");
        debugButton = new JButton("Debug Network");
    }

    private void layoutComponents() {
        // Menubar
        setJMenuBar(menuBar);
        filesMenu.add(connectItem);
        filesMenu.add(disconnectItem);
        filesMenu.addSeparator();
        filesMenu.add(exitItem);
        helpMenu.add(aboutItem);
        menuBar.add(filesMenu);
        menuBar.add(helpMenu);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10,10,10,10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Row 1: Shared folder
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Shared Folder:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        mainPanel.add(setSharedFolderButton, gbc);

        // Row 2: Destination folder
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Destination Folder:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        mainPanel.add(setDestinationFolderButton, gbc);

        // Row 3: Server port
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Server Port:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        mainPanel.add(serverPortLabel, gbc);

        // Row 4: Exclusions panel
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel exclusionsPanel = new JPanel(new GridBagLayout());
        exclusionsPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
        GridBagConstraints exGbc = new GridBagConstraints();
        exGbc.insets = new Insets(5,5,5,5);
        exGbc.fill = GridBagConstraints.BOTH;
        exGbc.weightx = 1;
        exGbc.weighty = 0;

        exGbc.gridx = 0;
        exGbc.gridy = 0;
        exGbc.gridwidth = 3;
        exclusionsPanel.add(checkNewFilesCheckbox, exGbc);

        exGbc.gridy++;
        exGbc.gridwidth = 3;
        exclusionsPanel.add(new JLabel("Exclude folders:"), exGbc);
        exGbc.gridy++;
        exGbc.weighty = 1;
        exclusionsPanel.add(new JScrollPane(folderExclusionList), exGbc);
        exGbc.gridx = 3;
        exGbc.weighty = 0;
        exGbc.fill = GridBagConstraints.NONE;
        exclusionsPanel.add(addFolderExclusionButton, exGbc);
        exGbc.gridx = 4;
        exclusionsPanel.add(deleteFolderExclusionButton, exGbc);

        exGbc.gridx = 0;
        exGbc.gridy++;
        exGbc.gridwidth = 3;
        exGbc.weighty = 0;
        exGbc.fill = GridBagConstraints.BOTH;
        exclusionsPanel.add(new JLabel("Exclude files matching masks:"), exGbc);
        exGbc.gridy++;
        exGbc.weighty = 1;
        exclusionsPanel.add(new JScrollPane(fileMaskList), exGbc);
        exGbc.gridx = 3;
        exGbc.weighty = 0;
        exGbc.fill = GridBagConstraints.NONE;
        exclusionsPanel.add(addFileMaskButton, exGbc);
        exGbc.gridx = 4;
        exclusionsPanel.add(deleteFileMaskButton, exGbc);

        mainPanel.add(exclusionsPanel, gbc);

        // Row 5: Downloading files table
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel downloadingPanel = new JPanel(new BorderLayout());
        downloadingPanel.setBorder(BorderFactory.createTitledBorder("Downloading Files"));
        downloadingPanel.add(new JScrollPane(downloadingFilesTable), BorderLayout.CENTER);
        mainPanel.add(downloadingPanel, gbc);

        // Row 6: Found Files
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.weighty = 0.5;
        JPanel foundPanel = new JPanel(new BorderLayout());
        foundPanel.setBorder(BorderFactory.createTitledBorder("Found Files"));
        foundPanel.add(new JScrollPane(foundFilesList), BorderLayout.CENTER);

        JPanel searchDownloadPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        searchDownloadPanel.add(searchButton);
        searchDownloadPanel.add(downloadButton);
        searchDownloadPanel.add(debugButton);
        
        // Add the manual peer button
        JButton addPeerButton = new JButton("Add Peer");
        addPeerButton.addActionListener(e -> addManualPeer());
        searchDownloadPanel.add(addPeerButton);
        
        foundPanel.add(searchDownloadPanel, BorderLayout.EAST);

        mainPanel.add(foundPanel, gbc);

        add(mainPanel);
    }

    private void addEventHandlers() {
        // Menu actions
        connectItem.addActionListener(e -> connectToNetwork());
        disconnectItem.addActionListener(e -> disconnectFromNetwork());
        exitItem.addActionListener(e -> System.exit(0));
        aboutItem.addActionListener(e ->
            JOptionPane.showMessageDialog(this, "P2P File Sharing Application\nDeveloper: Your Name"));

        // Folder pickers
        setSharedFolderButton.addActionListener(e -> chooseFolder("Select Shared Folder", true));
        setDestinationFolderButton.addActionListener(e -> chooseFolder("Select Destination Folder", false));

        // Searching for remote files
        searchButton.addActionListener(e -> {
            if (searchManager.isSearchInProgress()) {
                searchManager.cancelSearch();
                searchButton.setText("Search");
            } else {
                searchButton.setText("Cancel Search");
                searchManager.performSearch();
            }
        });
        
        // Download
        downloadButton.addActionListener(e -> initiateDownload());
        
        // Debug button
        debugButton.addActionListener(e -> debugPeers());

        // Exclusions
        addFolderExclusionButton.addActionListener(e -> addFolderExclusion());
        deleteFolderExclusionButton.addActionListener(e -> deleteFolderExclusion());
        addFileMaskButton.addActionListener(e -> addFileMask());
        deleteFileMaskButton.addActionListener(e -> deleteFileMask());

        // Found files double-click
        foundFilesList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    initiateDownload();
                }
            }
        });

        // Window close
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                disconnectFromNetwork();
            }
        });
    }

    private void chooseFolder(String dialogTitle, boolean isShared) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            if (isShared) {
                sharedFolder = selectedFolder;
                JOptionPane.showMessageDialog(this,
                    "Shared Folder selected: " + sharedFolder.getAbsolutePath(),
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            } else {
                destinationFolder = selectedFolder;
                JOptionPane.showMessageDialog(this,
                    "Destination Folder selected: " + destinationFolder.getAbsolutePath(),
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void addFolderExclusion() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Folder to Exclude");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            String folderPath = selectedFolder.getAbsolutePath();
            if (!folderExclusionListModel.contains(folderPath)) {
                folderExclusionListModel.addElement(folderPath);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Folder already in exclusion list.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void deleteFolderExclusion() {
        int selectedIndex = folderExclusionList.getSelectedIndex();
        if (selectedIndex != -1) {
            folderExclusionListModel.remove(selectedIndex);
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a folder to delete.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void addFileMask() {
        String fileMask = JOptionPane.showInputDialog(this, "Enter file mask (e.g. *.txt):");
        if (fileMask != null) {
            fileMask = fileMask.trim();
            if (!fileMask.isEmpty() && !fileMaskListModel.contains(fileMask)) {
                fileMaskListModel.addElement(fileMask);
            } else if (fileMaskListModel.contains(fileMask)) {
                JOptionPane.showMessageDialog(this,
                    "File mask already in exclusion list.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                    "File mask cannot be empty.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteFileMask() {
        int selectedIndex = fileMaskList.getSelectedIndex();
        if (selectedIndex != -1) {
            fileMaskListModel.remove(selectedIndex);
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a file mask to delete.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Connect to the P2P network with improved cross-platform support
     */
    private void connectToNetwork() {
        if (sharedFolder == null || destinationFolder == null) {
            JOptionPane.showMessageDialog(this,
                "Please set both Shared Folder and Destination Folder before connecting.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!sharedFolder.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                "Invalid Shared Folder.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!destinationFolder.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                "Invalid Destination Folder.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Configure network properties for cross-platform support
        NetworkUtils.configureNetworkProperties();

        // Initialize file manager
        fileTransferManager = new FileTransferManager(sharedFolder, destinationFolder);
        fileTransferManager.setGUI(this);

        // Start server
        serverThread = new ServerSocketThread(fileTransferManager, this);
        serverThread.start();
        logMessage("Server started, listening for connections...");

        // Wait asynchronously for the port to be assigned
        new Thread(() -> {
            try {
                // Wait for server to start, with timeout
                int waitCount = 0;
                while (serverThread.getAssignedPort() == 0 && waitCount < 50) {
                    try {
                        Thread.sleep(100);
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                final int assignedPort = serverThread.getAssignedPort();
                if (assignedPort == 0) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "Failed to assign a port for the server.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }

                // Now create and start the DiscoveryService
                discoveryService = new DiscoveryService(this);
                discoveryService.start();

                // Send discovery request after a short delay to ensure the service is running
                try {
                    Thread.sleep(1000);  // 1 second delay
                    discoveryService.sendDiscoveryRequest(assignedPort);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                SwingUtilities.invokeLater(() -> {
                    serverPortLabel.setText("Assigned Port: " + assignedPort);
                    connectItem.setEnabled(false);
                    disconnectItem.setEnabled(true);
                    JOptionPane.showMessageDialog(this,
                        "Connected on port " + assignedPort + "!\nDiscovery Request sent.",
                        "Info", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    logMessage("Error during connection: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                        "Error connecting to network: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    /**
     * Disconnect from the P2P network
     */
    public void disconnectFromNetwork() {
        if (searchManager.isSearchInProgress()) {
            searchManager.cancelSearch();
            searchButton.setText("Search");
        }

        if (discoveryService != null) {
            discoveryService.shutdown();
            discoveryService = null;
            logMessage("Discovery Service stopped.");
        }
        if (serverThread != null) {
            serverThread.shutdown();
            serverThread = null;
            logMessage("Server stopped.");
        }
        
        connectItem.setEnabled(true);
        disconnectItem.setEnabled(false);
        serverPortLabel.setText("Assigned Port: Not Connected");
        
        JOptionPane.showMessageDialog(this,
            "Disconnected from the network.",
            "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Debug peer connections and network settings
     */
    private void debugPeers() {
        JTextArea textArea = new JTextArea(20, 50);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        
        // Gather debug information
        StringBuilder debug = new StringBuilder();
        debug.append("=== P2P Network Debug Info ===\n\n");
        
        // System info
        debug.append("OS: ").append(System.getProperty("os.name"))
             .append(" ").append(System.getProperty("os.version")).append("\n");
        debug.append("Java: ").append(System.getProperty("java.version")).append("\n\n");
        
        // Network interfaces
        debug.append("=== Network Interfaces ===\n");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                debug.append("Interface: ").append(iface.getDisplayName()).append("\n");
                debug.append("  Up: ").append(iface.isUp())
                     .append(", Loopback: ").append(iface.isLoopback())
                     .append(", Virtual: ").append(iface.isVirtual()).append("\n");
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    debug.append("  Address: ").append(addr.getHostAddress()).append("\n");
                }
                debug.append("\n");
            }
        } catch (Exception e) {
            debug.append("Error getting network interfaces: ").append(e.getMessage()).append("\n");
        }
        
        // Best local address
        debug.append("Best local address: ");
        try {
            InetAddress bestAddr = NetworkUtils.getBestLocalAddress();
            debug.append(bestAddr != null ? bestAddr.getHostAddress() : "none found").append("\n\n");
        } catch (Exception e) {
            debug.append("Error: ").append(e.getMessage()).append("\n\n");
        }
        
        // Server info
        debug.append("Server port: ").append(getServerPort()).append("\n\n");
        
        // Discovery service peers
        debug.append("=== Known Peers ===\n");
        if (discoveryService != null) {
            Set<String> peers = discoveryService.getPeerAddresses();
            if (peers != null && !peers.isEmpty()) {
                for (String peer : peers) {
                    debug.append(peer).append("\n");
                }
            } else {
                debug.append("No peers discovered\n");
            }
        } else {
            debug.append("Discovery service not running\n");
        }
        
        // Display in dialog
        textArea.setText(debug.toString());
        JOptionPane.showMessageDialog(this, scrollPane, "P2P Network Debug", JOptionPane.INFORMATION_MESSAGE);
        
        // Also log to main window
        logMessage("--- Debug Info ---");
        logMessage("Server port: " + getServerPort());
        logMessage("Best local address: " + 
            (NetworkUtils.getBestLocalAddress() != null ? 
             NetworkUtils.getBestLocalAddress().getHostAddress() : "none found"));
        
        if (discoveryService != null) {
            Set<String> peers = discoveryService.getPeerAddresses();
            logMessage("Known peers: " + (peers != null ? peers.size() : 0));
            if (peers != null) {
                for (String peer : peers) {
                    logMessage("Peer: " + peer);
                }
            }
            
            // Force a new discovery request
            logMessage("Sending new discovery request...");
            discoveryService.sendDiscoveryRequest(getServerPort());
        }
    }

    /**
     * Add a peer manually
     */
    private void addManualPeer() {
        String peerAddress = JOptionPane.showInputDialog(this, 
            "Enter peer IP:port\nYour address is: " + 
            (discoveryService != null ? discoveryService.getLocalInfo() : "not connected"));
        
        if (peerAddress != null && !peerAddress.trim().isEmpty()) {
            if (discoveryService != null) {
                // Manually register this peer
                String[] parts = peerAddress.trim().split(":");
                if (parts.length == 2) {
                    try {
                        String ip = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        discoveryService.manuallyAddPeer(ip, port);
                        JOptionPane.showMessageDialog(this,
                            "Peer added successfully.\nClick Search to find files.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(this, 
                            "Invalid port number", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Invalid format. Use IP:Port", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Not connected. Connect first.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Log a message in the downloads table
     */
    public void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            downloadingFilesModel.addRow(new Object[]{message, "Info"});
            // Auto-scroll to bottom
            downloadingFilesTable.scrollRectToVisible(
                downloadingFilesTable.getCellRect(
                    downloadingFilesModel.getRowCount() - 1, 0, true));
        });
    }

    /**
     * Update download progress in the table
     */
    public void updateDownloadProgress(String fileName, String progress) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < downloadingFilesModel.getRowCount(); i++) {
                if (downloadingFilesModel.getValueAt(i, 0).equals(fileName)) {
                    downloadingFilesModel.setValueAt(progress, i, 1);
                    return;
                }
            }
            
            // If not found, add a new row
            downloadingFilesModel.addRow(new Object[]{fileName, progress});
        });
    }
    
    /**
     * Update the search button text based on search state
     */
    public void updateSearchButtonState(boolean isSearching) {
        SwingUtilities.invokeLater(() -> {
            searchButton.setText(isSearching ? "Cancel Search" : "Search");
        });
    }
    
    /**
     * Get the DiscoveryService instance
     */
    public DiscoveryService getDiscoveryService() {
        return discoveryService;
    }
    
    /**
     * Clear the found files list
     */
    public void clearFoundFilesList() {
        DefaultListModel<String> model = (DefaultListModel<String>) foundFilesList.getModel();
        model.clear();
    }
    
    /**
     * Update the found files list with the given files
     */
    public void updateFoundFilesList(List<String> files) {
        DefaultListModel<String> model = (DefaultListModel<String>) foundFilesList.getModel();
        model.clear();
        for (String file : files) {
            model.addElement(file);
        }
    }

    /**
     * Download the selected file from the peer(s).
     * If multiple peers have the same file (the same "fileName"), we do multi-source.
     */
    private void initiateDownload() {
        String selectedEntry = foundFilesList.getSelectedValue();
        if (selectedEntry == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a file to download.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Example: "fileX:192.168.1.10:5050"
        int lastColon = selectedEntry.lastIndexOf(':');
        if (lastColon == -1) {
            JOptionPane.showMessageDialog(this,
                "Invalid format in Found Files: " + selectedEntry,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String fileNameAndIP = selectedEntry.substring(0, lastColon);
        String portStr = selectedEntry.substring(lastColon + 1);

        int secondColon = fileNameAndIP.lastIndexOf(':');
        if (secondColon == -1) {
            JOptionPane.showMessageDialog(this,
                "Invalid IP format in Found Files: " + fileNameAndIP,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String fileName = fileNameAndIP.substring(0, secondColon);
        String peerIP = fileNameAndIP.substring(secondColon + 1);

        int peerPort;
        try {
            peerPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                "Invalid peer port: " + portStr,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Collect all peers that have the same fileName
        ListModel<String> model = foundFilesList.getModel();
        List<String> peersWithFile = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            String entry = model.getElementAt(i);
            int lc = entry.lastIndexOf(':');
            if (lc == -1) continue;
            String fNameAndIP = entry.substring(0, lc);
            String pStr = entry.substring(lc + 1);

            int sc = fNameAndIP.lastIndexOf(':');
            if (sc == -1) continue;
            String fName = fNameAndIP.substring(0, sc);
            String ip = fNameAndIP.substring(sc + 1);

            if (fName.equals(fileName)) {
                peersWithFile.add(ip + ":" + pStr);
            }
        }

        // Add a new row in the table for progress
        downloadingFilesModel.addRow(new Object[]{fileName, "0%"});

        // Multi-source chunk download
        fileTransferManager.downloadFile(peersWithFile, fileName);
    }

    /**
     * Get the server port from ServerSocketThread
     */
    public int getServerPort() {
        if (serverThread != null) {
            return serverThread.getAssignedPort();
        }
        return 0;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Set look and feel to match the OS
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            P2PFileSharingGUI gui = new P2PFileSharingGUI();
            gui.setVisible(true);
        });
    }

    /**
     * Custom table cell renderer for the "Progress" column
     */
    class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressBarRenderer() {
            super(0, 100);
            setStringPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            if (value instanceof String) {
               
                        String strVal = (String) value;
                        if (strVal.endsWith("%")) {
                            try {
                                int progress = Integer.parseInt(strVal.replace("%", ""));
                                setValue(progress);
                                setString(strVal);
                            } catch (NumberFormatException e) {
                                setValue(0);
                                setString("Error");
                            }
                        } else if (strVal.equals("Completed")) {
                            setValue(100);
                            setString("Completed");
                        } else if (strVal.equals("File Not Found")) {
                            setValue(0);
                            setString("File Not Found");
                        } else if (strVal.equals("Error")) {
                            setValue(0);
                            setString("Error");
                        } else {
                            setValue(0);
                            setString(strVal);
                        }
                    }
                    return this;
                }
            }
        }