package p2p;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * FileDump
 *  Creates fileToSend.txt with 10 million random digits (~21MB).
 */
public class FileDump {
    public static void main(String[] args) {
        File file = new File("fileToSend.txt");
        try (BufferedWriter myFile = new BufferedWriter(new FileWriter(file))) {
            Random r = new Random();
            int length = 10_000_000;
            System.out.println("Generating file with 10 million random digits...");

            StringBuilder sb = new StringBuilder();
            int batchSize = 100_000;
            for (int i = 0; i < length; i++) {
                sb.append(r.nextInt(10)); 
                if ((i + 1) % batchSize == 0) {
                    myFile.write(sb.toString());
                    sb.setLength(0);
                    System.out.println((i + 1) + " digits written...");
                }
            }
            if (sb.length() > 0) {
                myFile.write(sb.toString());
                System.out.println("Final batch of digits written.");
            }
            System.out.println("fileToSend.txt created with 10 million digits.");
        } catch (IOException e) {
            System.err.println("Error generating file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
