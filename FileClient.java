package p2p;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Improved FileClient with:
 * - Parallel downloading of chunks
 * - Robust error handling and retry logic
 * - Better progress reporting
 * - Peer blacklisting for failed downloads
 * - Fixed handling of small files (less than one chunk)
 */
public class FileClient extends Thread {
    private static final int CHUNK_SIZE = 256_000; // 256KB chunks as per project requirements
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int SOCKET_TIMEOUT_MS = 10000; // 10 seconds
    private static final int MAX_THREADS = 4; // Max parallel downloads
    
    private final List<String> peers; // List of peers in format "ip:port"
    private final String fileName;
    private final File destinationFolder;
    private final P2PFileSharingGUI gui;
    
    // Track which chunks are downloaded
    private final ConcurrentHashMap<Integer, Boolean> downloadedChunks = new ConcurrentHashMap<>();
    
    // Track failed peers
    private final ConcurrentHashMap<String, Integer> failedPeers = new ConcurrentHashMap<>();
    
    // Progress tracking
    private final AtomicInteger completedChunks = new AtomicInteger(0);
    private int totalChunks = 0;
    
    // Executor for parallel downloads
    private ExecutorService executor;

    public FileClient(List<String> peers, String fileName, File destinationFolder, P2PFileSharingGUI gui) {
        this.peers = new ArrayList<>(peers); // Make a copy to avoid external modification
        this.fileName = fileName;
        this.destinationFolder = destinationFolder;
        this.gui = gui;
    }

    @Override
    public void run() {
        gui.logMessage("Starting download of: " + fileName);
        gui.updateDownloadProgress(fileName, "0%");
        
        try {
            // Create thread pool
            executor = Executors.newFixedThreadPool(MAX_THREADS);
            
            // Step 1: Find file length from peers
            long fileLength = getFileLengthFromAnyPeer();
            if (fileLength == -1) {
                gui.logMessage("File not found on any peer: " + fileName);
                gui.updateDownloadProgress(fileName, "File Not Found");
                return;
            }
            
            // Step 2: Prepare local file
            File outputFile = new File(destinationFolder, fileName);
            try {
                if (!outputFile.exists()) {
                    outputFile.createNewFile();
                }
            } catch (IOException e) {
                gui.logMessage("Error creating local file: " + e.getMessage());
                gui.updateDownloadProgress(fileName, "Error");
                return;
            }
            
            // Step 3: Calculate chunk count
            totalChunks = (int) Math.ceil((double) fileLength / CHUNK_SIZE);
            if (totalChunks == 0 && fileLength > 0) {
                // Handle the case where file is smaller than one chunk
                totalChunks = 1;
            }
            gui.logMessage("File '" + fileName + "' has " + totalChunks + " chunks, total size: " + fileLength + " bytes");
            
            // Step 4: Find valid peers that have the file
            List<String> validPeers = getValidPeers(fileLength);
            if (validPeers.isEmpty()) {
                gui.logMessage("No peers have the file: " + fileName);
                gui.updateDownloadProgress(fileName, "File Not Found");
                return;
            }
            gui.logMessage("Found " + validPeers.size() + " peers with the file.");
            
            // Step 5: Download chunks in parallel
            downloadChunksInParallel(outputFile, fileLength, validPeers);
            
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }
    
    /**
     * Find valid peers that have the file with the correct length
     */
    private List<String> getValidPeers(long expectedLength) {
        List<String> validPeers = new ArrayList<>();
        
        for (String peerAddr : peers) {
            long length = getFileLengthFromPeer(peerAddr);
            if (length == expectedLength) {
                validPeers.add(peerAddr);
            }
        }
        
        return validPeers;
    }
    
    /**
     * Query any peer for the file length
     */
    private long getFileLengthFromAnyPeer() {
        for (String peerAddr : peers) {
            long length = getFileLengthFromPeer(peerAddr);
            if (length > 0) {
                return length;
            }
        }
        return -1;
    }

    /**
     * Get file length from a specific peer
     */
    private long getFileLengthFromPeer(String peerAddr) {
        if (failedPeers.getOrDefault(peerAddr, 0) >= MAX_RETRY_ATTEMPTS) {
            return -1; // Skip blacklisted peers
        }
        
        String[] parts = peerAddr.split(":");
        if (parts.length != 2) return -1;

        String ip = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }

        try (Socket socket = new Socket(ip, port)) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());

