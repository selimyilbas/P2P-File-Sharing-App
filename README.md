# P2PFileSharing
Java based peer-to-peer system that allows users to share and download files directly without relying on a central server. (CSE-471 Data Communications &amp; Computer Networks)


P2P File Sharing Application
<!-- Optional: Replace with your logo or remove -->

Overview
The P2P File Sharing Application is a Java-based peer-to-peer system that allows users to share and download files directly between machines without the need for a centralized server. The application features an intuitive graphical user interface (GUI) built with Java Swing, enabling seamless file transfers and real-time download progress monitoring.

Features
Peer Discovery: Automatically detects and connects to other peers on the same local network using multicast.
Graphical User Interface: Easy-to-use interface for configuring shared and destination folders, searching for files, and monitoring downloads.
File Exclusion: Customize file sharing by excluding specific files or file patterns (e.g., system files like .DS_Store).
Progress Tracking: Real-time progress bars for active downloads.
Cross-Platform: Runs on Windows, macOS, and Linux systems.
Dynamic Port Assignment: Each peer uses a dynamically assigned port, ensuring minimal conflicts.
Prerequisites
Java Development Kit (JDK) 17 or higher
You can download it from Oracle or use OpenJDK.
Git (to clone the repository)
Installation
Clone the Repository
Open your terminal and run:

bash

git clone https://github.com/selimyilbas/P2PFileSharing.git
cd P2PFileSharing
Compile the Source Code
Assuming your project structure is as follows:

src/ — Contains your Java source files (with package p2p)
bin/ — Destination directory for compiled classes
Run the following commands:

mkdir -p bin
javac -d bin $(find src -name "*.java")
Note: You might see some deprecation warnings. These do not affect the functionality.

Run the Application
After compiling, run the application with:


java -cp bin p2p.P2PFileSharingGUI
This command launches the GUI.

Usage
Set Shared Folder:
Click the "Set Shared Folder" button and select the directory containing the files you wish to share.

Set Destination Folder:
Click the "Set Destination Folder" button and select the directory where downloaded files should be saved.

Connect to Network:
Click the "Connect" menu item to start the peer discovery process. Each instance will be assigned a unique port and broadcast its presence.

Search for Files:
Use the "Search" button to scan your shared folder and discover files from other peers on the network.

Download Files:
Select a file from the "Found Files" list and click "Download Selected File" to initiate a file transfer. The download progress is displayed in real time.

Manage Exclusions:
You can add file masks (e.g., .DS_Store) to exclude unwanted files from the shared list.

Configuration
Network Settings:
Ensure that your system (or VM) allows multicast traffic on UDP port 8888 and that any firewalls are configured to permit Java network communication.

Virtual Machines Setup:
For multi-peer demonstrations, run at least three instances on different machines or VMs. On macOS using UTM, it is recommended to use bridged networking so that each VM receives its own IP address on your local network.

Demo Setup
For your demo session, you should have:

Instance 1 (Local Machine): Running the application with its own shared and destination folders.
Instance 2 (Virtual Machine): Using, for example, shared_folder1 and P2P Downloads1.
Instance 3 (Virtual Machine): Using, for example, shared_folder2 and P2P Downloads2.
Each instance will broadcast its presence, discover peers, and enable file sharing between all machines. Ensure that multicast is properly supported and that each machine can communicate on the same network.

Troubleshooting
Compilation Warnings:
You might see warnings about deprecated API usage. These warnings do not affect core functionality. Recompile with -Xlint:deprecation for more details if needed.

Network Issues:
If peers are not discovered, verify your network configuration and firewall settings. Using bridged networking in your VM settings is recommended.

File Exclusions:
If unwanted files (e.g., .DS_Store) appear, add them to the exclusion list using the provided GUI options.
