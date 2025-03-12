package p2p;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * Enhanced ProgressBarRenderer with better visuals
 */
public class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
    
    private static final Color PROGRESS_COLOR = new Color(233, 30, 99); // Pink color for progress
    private static final Color COMPLETED_COLOR = new Color(76, 175, 80); // Green color for completed
    private static final Color ERROR_COLOR = new Color(244, 67, 54);    // Red color for errors
    
    public ProgressBarRenderer() {
        super(0, 100);
        setStringPainted(true);
        setBorderPainted(false);
        setOpaque(true);
        setBackground(new Color(240, 240, 240)); // Light gray background
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        if (value instanceof String) {
            String strVal = (String) value;
            
            // Reset appearance
            setForeground(PROGRESS_COLOR);
            
            if (strVal.endsWith("%")) {
                try {
                    int progress = Integer.parseInt(strVal.replace("%", ""));
                    setValue(progress);
                    setString(strVal);
                    
                    // Set appearance based on progress
                    if (progress >= 100) {
                        setForeground(COMPLETED_COLOR);
                    } else if (progress > 0) {
                        setForeground(PROGRESS_COLOR);
                    }
                } catch (NumberFormatException e) {
                    setValue(0);
                    setString("Error");
                    setForeground(ERROR_COLOR);
                }
            } else if (strVal.equals("Completed")) {
                setValue(100);
                setString("Completed");
                setForeground(COMPLETED_COLOR);
            } else if (strVal.equals("File Not Found")) {
                setValue(0);
                setString("File Not Found");
                setForeground(ERROR_COLOR);
            } else if (strVal.equals("Error")) {
                setValue(0);
                setString("Error");
                setForeground(ERROR_COLOR);
            } else if (strVal.equals("Info")) {
                setValue(0);
                setString("Info");
                // Use default appearance for info
            } else {
                setValue(0);
                setString(strVal);
            }
        } else {
            setValue(0);
            setString("");
        }
        
        return this;
    }
}