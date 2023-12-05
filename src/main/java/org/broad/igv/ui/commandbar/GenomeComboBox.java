package org.broad.igv.ui.commandbar;

import org.broad.igv.logging.*;
import org.broad.igv.DirectoryManager;
import org.broad.igv.event.GenomeResetEvent;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.feature.genome.GenomeListItem;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.feature.genome.GenomeServerException;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.UIConstants;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.ProgressBar;
import org.broad.igv.ui.util.UIUtilities;
import org.broad.igv.util.LongRunningTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Created by jrobinso on 7/6/17.
 */
public class GenomeComboBox extends JComboBox<GenomeListItem> {

    private static Logger log = LogManager.getLogger(GenomeComboBox.class);

    public GenomeComboBox() {

        setRenderer(new ComboBoxRenderer());
        addActionListener(new GenomeBoxActionListener());
    }


    public void refreshGenomeListComboBox() {
        setModel(getModelForGenomeListComboBox());
        String curId = GenomeManager.getInstance().getGenomeId();
        Object item = GenomeListManager.getInstance().getLoadedGenomeListItemById(curId);
        if (item != null) {
            setSelectedItem(item);
        }
    }

    public boolean hasItem(Object item) {
        int c = this.getItemCount();
        for (int i = 0; i < c; i++) {
            if (item.equals(this.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build a model for the genome combo box
     *
     * @return
     */
    private DefaultComboBoxModel getModelForGenomeListComboBox() {
        Collection<GenomeListItem> genomes;
        try {
            genomes = GenomeListManager.getInstance().getGenomeItemMap().values();
        } catch (IOException e) {
            log.error("Error reading genome list ", e);
            genomes = new ArrayList<>();
            MessageUtils.showErrorMessage("Error reading genome list ", e);
        }

        Vector<GenomeListItem> vector = new Vector<>(genomes);
        vector.sort(Comparator.comparing(GenomeListItem::getDisplayableName));
        vector.add(GenomeListItem.DOWNLOAD_ITEM);
        return new DefaultComboBoxModel(vector);
    }


    class GenomeBoxActionListener implements ActionListener {

        public void actionPerformed(ActionEvent actionEvent) {
            Object selItem = getSelectedItem();
            if (!(selItem instanceof GenomeListItem)) {
                return;
            }
            GenomeListItem genomeListItem = (GenomeListItem) selItem;
            loadGenomeListItem(genomeListItem);
        }

        private void loadGenomeListItem(final GenomeListItem genomeListItem) {

            // If we haven't changed genomes do nothing
            if (genomeListItem.getId().equalsIgnoreCase(GenomeManager.getInstance().getGenomeId())) {
                return;
            }

            final Runnable runnable = new Runnable() {

                public void run() {

                    if (genomeListItem != null && genomeListItem.getPath() != null) {

                        //log.warn("Loading " + genomeListItem.getId());

                        //User selected "more", pull up dialog and revert combo box
                        if (genomeListItem == GenomeListItem.DOWNLOAD_ITEM) {
                            loadGenomeFromServer();
                            return;
                        }

                        try {
                            GenomeManager.getInstance().loadGenomeById(genomeListItem.getId());
                        } catch (GenomeServerException e) {
                            log.error("Error loading genome: " + genomeListItem.getId() + "  " + genomeListItem.getPath(), e);
                            JOptionPane.showMessageDialog(
                                    IGV.getInstance().getMainFrame(),
                                    "Error loading genome: " + genomeListItem.getDisplayableName());
                        } catch (Exception e) {
                            log.error(e);
                            int choice = JOptionPane.showConfirmDialog(
                                    IGV.getInstance().getMainFrame(), "The genome [" + genomeListItem.getId() +
                                            "] could not be read. Would you like to remove the selected entry?",
                                    "", JOptionPane.OK_CANCEL_OPTION);

                            if (choice == JOptionPane.OK_OPTION) {
                                GenomeListManager.getInstance().removeGenomeListItem(genomeListItem);
                                refreshGenomeListComboBox();
                                log.error("Error initializing genome", e);
                            }
                        } finally {

                        }

                    }
                }
            };

            // If we're on the dispatch thread spawn a worker, otherwise just execute.
            if (SwingUtilities.isEventDispatchThread()) {
                LongRunningTask.submit(runnable);
            } else {
                runnable.run();
            }
        }
    }


    static class ComboBoxRenderer implements ListCellRenderer {

        JSeparator separator;

        /**
         * Constructs ...
         */
        public ComboBoxRenderer() {
            separator = new JSeparator(JSeparator.HORIZONTAL);
        }

        /**
         * Method description
         *
         * @param list
         * @param value
         * @param index
         * @param isSelected
         * @param cellHasFocus
         * @return
         */
        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            String text = (value == null) ? "" : value.toString();

            Component renderer = null;

            if (UIConstants.GENOME_LIST_SEPARATOR.equals(text)) {
                return separator;
            }

            if (text.equals(UIConstants.REMOVE_GENOME_LIST_MENU_ITEM)) {
                JLabel label = new JLabel(text);

                label.setOpaque(true);
                label.setBorder(new EmptyBorder(1, 1, 1, 1));
                renderer = label;
            } else {

                JLabel label = new JLabel(text);

                label.setOpaque(true);
                label.setBorder(new EmptyBorder(1, 1, 1, 1));
                label.setSize(label.getWidth() + 10, label.getHeight());
                renderer = label;
            }

            //We call with a null list when setting width
            if (list != null) {
                if (isSelected) {
                    renderer.setBackground(list.getSelectionBackground());
                    renderer.setForeground(list.getSelectionForeground());
                } else {
                    renderer.setBackground(list.getBackground());
                    renderer.setForeground(list.getForeground());
                }
                renderer.setFont(list.getFont());
            }


            return renderer;
        }
    }


    /**
     * Open a selection list to load a genome from the server.   This method is static because its used by multiple
     * UI elements  (menu bar and genome selection pulldown).
     */
    public static void loadGenomeFromServer() {

        Runnable showDialog = () -> {

            Collection<GenomeListItem> inputListItems = GenomeListManager.getInstance().getServerGenomeList();
            if (inputListItems == null) {
                //Could not reach genome server.  Not necessary to display a message, getServerGenomeArchiveList does it already
                return;
            }
            GenomeSelectionDialog dialog = new GenomeSelectionDialog(IGV.getInstance().getMainFrame(), inputListItems);
            UIUtilities.invokeAndWaitOnEventThread(() -> dialog.setVisible(true));

            if (dialog.isCanceled()) {
                // Clear the "More..."  selection in pulldown
                IGVEventBus.getInstance().post(new GenomeResetEvent());
            } else {
                List<GenomeListItem> selectedValueList = dialog.getSelectedValues();
                GenomeListItem firstItem = null;
                for (GenomeListItem selectedValue : selectedValueList) {
                    if (selectedValue != null) {
                        boolean success = GenomeManager.getInstance().downloadGenome(selectedValue, dialog.downloadSequence());
                        if (success) {
                            GenomeListManager.getInstance().addServerGenomeItem(selectedValue);
                            firstItem = selectedValue;
                        }
                    }
                }
                if (firstItem != null) {
                    try {

                        GenomeManager.getInstance().loadGenome(firstItem.getPath());
                        // If the user has previously defined this genome, remove it.
                        GenomeListManager.getInstance().removeUserDefinedGenome(firstItem.getId());

                        // If this is a .json genome, attempt to remove existing .genome files
                        if(firstItem.getPath().endsWith(".json")) {
                            removeDotGenomeFile(firstItem.getId());
                        }


                    } catch (IOException e) {
                        GenomeListManager.getInstance().removeGenomeListItem(firstItem);
                        MessageUtils.showErrorMessage("Error loading genome " + firstItem.getDisplayableName(), e);
                        log.error("Error loading genome " + firstItem.getDisplayableName(), e);
                    }
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            LongRunningTask.submit(showDialog);
        } else {
            showDialog.run();
        }
    }

    public static void removeDotGenomeFile(String id) {
        try {
            File dotGenomeFile = new File(DirectoryManager.getGenomeCacheDirectory(), id + ".genome");
            if(dotGenomeFile.exists()) {
                dotGenomeFile.delete();
            }
        } catch (Exception e) {
            // If anything goes wrong, just log it, this cleanup is not essential
            log.error("Error deleting .genome file", e);
        }
    }


}
