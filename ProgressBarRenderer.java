package p2p;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * ProgressBarRenderer
 * - A JProgressBar that serves as a custom TableCellRenderer
 *   to display a percent-based progress string like "45%", "Completed", etc.
 */
public class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
    public ProgressBarRenderer() {
        super(0, 100);
        setStringPainted(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        if (value instanceof String) {
            String progressStr = (String) value;
            if (progressStr.endsWith("%")) {
                try {
                    int progress = Integer.parseInt(progressStr.replace("%", ""));
                    setValue(progress);
                    setString(progressStr);
                } catch (NumberFormatException e) {
                    setValue(0);
                    setString("Error");
                }
            } else if (progressStr.equals("Completed")) {
                setValue(100);
                setString("Completed");
            } else if (progressStr.equals("File Not Found") || progressStr.equals("Error")) {
                setValue(0);
                setString(progressStr);
            } else {
                // fallback
                setValue(0);
                setString(progressStr);
            }
        }
        return this;
    }
}
