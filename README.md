# P2P File Sharing Application

[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.java.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Status](https://img.shields.io/badge/Status-Active-success.svg)](https://github.com/selimyilbas/P2P-File-Sharing-App)
[![Platform](https://img.shields.io/badge/Platform-Cross--platform-lightgrey.svg)](https://github.com/selimyilbas/P2P-File-Sharing-App)
[![University](https://img.shields.io/badge/University-Yeditepe-red.svg)](https://www.yeditepe.edu.tr/)
[![Course](https://img.shields.io/badge/Course-CSE471-red.svg)](https://www.yeditepe.edu.tr/)

A decentralized peer-to-peer file sharing application developed in Java, featuring multi-source downloading, UDP discovery, and an intuitive user interface. This application allows users to share and download files directly between computers without a central server.

<div align="center">
  <img width="1446" alt="Screenshot 2025-03-12 at 22 17 34" src="https://github.com/user-attachments/assets/7bc9c7ad-a920-477b-a7b7-b3ab81c1de3e" />

</div>

## 📋 Features

- **🔄 Decentralized Architecture**: True peer-to-peer model with no central server
- **🔍 UDP Broadcast Discovery**: Automatic peer discovery on the local network
- **🤝 Manual Peer Registration**: Connect to peers on different networks
- **⚡ Multi-source Downloads**: Download different chunks of a file from multiple peers
- **📦 Chunk-based File Transfer**: Files are split into 256KB chunks for efficient transfer
- **🔍 File Identification**: Identify identical files with different names
- **📊 Visual Progress Tracking**: Color-coded progress bars showing download status
- **🚫 Exclusion Rules**: Exclude specific folders and file masks from sharing
- **🖥️ Cross-platform Compatibility**: Works on macOS, Linux, and Windows

## 🚀 Getting Started

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- Network connectivity between peers

### Installation

1. Clone this repository:
```bash
git clone https://github.com/selimyilbas/P2P-File-Sharing-App.git
cd P2P-File-Sharing-App
```

2. Compile the Java code:
```bash
javac p2p/*.java
```

3. Run the application:
```bash
java p2p.P2PFileSharingGUI
```

### Setup Instructions

1. **Set Shared and Destination Folders**:
   - Click "Set Shared Folder" to select the folder containing files you want to share
     ![Kapture 2025-03-12 at 21 28 41](https://github.com/user-attachments/assets/4ed574c8-7d4a-4c63-a364-8c916f36d582)

   - Click "Set Destination Folder" to select where downloaded files will be saved

2. **Connect to the Network**:
   - Click "Files" -> "Connect" from the menu
   - The application will start the server on a random port and broadcast its presence
     ![Kapture 2025-03-12 at 21 33 29](https://github.com/user-attachments/assets/7b98a6c9-93d2-48ea-934e-823db69bead9)


3. **Find Peers and Files**:
   - Click "Search" to discover files shared by other peers on your network
   - Found files will appear in the "Found Files" section
     ![Kapture 2025-03-12 at 21 52 50](https://github.com/user-attachments/assets/8775416c-7846-4ed0-928b-4f358ad4a1e4)



4. **Manual Peer Connection**:
   - If automatic discovery doesn't work (e.g., peers on different subnets):
   - Click "Debug Network" to see your IP and port information
   - Click "Add Peer" and enter the remote peer's IP:port (e.g., 192.168.1.100:45678)
   - Click "Search" again to find files from the manually added peer

5. **Download Files**:
   - Select a file from the found files list
   - Click "Download Selected File"
   - The download progress will be displayed in the "Downloading Files" section
     ![Kapture 2025-03-12 at 22 14 13](https://github.com/user-attachments/assets/d3c94b79-2d79-4d91-be14-5d6c063ced45)


## 🏗️ Project Structure

| File | Description |
|------|-------------|
| **P2PFileSharingGUI.java** | Main GUI and application entry point |
| **DiscoveryService.java** | Handles peer discovery via UDP broadcast |
| **NetworkUtils.java** | Cross-platform network utilities |
| **ServerSocketThread.java** | Listens for incoming file requests |
| **Peer.java** | Handles incoming connections and serves files |
| **FileClient.java** | Downloads files from peers |
| **FileIdentifier.java** | Identifies identical files with different names |
| **FileTransferManager.java** | Manages file transfers |
| **SearchManager.java** | Handles non-blocking file searches |
| **ProgressBarRenderer.java** | Renders appealing download progress bars |

## 📝 Protocol Specification

The application uses a simple text-based protocol for communication:

### Discovery Protocol (UDP)
- `DISCOVER_P2P;<message_id>;<ttl>;<sender_ip>;<sender_port>`: Broadcast to discover peers
- `P2P_FILE_SHARING;<message_id>;<ttl>;<sender_ip>;<sender_port>`: Response to discovery request

### File Transfer Protocol (TCP)
- `REQUEST_FILE_LIST`: Request a list of available files
- `FILE_LIST;<count>;<filename1>;<filename2>...`: Response with available files
- `REQUEST_FILE_INFO <filename>`: Request file size information
- `<filesize>`: Response with file size in bytes (-1 if not found)
- `REQUEST_CHUNK <filename> <chunk_id>`: Request a specific file chunk
- `<chunk_id>;<size>;<data>`: Response with chunk data

## 🔧 Advanced Features

### Limited Scope Flooding
<img src="https://img.shields.io/badge/Network-Limited%20Scope%20Flooding-brightgreen.svg" alt="Limited Scope Flooding">

The application uses Time-To-Live (TTL) values in discovery messages to prevent network flooding:
- Each discovery request has an initial TTL (default: 3)
- When forwarding a discovery request, peers decrement the TTL
- When TTL reaches 0, messages are no longer forwarded

### Manual Peer Registration
<img src="https://img.shields.io/badge/Feature-Manual%20Peer%20Registration-blue.svg" alt="Manual Peer Registration">

For scenarios where automatic discovery doesn't work:
1. On the first machine, click "Debug Network" to see your IP:port
2. On the second machine, click "Add Peer" and enter the first machine's IP:port
3. Repeat in reverse to allow bi-directional discovery

### File Exclusions
<img src="https://img.shields.io/badge/Feature-File%20Exclusions-purple.svg" alt="File Exclusions">

Two types of exclusions are supported:
- **Folder Exclusions**: Exclude specific subfolders from sharing
- **File Mask Exclusions**: Exclude files matching patterns (e.g., *.tmp, *.log)

## 🐞 Troubleshooting

### Common Issues

**No Peers Found**
- Ensure both peers are on the same network
- Check if firewall is blocking UDP port 8888
- Try manual peer registration with the "Add Peer" button

**Files Not Transferring**
- Verify that shared folders contain files
- Ensure destination folder is writable
- Check file exclusion settings

**Application Freezes During Search**
- Use the "Debug Network" button to diagnose the issue
- Try using manual peer registration if automatic discovery isn't working

### Using Debug Network

The "Debug Network" button provides detailed information about:
- Network interfaces and IP addresses
- Known peers and their addresses
- The application's server port
- Network configuration issues

## 📜 License

This project is licensed under the MIT License

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

<div align="center">
  <b>Made for CSE471 Data Communications and Computer Networks - Yeditepe University</b><br>
  
</div>
