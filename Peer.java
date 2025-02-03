package p2p;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;

/**
 * Peer
 * - When a client connects, this thread handles either file list requests
 *   or chunk-based file downloads.
 */
public class Peer extends Thread {
    private static final int CHUNK_SIZE = 256_000;
    private final Socket socket;
    private final FileTransferManager fileTransferManager;
    private final P2PFileSharingGUI gui;

    public Peer(Socket socket, FileTransferManager fileTransferManager, P2PFileSharingGUI gui) {
        this.socket = socket;
        this.fileTransferManager = fileTransferManager;
        this.gui = gui;
    }

    @Override
    public void run() {
        try (DataInputStream dIS = new DataInputStream(socket.getInputStream());
             DataOutputStream dOS = new DataOutputStream(socket.getOutputStream())) {

            String command = dIS.readUTF().trim();
            if (command.equals("REQUEST_FILE_LIST")) {
                handleRequestFileList(dOS);
            } else if (command.startsWith("REQUEST_FILE ")) {
                String requestedFile = command.substring("REQUEST_FILE ".length()).trim();
                handleFileDownload(requestedFile, dIS, dOS);
            } else {
                gui.logMessage("Unknown command: " + command);
                dOS.writeUTF("ERROR: Unknown command");
            }

        } catch (IOException e) {
            gui.logMessage("Error in Peer communication: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                gui.logMessage("Error closing socket: " + e.getMessage());
            }
        }
    }

    private void handleRequestFileList(DataOutputStream dOS) throws IOException {
        gui.logMessage("Peer " + socket.getInetAddress().getHostAddress() + " requested file list.");

        File sharedFolder = fileTransferManager.getSharedFolder();
        if (sharedFolder == null || !sharedFolder.isDirectory()) {
            dOS.writeUTF("FILE_LIST");
            dOS.writeInt(0);
            return;
        }
        File[] files = sharedFolder.listFiles();
        if (files == null) {
            dOS.writeUTF("FILE_LIST");
            dOS.writeInt(0);
            return;
        }

        java.util.List<String> fileList = new java.util.ArrayList<>();
        for (File f : files) {
            if (f.isFile()) {
                if (!f.getName().equals(".DS_Store")) {
                    fileList.add(f.getName());
                }
            }
        }

        dOS.writeUTF("FILE_LIST");
        dOS.writeInt(fileList.size());
        for (String filename : fileList) {
            dOS.writeUTF(filename);
        }
        dOS.flush();
    }

    private void handleFileDownload(String requestedFile,
                                    DataInputStream dIS,
                                    DataOutputStream dOS) throws IOException {
        File file = new File(fileTransferManager.getSharedFolder(), requestedFile);
        if (!file.exists() || !file.isFile()) {
            dOS.writeLong(-1);
            gui.logMessage("File not found: " + requestedFile);
            return;
        }

        long fileLength = file.length();
        dOS.writeLong(fileLength);

        int chunkCount = (int) Math.ceil((double) fileLength / CHUNK_SIZE);
        dOS.writeInt(chunkCount);

        gui.logMessage("Sending file '" + requestedFile
                       + "' in " + chunkCount + " chunks of 256KB.");

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            for (int chunkID = 0; chunkID < chunkCount; chunkID++) {
                raf.seek((long) chunkID * CHUNK_SIZE);

                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead = raf.read(buffer);
                if (bytesRead == -1) {
                    break;
                }

                gui.logMessage("Sending chunk " + chunkID + " of size " + bytesRead);

                dOS.writeInt(chunkID);
                dOS.writeInt(bytesRead);
                dOS.write(buffer, 0, bytesRead);
                dOS.flush();

                int ack = dIS.readInt();
                if (ack != chunkID) {
                    gui.logMessage("ACK mismatch! Expected " + chunkID + ", got " + ack);
                    break;
                }
            }
        }

        dOS.writeInt(-1);
        dOS.flush();

        gui.logMessage("Finished sending file '" + requestedFile + "'.");
    }
}
