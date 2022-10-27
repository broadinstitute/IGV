/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/*
 * GenomeSelectionDialog.java
 *
 * Created on November 8, 2007, 3:51 PM
 */

package org.broad.igv.ui;

import org.broad.igv.track.AttributeManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author eflakes
 */
public class AttributeSelectionDialog extends org.broad.igv.ui.IGVDialog  {

    private javax.swing.JButton cancelButton;
    private JComboBox comboBox;
    private javax.swing.JLabel comboBoxLabel;
    private javax.swing.JButton okButton;
    private boolean isCanceled = true;
    private String[] selArray;

    /**
     * Creates new form GenomeSelectionDialog
     */
    public AttributeSelectionDialog(java.awt.Frame parent, String action) {
        super(parent, true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        initComponents(action);
        setLocationRelativeTo(parent);
    }

    public void setSelectedItem(Object selection) {
        comboBox.setSelectedItem(selection);
    }

    public void setSelectedIndex(int index) {
        comboBox.setSelectedIndex(index);
    }

    public String getSelected() {
        int selIndex = comboBox.getSelectedIndex();
        return (isCanceled || selIndex == 0 ? null : selArray[selIndex]);
    }

    public boolean isCanceled() {
        return isCanceled;
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    private void initComponents(String action) {

        List<String> attributeKeys = AttributeManager.getInstance().getVisibleAttributes();
        ArrayList<String> selections = new ArrayList<>();
        selections.add("None");
        selections.addAll(attributeKeys);
        this.selArray = selections.toArray(new String[]{});

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(action);
        getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

        JPanel attributeSelectionPanel = new JPanel();
        getContentPane().add(attributeSelectionPanel);
        attributeSelectionPanel.setLayout(new FlowLayout());
        comboBoxLabel = new javax.swing.JLabel(action + " Tracks By:");
        attributeSelectionPanel.add(comboBoxLabel);
        comboBox = new JComboBox();
        comboBox.setModel(new javax.swing.DefaultComboBoxModel(selArray));
        comboBox.setEditable(false);
        attributeSelectionPanel.add(comboBox);

        JPanel buttonPanel = new JPanel();
        getContentPane().add(buttonPanel);
        okButton = new javax.swing.JButton();
        okButton.setText("Ok");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        buttonPanel.add(okButton);

        cancelButton = new javax.swing.JButton();
        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        buttonPanel.add(cancelButton);


        pack();
    }

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        isCanceled = true;
        setVisible(false);
        dispose();
    }

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        isCanceled = false;
        setVisible(false);
        dispose();
    }

}
