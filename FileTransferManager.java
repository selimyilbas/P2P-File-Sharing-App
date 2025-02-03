package p2p;

import java.io.File;

public class FileTransferManager {
    private final File sharedFolder;
    private final File destinationFolder;
    private P2PFileSharingGUI gui;

    public FileTransferManager(File sharedFolder, File destinationFolder) {
        this.sharedFolder = sharedFolder;
        this.destinationFolder = destinationFolder;
    }

    public void setGUI(P2PFileSharingGUI gui) {
        this.gui = gui;
    }

    public File getSharedFolder() {
        return sharedFolder;
    }

    public void downloadFile(String peerIP, int peerPort, String fileName) {
        new FileClient(peerIP, peerPort, fileName, destinationFolder, gui).start();
    }

    public void updateDownloadProgress(String fileName, String progress) {
        if (gui != null) {
            gui.updateDownloadProgress(fileName, progress);
        }
    }

    public void addReceivedFile(String fileName) {
        // If you need to track completed files, you can do it here
    }
}
