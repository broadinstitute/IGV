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
 * Created by JFormDesigner on Wed Mar 13 11:24:25 EDT 2013
 */

package org.broad.igv.ui.util;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Jacob Silterra
 */
public class CancellableProgressDialog extends JDialog {
    public CancellableProgressDialog(Frame owner) {
        super(owner);
        initComponents();
    }

    public CancellableProgressDialog(Dialog owner) {
        super(owner);
        initComponents();
    }

    private void cancelButtonActionPerformed(ActionEvent e) {
        setVisible(false);
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        permText = new JLabel();
        statusText = new JLabel();
        progressBar = new JProgressBar();
        buttonBar = new JPanel();
        hSpacer1 = new JPanel(null);
        button = new JButton();
        hSpacer2 = new JPanel(null);

        //======== this ========
        setAlwaysOnTop(true);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
                contentPanel.add(permText);

                //---- statusText ----
                statusText.setText("...");
                contentPanel.add(statusText);
                contentPanel.add(progressBar);
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridLayout(1, 3));
                buttonBar.add(hSpacer1);

                //---- button ----
                button.setText("Cancel");
                button.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        cancelButtonActionPerformed(e);
                    }
                });
                buttonBar.add(button);
                buttonBar.add(hSpacer2);
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner non-commercial license
    private JPanel dialogPane;
    private JPanel contentPanel;
    private JLabel permText;
    private JLabel statusText;
    private JProgressBar progressBar;
    private JPanel buttonBar;
    private JPanel hSpacer1;
    private JButton button;
    private JPanel hSpacer2;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public void addButtonActionListener(ActionListener cancelActionListener) {
        button.addActionListener(cancelActionListener);
    }

    public void setStatus(final String status) {
        Runnable updater = new Runnable() {
            @Override
            public void run() {
                statusText.setText(status);
            }
        };
        UIUtilities.invokeOnEventThread(updater);
    }


    /**
     * Create a show a progress dialog with a single button (default text "Cancel")
     * @param dialogsParent
     * @param title
     * @param buttonActionListener The {@code ActionListener} to be called when the  button is pressed.
     * @param autoClose Whether to automatically close the dialog when it's finished
     * @param monitor Optional (may be null). Status text is updated based on monitor.updateStatus
     * @return
     */
    public static CancellableProgressDialog showCancellableProgressDialog(Frame dialogsParent, String title, final ActionListener buttonActionListener, final boolean autoClose, ProgressMonitor monitor){
        final CancellableProgressDialog progressDialog = new CancellableProgressDialog(dialogsParent);

        progressDialog.setTitle(title);
        progressDialog.addButtonActionListener(buttonActionListener);

        if(monitor != null && monitor instanceof IndefiniteProgressMonitor) progressDialog.getProgressBar().setIndeterminate(true);

        if(monitor != null){
            monitor.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(ProgressMonitor.STATUS_PROPERTY)) {
                        progressDialog.setStatus("" + evt.getNewValue());
                    } else if (evt.getPropertyName().equals(ProgressMonitor.PROGRESS_PROPERTY) && (Integer) evt.getNewValue() >= 100) {
                        progressDialog.button.setText("Done");
                        if(autoClose){
                            progressDialog.button.doClick(1);
                        }
                    }else if (evt.getPropertyName().equals(ProgressMonitor.PROGRESS_PROPERTY)) {
                        progressDialog.getProgressBar().setValue((Integer) evt.getNewValue());
                    }
                }
            });
        }

        progressDialog.setVisible(true);
        progressDialog.toFront();

        return progressDialog;
    }

}
