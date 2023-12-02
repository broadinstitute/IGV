package org.broad.igv.feature.genome.load;

import org.broad.igv.feature.genome.Genome;
import org.broad.igv.ucsc.Hub;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.TrackSelectionDialog;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class HubGenomeLoader extends GenomeLoader {

    String hubURL;

    public HubGenomeLoader(String hubURL) {
        this.hubURL = hubURL;
    }

    public static boolean isHubURL(String obj) {
        return obj.endsWith("/hub.txt");
    }

    public static String convertToHubURL(String accension) {
        //https://hgdownload.soe.ucsc.edu/hubs/GCF/016/808/095/GCF_016808095.1/
        //https://hgdownload.soe.ucsc.edu/hubs/GCA/028/534/965/GCA_028534965.1/
       if(accension.startsWith("GCF") || accension.startsWith("GCA") && accension.length() >= 13) {
           String prefix = accension.substring(0, 3);
           String n1 = accension.substring(4, 7);
           String n2 = accension.substring(7, 10);
           String n3 = accension.substring(10, 13);
           return "https://hgdownload.soe.ucsc.edu/hubs/" + prefix + "/" + n1 + "/" + n2 + "/" + n3 + "/" + accension + "/hub.txt";
       } else {
           return null;
       }
    }

    @Override
    public Genome loadGenome() throws IOException {

        Hub hub = Hub.loadHub(this.hubURL);

        GenomeConfig config = hub.getGenomeConfig(null);

        // Select tracks
        if(IGV.hasInstance()) {
            TrackSelectionDialog dlg = new TrackSelectionDialog(hub.getGroupedTrackConfigurations(), IGV.getInstance().getMainFrame());
            dlg.setVisible(true);
            List<TrackConfig> selectedTracks = dlg.getSelectedConfigs();
            selectedTracks.sort((o1, o2) -> o1.order - o2.order);
            config.tracks = selectedTracks;
        }

        return new Genome(config);

    }
}
