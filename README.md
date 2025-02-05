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


Network Issues:
If peers are not discovered, verify your network configuration and firewall settings. Using bridged networking in your VM settings is recommended.

File Exclusions:
If unwanted files (e.g., .DS_Store) appear, add them to the exclusion list using the provided GUI options.