            dos.writeUTF("REQUEST_FILE_INFO " + fileName);
            dos.flush();

            long length = dis.readLong(); // -1 if not found
            
            // Reset failure count on success
            failedPeers.remove(peerAddr);
            
            return length;
        } catch (IOException e) {
            gui.logMessage("Error getting file length from " + peerAddr + ": " + e.getMessage());
            
            // Increment failure count
            failedPeers.put(peerAddr, failedPeers.getOrDefault(peerAddr, 0) + 1);
            
            return -1;
        }
    }
    
    /**
     * Download chunks in parallel from multiple peers
     */
    private void downloadChunksInParallel(File outputFile, long fileLength, List<String> validPeers) {
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            // Set the file length upfront
            raf.setLength(fileLength);
            
            // Initialize progress
            completedChunks.set(0);
            
            // Special handling for very small files (less than one chunk)
            if (totalChunks == 1 && fileLength < CHUNK_SIZE) {
                gui.logMessage("Small file detected, downloading in a single operation");
                boolean success = downloadSmallFile(raf, fileLength, validPeers);
                if (success) {
                    gui.logMessage("Download completed: " + fileName);
                    gui.updateDownloadProgress(fileName, "Completed");
                } else {
                    gui.logMessage("Failed to download small file: " + fileName);
                    gui.updateDownloadProgress(fileName, "Error");
                }
                return;
            }
            
            // Prepare list of chunks to download
            List<Integer> chunkIds = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                chunkIds.add(i);
            }
            
            // Randomize to avoid multiple clients hitting the same chunks first
            Collections.shuffle(chunkIds);
            
            // Submit all chunk downloads to the executor
            List<Future<?>> futures = new ArrayList<>();
            
            for (int chunkId : chunkIds) {
                futures.add(executor.submit(() -> downloadChunkWithRetry(raf, chunkId, validPeers)));
            }
            
            // Wait for all downloads to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    gui.logMessage("Error during download: " + e.getMessage());
                }
            }
            
            // Verify all chunks were downloaded
            boolean allChunksDownloaded = downloadedChunks.size() == totalChunks;
            
            if (allChunksDownloaded) {
                gui.logMessage("Download completed: " + fileName);
                gui.updateDownloadProgress(fileName, "Completed");
            } else {
                gui.logMessage("Download incomplete. Missing " + (totalChunks - downloadedChunks.size()) + " chunks.");
                gui.updateDownloadProgress(fileName, "Incomplete");
            }
            
        } catch (IOException e) {
            gui.logMessage("File write error: " + e.getMessage());
            gui.updateDownloadProgress(fileName, "Error");
        }
    }
    
    /**
     * Download a small file (less than one chunk) directly
     */
    private boolean downloadSmallFile(RandomAccessFile raf, long fileLength, List<String> validPeers) {
        for (String peerAddr : validPeers) {
            // Skip blacklisted peers
            if (failedPeers.getOrDefault(peerAddr, 0) >= MAX_RETRY_ATTEMPTS) {
                continue;
            }
            
            String[] parts = peerAddr.split(":");
            if (parts.length != 2) continue;

            String ip = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            
            try (Socket socket = new Socket(ip, port)) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                
                DataOutputStream dOS = new DataOutputStream(socket.getOutputStream());
                DataInputStream dIS = new DataInputStream(socket.getInputStream());

                // Request first chunk (which contains all data for small files)
                dOS.writeUTF("REQUEST_CHUNK " + fileName + " 0");
                dOS.flush();

                // Read chunk ID from response
                int returnedChunkId = dIS.readInt();
                if (returnedChunkId == -1) {
                    continue; // try next peer
                }

                // Read chunk size
                int currentChunkSize = dIS.readInt();
                if (currentChunkSize < 0 || currentChunkSize < fileLength) {
                    continue; // invalid size, try next peer
                }

                // Read chunk data
                byte[] chunkData = new byte[currentChunkSize];
                dIS.readFully(chunkData);

                // Write chunk data at beginning of file
                synchronized (raf) {
                    raf.seek(0);
                    raf.write(chunkData, 0, (int)fileLength);
                }

                // Send ACK
                dOS.writeInt(returnedChunkId);
                dOS.flush();

                // Update progress in steps to show animation
                for (int i = 0; i <= 100; i += 5) {
                    gui.updateDownloadProgress(fileName, i + "%");
                    try {
                        Thread.sleep(50); // Short delay for animation
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                return true;
            } catch (IOException e) {
                gui.logMessage("Error downloading small file from " + peerAddr + ": " + e.getMessage());
                failedPeers.put(peerAddr, failedPeers.getOrDefault(peerAddr, 0) + 1);
            }
        }
        
        return false; // All peers failed
    }
    
    /**
     * Download a single chunk with retry logic
     */
    private void downloadChunkWithRetry(RandomAccessFile raf, int chunkId, List<String> validPeers) {
        int attempts = 0;
        boolean success = false;
        
        // Shuffle the peers list to distribute load
        List<String> shuffledPeers = new ArrayList<>(validPeers);
        Collections.shuffle(shuffledPeers);
        
        while (!success && attempts < MAX_RETRY_ATTEMPTS && !Thread.currentThread().isInterrupted()) {
            // Select a peer based on attempt number
            String peerAddr = shuffledPeers.get(attempts % shuffledPeers.size());
            
            // Skip blacklisted peers
            if (failedPeers.getOrDefault(peerAddr, 0) >= MAX_RETRY_ATTEMPTS) {
                attempts++;
                continue;
            }
            
            try {
                success = downloadChunkFromPeer(peerAddr, chunkId, raf);
                
                if (success) {
                    // Mark chunk as downloaded
                    downloadedChunks.put(chunkId, true);
                    
                    // Update progress
                    int completed = completedChunks.incrementAndGet();
                    int progressPercent = (int) (((long) completed * 100) / totalChunks);
                    gui.updateDownloadProgress(fileName, progressPercent + "%");
                }
            } catch (Exception e) {
                gui.logMessage("Error downloading chunk " + chunkId + " from " + peerAddr + ": " + e.getMessage());
                
                // Increment failure count for this peer
                failedPeers.put(peerAddr, failedPeers.getOrDefault(peerAddr, 0) + 1);
            }
            
            attempts++;
        }
        
        if (!success) {
            gui.logMessage("Failed to download chunk " + chunkId + " after " + attempts + " attempts");
        }
    }

    /**
     * Download a specific chunk from a peer
     */
    private boolean downloadChunkFromPeer(String peerAddr, int chunkId, RandomAccessFile raf) throws IOException {
        String[] parts = peerAddr.split(":");
        if (parts.length != 2) return false;

        String ip = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        try (Socket socket = new Socket(ip, port)) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            
            DataOutputStream dOS = new DataOutputStream(socket.getOutputStream());
            DataInputStream dIS = new DataInputStream(socket.getInputStream());

            // Request a specific chunk
            dOS.writeUTF("REQUEST_CHUNK " + fileName + " " + chunkId);
            dOS.flush();

            // Read chunk ID from response
            int returnedChunkId = dIS.readInt();
            if (returnedChunkId == -1) {
                return false; // peer didn't have chunk
            }

            // Read chunk size
            int currentChunkSize = dIS.readInt();
            if (currentChunkSize < 0) {
                return false;
            }

            // Read chunk data
            byte[] chunkData = new byte[currentChunkSize];
            dIS.readFully(chunkData);

            // Write chunk data at correct offset
            long offset = (long) chunkId * CHUNK_SIZE;
            synchronized (raf) {
                raf.seek(offset);
                raf.write(chunkData);
            }

            // Send ACK
            dOS.writeInt(returnedChunkId);
            dOS.flush();

            return true;
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out: " + e.getMessage());
        } catch (IOException e) {
            throw new IOException("Error downloading chunk: " + e.getMessage());
        }
    }
}