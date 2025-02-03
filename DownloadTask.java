package p2p;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * DownloadTask - An alternative or older approach to a file download thread
 *   (some code bases used FileClient instead).
 *
 * If you do not use this class actively, you can remove it. But here it is in case
 * you still want it in your project.
 */
public class DownloadTask extends Thread {
    private String peerIP;
    private int peerPort;
    private String fileName;
    private File destinationFolder;
    private P2PFileSharingGUI gui;
    private FileTransferManager fileTransferManager;

    public DownloadTask(String peerIP, int peerPort, String fileName, File destinationFolder,
                       P2PFileSharingGUI gui, FileTransferManager fileTransferManager) {
        this.peerIP = peerIP;
        this.peerPort = peerPort;
        this.fileName = fileName;
        this.destinationFolder = destinationFolder;
        this.gui = gui;
        this.fileTransferManager = fileTransferManager;
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(peerIP, peerPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // Possibly something like: dos.writeUTF("REQUEST_FILE " + fileName);
            // Then read the response, etc. 
            // Here is just a simple example:
            dos.writeUTF("REQUEST_FILE " + fileName);
            dos.flush();

            String response = dis.readUTF();
            if (response.startsWith("FILE_FOUND")) {
                long fileSize = Long.parseLong(response.split(" ")[1]);
                File outputFile = new File(destinationFolder, fileName);
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    long totalRead = 0;
                    int percentCompleted = 0;

                    while ((read = dis.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        totalRead += read;
                        int currentPercent = (int) ((totalRead * 100) / fileSize);
                        if (currentPercent > percentCompleted) {
                            percentCompleted = currentPercent;
                            fileTransferManager.updateDownloadProgress(fileName, percentCompleted + "%");
                        }
                    }
                }
                fileTransferManager.addReceivedFile(fileName);
            } else if (response.equals("FILE_NOT_FOUND")) {
                gui.logMessage("Peer does not have the requested file: " + fileName);
                gui.updateDownloadProgress(fileName, "File Not Found");
            } else {
                gui.logMessage("Unknown response: " + response);
                gui.updateDownloadProgress(fileName, "Error");
            }
        } catch (IOException e) {
            gui.logMessage("Failed to download file via DownloadTask: " + fileName);
            gui.updateDownloadProgress(fileName, "Error");
            e.printStackTrace();
        }
    }
}
