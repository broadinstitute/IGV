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
 * Created by JFormDesigner on Wed Feb 20 16:48:05 EST 2013
 */

package org.broad.igv.ui.panel;

import com.jidesoft.swing.CheckBoxList;
import org.broad.igv.track.Track;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Dialog used for selecting one or more tracks
 * @author Jacob Silterra
 */
public class TrackSelectionDialog<T extends Track> extends JDialog {

    private boolean isCancelled = false;
    private final SelectionMode mode;

    /**
     * Whether this dialog will be used to select
     * single or multiple tracks. If single, the UI will be altered
     * so that the user can only input one track
     */
    public enum SelectionMode{
        SINGLE,
        MULTIPLE
    }

    private void initTrackList(){
        switch(mode){
            case MULTIPLE:
                //Default
                break;
            case SINGLE:
                trackList = new RadioButtonSelectionList();
                break;
        }
    }

    /**
     *
     * @param owner
     * @param mode
     * @param tracks
     */
    public TrackSelectionDialog(Frame owner, SelectionMode mode, Collection<T> tracks){
        super(owner);
        this.mode = mode;
        initComponents();
        setModal(true);

        java.util.List<TrackWrapper> wrappers = TrackWrapper.wrapTracks(tracks);
        trackList.setListData(wrappers.toArray());
    }

    public Collection<T> getSelectedTracks() {
        if (isCancelled) return null;
        Object[] selectedObjects = trackList.getCheckBoxListSelectedValues();
        if (selectedObjects == null || selectedObjects.length == 0) return null;
        List<T> selectedTracks = new ArrayList<T>(selectedObjects.length);
        for(Object object: selectedObjects){
            selectedTracks.add(((TrackWrapper<T>) object).getTrack());
        }
        return selectedTracks;
    }

    private void okButtonActionPerformed(ActionEvent e) {
        setVisible(false);
        dispose();
    }

    private void cancelButtonActionPerformed(ActionEvent e) {
        isCancelled = true;
        setVisible(false);
        dispose();
    }

    public boolean getIsCancelled(){
        return isCancelled;
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        vSpacer1 = new JPanel(null);
        contentPanel = new JPanel();
        trackPanel = new JScrollPane();
        trackList = new CheckBoxList();
        initTrackList();
        buttonBar = new JPanel();
        okButton = new JButton();
        cancelButton = new JButton();

        //======== this ========
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setMinimumSize(new Dimension(220, 220));
            dialogPane.setLayout(new BorderLayout());

            //---- vSpacer1 ----
            vSpacer1.setPreferredSize(new Dimension(10, 30));
            dialogPane.add(vSpacer1, BorderLayout.NORTH);

            //======== contentPanel ========
            {
                contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.X_AXIS));

                //======== trackPanel ========
                {

                    //---- trackList ----
                    trackList.setClickInCheckBoxOnly(false);
                    trackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                    trackPanel.setViewportView(trackList);
                }
                contentPanel.add(trackPanel);
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridBagLayout());
                ((GridBagLayout)buttonBar.getLayout()).columnWidths = new int[] {0, 85, 80};
                ((GridBagLayout)buttonBar.getLayout()).columnWeights = new double[] {1.0, 0.0, 0.0};

                //---- okButton ----
                okButton.setText("OK");
                okButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        okButtonActionPerformed(e);
                    }
                });
                buttonBar.add(okButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 5), 0, 0));

                //---- cancelButton ----
                cancelButton.setText("Cancel");
                cancelButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        cancelButtonActionPerformed(e);
                    }
                });
                buttonBar.add(cancelButton, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 0), 0, 0));
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner non-commercial license
    private JPanel dialogPane;
    private JPanel vSpacer1;
    private JPanel contentPanel;
    private JScrollPane trackPanel;
    private CheckBoxList trackList;
    private JPanel buttonBar;
    private JButton okButton;
    private JButton cancelButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
