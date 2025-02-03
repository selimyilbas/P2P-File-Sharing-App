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
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * P2PFileSharingGUI:
 * - Main graphical interface for the P2P system.
 * - Connect/Disconnect to the network, search for remote files, and download them.
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
    private JButton searchButton, downloadButton;

    // Networking references
    private DiscoveryService discoveryService;
    private ServerSocketThread serverThread;

    // Folders chosen
    private File sharedFolder;
    private File destinationFolder;

    // Manages file downloads
    private FileTransferManager fileTransferManager;

    public P2PFileSharingGUI() {
        setTitle("P2P File Sharing Application");
        setSize(900, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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

        JPanel searchDownloadPanel = new JPanel(new BorderLayout());
        searchDownloadPanel.add(searchButton, BorderLayout.NORTH);
        searchDownloadPanel.add(downloadButton, BorderLayout.SOUTH);
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

        // Searching
        searchButton.addActionListener(e -> performSearch());
        // Download
        downloadButton.addActionListener(e -> initiateDownload());

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

        fileTransferManager = new FileTransferManager(sharedFolder, destinationFolder);
        fileTransferManager.setGUI(this);

        serverThread = new ServerSocketThread(fileTransferManager, this);
        serverThread.start();
        logMessage("Server started, listening for connections...");

        new Thread(() -> {
            while (serverThread.getAssignedPort() == 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            int assignedPort = serverThread.getAssignedPort();
            if (assignedPort == 0) {
                JOptionPane.showMessageDialog(this,
                    "Failed to assign a port for the server.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            discoveryService = new DiscoveryService(assignedPort, this);
            discoveryService.start();
            logMessage("Discovery Service started on multicast address "
                       + DiscoveryService.MULTICAST_ADDRESS + ":" + DiscoveryService.DISCOVERY_PORT);

            SwingUtilities.invokeLater(() -> {
                serverPortLabel.setText("Assigned Port: " + assignedPort);
                JOptionPane.showMessageDialog(this,
                    "Connected on port " + assignedPort + "!",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }

    private void disconnectFromNetwork() {
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
        serverPortLabel.setText("Assigned Port: Not Connected");
        JOptionPane.showMessageDialog(this,
            "Disconnected from the network.",
            "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    public void logMessage(String message) {
        downloadingFilesModel.addRow(new Object[]{message, "In Progress"});
    }

    public void updateDownloadProgress(String fileName, String progress) {
        for (int i = 0; i < downloadingFilesModel.getRowCount(); i++) {
            if (downloadingFilesModel.getValueAt(i, 0).equals(fileName)) {
                downloadingFilesModel.setValueAt(progress, i, 1);
                return;
            }
        }
    }

    private void performSearch() {
        if (discoveryService == null) {
            JOptionPane.showMessageDialog(this,
                "Not connected to the network. Please Connect first.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Set<String> peers = discoveryService.getPeerAddresses();
        if (peers == null || peers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No peers discovered yet.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> remoteFiles = new ArrayList<>();
        for (String peerAddr : peers) {
            String[] parts = peerAddr.split(":");
            if (parts.length != 2) continue;
            String peerIP = parts[0];
            int peerPort;
            try {
                peerPort = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            remoteFiles.addAll(requestFileListFromPeer(peerIP, peerPort));
        }

        foundFilesList.setListData(remoteFiles.toArray(new String[0]));
    }

    private List<String> requestFileListFromPeer(String peerIP, int peerPort) {
        List<String> result = new ArrayList<>();
        try (Socket socket = new Socket(peerIP, peerPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeUTF("REQUEST_FILE_LIST");
            dos.flush();

            String marker = dis.readUTF();
            if (!"FILE_LIST".equals(marker)) {
                return result;
            }
            int fileCount = dis.readInt();
            for (int i = 0; i < fileCount; i++) {
                String fileName = dis.readUTF();
                // "fileName:peerIP:peerPort"
                result.add(fileName + ":" + peerIP + ":" + peerPort);
            }

        } catch (IOException e) {
            logMessage("Failed to fetch file list from "
                       + peerIP + ":" + peerPort + " -> " + e.getMessage());
        }
        return result;
    }

    private void initiateDownload() {
        String selectedEntry = foundFilesList.getSelectedValue();
        if (selectedEntry == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a file to download.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

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

        fileTransferManager.downloadFile(peerIP, peerPort, fileName);
        downloadingFilesModel.addRow(new Object[]{fileName, "0%"});
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            P2PFileSharingGUI gui = new P2PFileSharingGUI();
            gui.setVisible(true);
        });
    }

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
