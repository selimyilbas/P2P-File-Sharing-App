package p2p;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;

/**
 * FileClient
 * - Connects to a peer's IP/port, requests a specific file with "REQUEST_FILE <filename>",
 *   and downloads it in 256KB chunks.
 */
public class FileClient extends Thread {
    private static final int CHUNK_SIZE = 256_000;
    private final String peerIP;
    private final int peerPort;
    private final String fileName;
    private final File destinationFolder;
    private final P2PFileSharingGUI gui;

    public FileClient(String peerIP, int peerPort, String fileName,
                      File destinationFolder, P2PFileSharingGUI gui) {
        this.peerIP = peerIP;
        this.peerPort = peerPort;
        this.fileName = fileName;
        this.destinationFolder = destinationFolder;
        this.gui = gui;
    }

    @Override
    public void run() {
        gui.logMessage("Starting chunked download of '" + fileName
                       + "' from " + peerIP + ":" + peerPort);
        try (Socket socket = new Socket(peerIP, peerPort);
             DataOutputStream dOS = new DataOutputStream(socket.getOutputStream());
             DataInputStream dIS = new DataInputStream(socket.getInputStream())) {

            dOS.writeUTF("REQUEST_FILE " + fileName);
            dOS.flush();

            long fileLength = dIS.readLong();
            if (fileLength == -1) {
                gui.logMessage("File not found on peer: " + fileName);
                gui.updateDownloadProgress(fileName, "File Not Found");
                return;
            }

            int chunkCount = dIS.readInt();
            gui.logMessage("Peer reports " + chunkCount
                           + " chunks for file " + fileName);

            File outputFile = new File(destinationFolder, fileName);
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }

            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                raf.setLength(fileLength);

                int receivedChunks = 0;
                while (true) {
                    int chunkID = dIS.readInt();
                    if (chunkID == -1) {
                        break;
                    }
                    int currentChunkSize = dIS.readInt();
                    byte[] chunkData = new byte[currentChunkSize];
                    dIS.readFully(chunkData);

                    gui.logMessage("Received chunk " + chunkID
                                   + " of size " + currentChunkSize);

                    long offset = (long) chunkID * CHUNK_SIZE;
                    raf.seek(offset);
                    raf.write(chunkData);

                    dOS.writeInt(chunkID);
                    dOS.flush();

                    receivedChunks++;
                    int progress = (int)((receivedChunks * 100L) / chunkCount);
                    gui.updateDownloadProgress(fileName, progress + "%");
                }
            }

            gui.logMessage("Download of '" + fileName + "' completed.");
            gui.updateDownloadProgress(fileName, "Completed");

        } catch (IOException e) {
            gui.logMessage("Error downloading '" + fileName + "': " + e.getMessage());
            gui.updateDownloadProgress(fileName, "Error");
        }
    }
}
