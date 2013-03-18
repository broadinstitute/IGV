/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.broad.igv.ui.util;

//~--- non-JDK imports --------------------------------------------------------

import org.broad.igv.ui.IGV;

import javax.swing.*;
import java.awt.*;

/**
 * @author eflakes
 */
public class UIUtilities {

    final private static StringBuffer scratchBuffer = new StringBuffer();

    /**
     * Display a dialog which can be used to select a color
     *
     * @param dialogTitle
     * @param defaultColor The currently selected color
     * @return  The color the user selected, or null if none/cancelled
     */
    public static Color showColorChooserDialog(String dialogTitle, Color defaultColor) {

        Color color = null;
        JColorChooser chooser = new JColorChooser();
        chooser.setColor(defaultColor);
        while (true) {

            int response = JOptionPane.showConfirmDialog(IGV.getMainFrame(), chooser,
                    dialogTitle, JOptionPane.OK_CANCEL_OPTION);

            if ((response == JOptionPane.CANCEL_OPTION) || (response == JOptionPane.CLOSED_OPTION)) {
                return null;
            }

            color = chooser.getColor();
            if (color == null) {
                continue;
            } else {
                break;
            }
        }
        return color;
    }

    /**
     * Method description
     *
     * @param parent
     * @param message
     * @return
     */
    public static boolean showConfirmationDialog(Component parent, String message) {

        int status = JOptionPane.showConfirmDialog(parent, message, null,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null);

        if ((status == JOptionPane.CANCEL_OPTION) || (status == JOptionPane.CLOSED_OPTION)) {
            return false;
        }
        return true;
    }

    /**
     * Method description
     *
     * @param color
     * @return
     */
    public static String getcommaSeparatedRGBString(Color color) {

        if (color != null) {

            scratchBuffer.delete(0, scratchBuffer.length());    // Clear
            int red = color.getRed();
            int green = color.getGreen();
            int blue = color.getBlue();
            scratchBuffer.append(red);
            scratchBuffer.append(",");
            scratchBuffer.append(green);
            scratchBuffer.append(",");
            scratchBuffer.append(blue);
        }
        return scratchBuffer.toString();

    }

    /**
     * Method description
     *
     * @param window
     */
    public static void centerWindow(Window window) {

        Dimension dimension = window.getSize();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - dimension.width) / 2;
        int y = (screenSize.height - dimension.height) / 2;
        window.setLocation(x, y);
        window.requestFocus();
    }

    /**
     * A wrapper around invokeOnEventThread.  If the runnable is already in the event dispatching
     * queue it is just run.  Otherwise it is placed in the queue via invokeOnEventThread.
     * <p/>
     * I'm not sure this is strictly necessary,  but is safe.
     *
     * @param runnable
     */
    public static void invokeOnEventThread(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }

    }
}
