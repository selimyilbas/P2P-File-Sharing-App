package p2p;

import java.io.File;
import java.util.List;

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

    public void downloadFile(List<String> peersWithFile, String fileName) {
        if (peersWithFile == null || peersWithFile.isEmpty()) {
            if (gui != null) {
                gui.logMessage("No available peers for file: " + fileName);
                gui.updateDownloadProgress(fileName, "File Not Found");
            }
            return;
        }
        FileClient fc = new FileClient(peersWithFile, fileName, destinationFolder, gui);
        fc.start();
    }

    public void updateDownloadProgress(String fileName, String progress) {
        if (gui != null) {
            gui.updateDownloadProgress(fileName, progress);
        }
    }

    public void addReceivedFile(String fileName) {
        // if you need to track completed files, do so here
    }
}
