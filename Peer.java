package p2p;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;

/**
 * Peer
 * - When a client connects, this thread handles:
 *   1) REQUEST_FILE_LIST        --> returns list of files in the shared folder
 *   2) REQUEST_FILE_INFO <f>    --> returns length of file 'f', or -1 if not found
 *   3) REQUEST_CHUNK <f> <id>   --> returns chunk #<id> of file 'f'
 *
 * This allows a multi-source chunk-by-chunk approach from the client side.
 * Now with improved handling of small files.
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
            }
            else if (command.startsWith("REQUEST_FILE_INFO ")) {
                String requestedFile = command.substring("REQUEST_FILE_INFO ".length()).trim();
                handleRequestFileInfo(requestedFile, dOS);
            }
            else if (command.startsWith("REQUEST_CHUNK ")) {
                // Format: "REQUEST_CHUNK <filename> <chunkID>"
                String remainder = command.substring("REQUEST_CHUNK ".length()).trim();
                String[] parts = remainder.split(" ");
                if (parts.length != 2) {
                    dOS.writeInt(-1); // signal error
                    return;
                }
                String requestedFile = parts[0];
                int chunkID = Integer.parseInt(parts[1]);
                handleRequestChunk(requestedFile, chunkID, dIS, dOS);
            }
            else {
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
                // skip hidden and system files
                if (!f.isHidden() && !f.getName().startsWith(".")) {
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

    private void handleRequestFileInfo(String requestedFile, DataOutputStream dOS) throws IOException {
        File file = new File(fileTransferManager.getSharedFolder(), requestedFile);
        if (!file.exists() || !file.isFile()) {
            // send -1 if not found
            dOS.writeLong(-1);
            gui.logMessage("File not found (INFO): " + requestedFile);
        } else {
            long fileLength = file.length();
            dOS.writeLong(fileLength); // send back file length
            gui.logMessage("Sending FILE INFO for '" + requestedFile + "': length=" + fileLength);
        }
    }

    private void handleRequestChunk(String requestedFile, int chunkID,
                                    DataInputStream dIS, DataOutputStream dOS) throws IOException {

        File file = new File(fileTransferManager.getSharedFolder(), requestedFile);
        if (!file.exists() || !file.isFile()) {
            // signals that chunk can't be fetched
            dOS.writeInt(-1);
            gui.logMessage("File not found for chunk request: " + requestedFile);
            return;
        }

        long fileLength = file.length();
        int chunkCount = (int) Math.ceil((double) fileLength / CHUNK_SIZE);
        
        // Handle case of zero chunks (empty file)
        if (chunkCount == 0 && fileLength > 0) {
            chunkCount = 1;
        }
        
        if (chunkID < 0 || chunkID >= chunkCount) {
            // invalid chunk ID
            dOS.writeInt(-1);
            gui.logMessage("Invalid chunk ID requested: " + chunkID);
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long offset = (long) chunkID * CHUNK_SIZE;
            
            // Ensure we don't seek beyond the file
            if (offset > fileLength) {
                offset = 0;
            }
            
            raf.seek(offset);

            // Calculate remaining bytes that can be read
            int maxBytesToRead = (int) Math.min(CHUNK_SIZE, fileLength - offset);
            if (maxBytesToRead <= 0) {
                // No data to read (should never happen with valid chunkID)
                dOS.writeInt(-1);
                gui.logMessage("No data available for chunk " + chunkID);
                return;
            }
            
            byte[] buffer = new byte[maxBytesToRead];
            int bytesRead = raf.read(buffer);
            
            if (bytesRead == -1) {
                // no data
                dOS.writeInt(-1);
                gui.logMessage("No data read for chunk " + chunkID);
                return;
            }

            dOS.writeInt(chunkID);      // re-send the chunkID
            dOS.writeInt(bytesRead);    // how many bytes in this chunk
            dOS.write(buffer, 0, bytesRead);
            dOS.flush();
            gui.logMessage("Sent chunk #" + chunkID + " (" + bytesRead + " bytes) of file " + requestedFile);

            // Optionally read an ACK from client:
            int ack = dIS.readInt();
            if (ack != chunkID) {
                gui.logMessage("ACK mismatch! Expected " + chunkID + ", got " + ack);
            }
        }
    }
}