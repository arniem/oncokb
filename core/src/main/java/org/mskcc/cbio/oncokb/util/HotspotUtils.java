package org.mskcc.cbio.oncokb.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.cmo.cancerhotspots.model.SingleResidueHotspotMutation;
import org.mskcc.cbio.oncokb.model.Alteration;
import org.mskcc.cbio.oncokb.model.VariantConsequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Hongxin on 11/03/16.
 */
public class HotspotUtils {
    private static List<SingleResidueHotspotMutation> hotspotMutations = null;

    public static void getHotspotsFromRemote() {
        String cancerHotspotsUrl = null;
        try {
            cancerHotspotsUrl = PropertiesUtils.getProperties("cancerhotspots.single");
            if (cancerHotspotsUrl != null && cancerHotspotsUrl.isEmpty()) {
                cancerHotspotsUrl = null;
            }
        } catch (IOException e) {
            System.out.println("Please specify cancerhotspot.single url in the config.properties file.");
            e.printStackTrace();
        }

        String response = null;

        if (cancerHotspotsUrl != null) {
            response = HttpUtils.postRequest(cancerHotspotsUrl, "", true);
        }

        if (cancerHotspotsUrl == null || response.equals("TIMEOUT")) {
            Gson gson = new GsonBuilder().create();
            SingleResidueHotspotMutation[] mutations = gson.fromJson(new BufferedReader(new InputStreamReader(HotspotUtils.class.getResourceAsStream("/data/cancer-hotspots-public-single.json"))), SingleResidueHotspotMutation[].class);
            hotspotMutations = new ArrayList<>(Arrays.asList(mutations));
            if (hotspotMutations == null) {
                hotspotMutations = new ArrayList<>();
            }
        } else {
            if (response != null) {
                try {
                    hotspotMutations = new ObjectMapper().readValue(response, new TypeReference<List<SingleResidueHotspotMutation>>() {
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                hotspotMutations = new ArrayList<>();
            }
        }
    }

    public static List<SingleResidueHotspotMutation> getHotspots() {
        if (hotspotMutations == null) {
            getHotspotsFromRemote();
        }
        return hotspotMutations;
    }

    public static Boolean isHotspot(Alteration alteration) {
        Boolean isHotspot = false;
        if (alteration != null && alteration.getGene() != null) {
            if (alteration.getConsequence() == null) {
                AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());
            }

            Integer proteinStart = alteration.getProteinStart();
            Integer proteinEnd = alteration.getProteinEnd();
            VariantConsequence missense = VariantConsequenceUtils.findVariantConsequenceByTerm("missense_variant");
            VariantConsequence insertion = VariantConsequenceUtils.findVariantConsequenceByTerm("inframe_insertion");
            VariantConsequence deletion = VariantConsequenceUtils.findVariantConsequenceByTerm("inframe_deletion");

            if (proteinStart != null && alteration.getConsequence().equals(missense)) {
                if (proteinEnd == null) {
                    proteinEnd = proteinStart;
                }
                for (SingleResidueHotspotMutation hotspotMutation : hotspotMutations) {
                    if (hotspotMutation.getType().equals("single residue")
                        && hotspotMutation.getHugoSymbol().equals(alteration.getGene().getHugoSymbol())
                        && hotspotMutation.getAminoAcidPosition() != null
                        && proteinStart >= hotspotMutation.getAminoAcidPosition().getStart()
                        && proteinEnd <= hotspotMutation.getAminoAcidPosition().getEnd()) {
                        isHotspot = true;
                        break;
                    }
                }
            } else if (alteration.getConsequence().equals(insertion) || alteration.getConsequence().equals(deletion)) {
                for (SingleResidueHotspotMutation hotspotMutation : hotspotMutations) {
                    if (hotspotMutation.getType().equals("in-frame indel")
                        && hotspotMutation.getHugoSymbol().equals(alteration.getGene().getHugoSymbol())
                        && hotspotMutation.getAminoAcidPosition() != null
                        && proteinEnd >= hotspotMutation.getAminoAcidPosition().getStart()
                        && proteinStart <= hotspotMutation.getAminoAcidPosition().getEnd()) {
                        isHotspot = true;
                        break;
                    }
                }
            }
        }
        return isHotspot;
    }
}
