package p2p;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerSocketThread extends Thread {
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private final FileTransferManager fileTransferManager;
    private final P2PFileSharingGUI gui;
    private int assignedPort;

    public ServerSocketThread(FileTransferManager fileTransferManager, P2PFileSharingGUI gui) {
        this.fileTransferManager = fileTransferManager;
        this.gui = gui;
    }

    public int getAssignedPort() {
        return assignedPort;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(0);  // random available port
            assignedPort = serverSocket.getLocalPort();
            gui.logMessage("Server listening on port " + assignedPort + ".");
            gui.logMessage("Waiting for incoming connections...");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String peerIP = clientSocket.getInetAddress().getHostAddress();
                    gui.logMessage("Accepted connection from " + peerIP
                                   + " on port " + clientSocket.getPort());

                    new Peer(clientSocket, fileTransferManager, gui).start();
                } catch (IOException e) {
                    if (running) {
                        gui.logMessage("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                gui.logMessage("Server encountered an error: " + e.getMessage());
            }
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                gui.logMessage("Server socket closed.");
            }
        } catch (IOException e) {
            gui.logMessage("Error closing server socket: " + e.getMessage());
        }
    }
}
