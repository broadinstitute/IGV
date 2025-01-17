package org.broad.igv.ucsc.bb.codecs;

import org.broad.igv.Globals;
import org.broad.igv.bedpe.BedPEFeature;
import org.broad.igv.feature.BasicFeature;
import org.broad.igv.feature.Exon;
import org.broad.igv.feature.tribble.IGVBEDCodec;
import org.broad.igv.ucsc.bb.BBUtils;
import org.broad.igv.ucsc.bb.BedData;

import java.util.List;


public class BBInteractCodec implements BBCodec {


    private final BBUtils.ASTable astable;

    int standardFieldCount;
    IGVBEDCodec igvBedCodec;

    public BBInteractCodec(int standardFieldCount, BBUtils.ASTable autosql) {
        this.astable = autosql;
        this.standardFieldCount = standardFieldCount;
        this.igvBedCodec = new IGVBEDCodec();   // Backing "tribble" codec
    }

    /*
            feature.chr1 = tokens[5]
        feature.start1 = Number.parseInt(tokens[6])
        feature.end1 = Number.parseInt(tokens[7])

        feature.chr2 = tokens[10]
        feature.start2 = Number.parseInt(tokens[11])
        feature.end2 = Number.parseInt(tokens[12])

        feature.name = tokens[0]
        feature.score = Number(tokens[1])
        feature.value = Number(tokens[2])
        feature.color = tokens[4] === '.' ? undefined : tokens[4] === "0" ? "rgb(0,0,0)" : tokens[4]

     */

    public BedPEFeature decode(BedData bedData) {

        String[] restOfFields = Globals.tabPattern.split(bedData.restOfFields, -1);
        String[] tokens = new String[this.standardFieldCount];

        String chr1 = tokens[5];
        int start1 = Integer.parseInt(tokens[6]);
        int end1 = Integer.parseInt(tokens[7]);

        String chr2 = tokens[10];
        int start2 = Integer.parseInt(tokens[11]);
        int end2 = Integer.parseInt(tokens[12]);

        String name = tokens[0];
        double score = Double.parseDouble(tokens[1]);
        double value = Double.parseDouble(tokens[2]);
        String color = tokens[4].equals(".") ? null : tokens[4].equals("0") ? "rgb(0,0,0)" : tokens[4];

        BedPEFeature feature = new BedPEFeature(chr1, start1, end1, chr2, start2, end2);

        return feature;

        //    public BedPEFeature(String chr1, int start1, int end1, String chr2, int start2, int end2) {



        // "Non standard" fields
//        if (astable != null && astable.fields.size() > standardFieldCount) {
//            LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
//            List<BBUtils.ASField> fields = astable.fields;
//            for (int i = this.standardFieldCount; i < fields.size(); i++) {
//                BBUtils.ASField field = fields.get(i);
//                attributes.put(field.name, restOfFields[i - 3]);
//            }
//            feature.setAttributes(attributes);
//
//            if (attributes.containsKey("exonFrames")) {
//                computeExonFrames(feature, attributes.get("exonFrames"));
//            }
//        }

    }

    private void computeExonFrames(BasicFeature feature, String exonFrames) {
        String[] frameBuffer = Globals.commaPattern.split(exonFrames);
        List<Exon> exons = feature.getExons();
        for (int i = 0; i < frameBuffer.length; i++) {
            int exonFrame = Integer.parseInt(frameBuffer[i].trim());
            if (exonFrame == -1) {
                exons.get(i).setNonCoding(true);
            } else {
                exons.get(i).setReadingFrame(exonFrame);
            }
        }
    }


}
