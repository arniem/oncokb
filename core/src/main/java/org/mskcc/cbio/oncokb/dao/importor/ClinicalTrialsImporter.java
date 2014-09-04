/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mskcc.cbio.oncokb.dao.importor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.mskcc.cbio.oncokb.bo.AlterationBo;
import org.mskcc.cbio.oncokb.bo.ClinicalTrialBo;
import org.mskcc.cbio.oncokb.bo.DrugBo;
import org.mskcc.cbio.oncokb.bo.TumorTypeBo;
import org.mskcc.cbio.oncokb.model.Alteration;
import org.mskcc.cbio.oncokb.model.ClinicalTrial;
import org.mskcc.cbio.oncokb.model.Drug;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.mskcc.cbio.oncokb.util.ApplicationContextSingleton;
import org.mskcc.cbio.oncokb.util.FileUtils;
import org.w3c.dom.NodeList;

/**
 *
 * @author jgao
 */
public class ClinicalTrialsImporter {
    private static final String NCI_CLINICAL_TRIAL_FOLDER = "/Users/jgao/projects/oncokb-data/CTGovProtocol";
    
    public static void main(String[] args) throws IOException {
        TumorTypeBo tumorTypeBo = ApplicationContextSingleton.getTumorTypeBo();
        List<TumorType> tumorTypes = tumorTypeBo.findAll();
        
        ClinicalTrialBo clinicalTrialBo = ApplicationContextSingleton.getClinicalTrialBo();
        List<String> files = FileUtils.getFilesInFolder(NCI_CLINICAL_TRIAL_FOLDER, "xml");
        int n = files.size();
        System.out.println("Found "+n+" trials");
        for (int i=0; i<n; i++) {
            String file = files.get(i);
            System.out.print("Process... "+(i+1)+"/"+n);
            ClinicalTrial trial = null;
            try {
                trial = parseNci(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (trial!=null) {
                matchTumorTypes(trial, tumorTypes);
                clinicalTrialBo.save(trial);
            }
            System.out.println();
        }
        
    }
    
    private static void matchTumorTypes(ClinicalTrial trial, List<TumorType> tumorTypes) {
        String condition = trial.getDiseaseCondition().toLowerCase();
        Set<TumorType> matched = new HashSet<TumorType>();
        for (TumorType tumorType : tumorTypes) {
            for (String keyword : tumorType.getClinicalTrialKeywords()) {
                if (condition.contains(keyword.toLowerCase())) {
                    matched.add(tumorType);
                }
            }
        }
        trial.setTumorTypes(matched);
    }
    
    private static ClinicalTrial parseNci(String file) throws ParserConfigurationException, SAXException, IOException {  
        
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        
        Document doc = db.parse(file);
        Element docEle = doc.getDocumentElement();
        String cdrId = docEle.getAttribute("id");
        
        String status = getText(docEle, "CurrentProtocolStatus");
        if (!isTrialOpen(status)) return null;
        
        String nctId = getText(docEle, "IDInfo/NCTID");
        
        System.out.print(" "+nctId);
        
        ClinicalTrial clinicalTrial = parseClinicalTrialsGov(nctId, db);
        if (clinicalTrial!=null) {
            clinicalTrial.setCdrId(cdrId);
        }
        
        return clinicalTrial;
    }
    
    private static ClinicalTrial parseClinicalTrialsGov(String nctId, DocumentBuilder db) throws SAXException, IOException {
        String strUrl = "http://clinicaltrials.gov/show/"+nctId+"?displayxml=true";
        
        Document doc = db.parse(strUrl);
        Element docEle = doc.getDocumentElement();
        
        if (!isLocationUsa(docEle)) return null;
        
        System.out.print(" in US");
        
        String briefTitle = getText(docEle, "brief_title");
//        String officialTitle = getText(docEle, "official_title");
        String briefSummary = getText(docEle, "brief_summary/textblock");
//        String detailedDesc = getText(docEle, "detailed_description/textblock");
        String status = getText(docEle, "overall_status");
        String phase = getText(docEle, "phase");
        String condition = getText(docEle, "condition");
        String eligibility = getText(docEle, "eligibility/criteria/textblock");
        eligibility = replaceUTFcode(eligibility);
        
        if (!isTrialOpen(status)) {
            return null;
        }
        
        ClinicalTrial trial = new ClinicalTrial();
        trial.setNctId(nctId);
        trial.setTitle(briefTitle);
        trial.setPhase(phase);
        trial.setPurpose(briefSummary);
        trial.setRecruitingStatus(status);
        trial.setDiseaseCondition(condition);
        trial.setEligibilityCriteria(eligibility);
        trial.setDrugs(parseDrugs(docEle));
        trial.setAlterations(parseAlterations(docEle));
        
        return trial;
    }
    
    private static Set<Drug> parseDrugs(Element docEle) {
        List<String> drugNames = getTexts(docEle, "intervention_browse/mesh_term");
        Set<Drug> drugs = new HashSet<Drug>();
        DrugBo drugBo = ApplicationContextSingleton.getDrugBo();

        for (String drugName : drugNames) {
            Drug drug = drugBo.guessUnambiguousDrug(drugName);
            if (drug==null) {
                drug = new Drug();
                drug.setDrugName(drugName);
                drugBo.save(drug);
            }
            drugs.add(drug);
        }
        
        return drugs;
    }
    
    
    private static List<Alteration> allAlterations = null;
    private static Set<Alteration> parseAlterations(Element docEle) {
        if (allAlterations==null) {
            AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
            allAlterations = alterationBo.findAll();
        }
        
        Set<Alteration> ret = new HashSet<Alteration>();
        List<String> keywords = getTexts(docEle, "keyword");
        for (Alteration alteration : allAlterations) {
            for (String keyword : keywords) {
                if (keyword.toUpperCase().contains(alteration.getAlteration().toUpperCase())) {
                    ret.add(alteration);
                }
            }
        }
        
        return ret;
    }
    
    private static boolean isLocationUsa(Element docEle) {
        List<String> locs = getTexts(docEle, "location_countries/country");
        for (String loc : locs) {
            if (loc.equalsIgnoreCase("United States")) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isTrialOpen(String status) {
        return !status.equalsIgnoreCase("Terminated") &&
                !status.equalsIgnoreCase("Suspended") &&
                !status.equalsIgnoreCase("Completed") &&
                !status.equalsIgnoreCase("Closed") &&
                !status.equalsIgnoreCase("Active, not recruiting") &&
                !status.equalsIgnoreCase("Withdrawn");
    }
    
    private static String getText(Element el, String path) {
        return getTexts(el, path).get(0);
    }
    
    private static List<String> getTexts(Element el, String path) {
        LinkedList<Element> els = new LinkedList<Element>();
        els.add(el);
        String[] tags = path.split("/");
        for (String tag : tags) {
            int n = els.size();
            for (int i=0; i<n; i++) {
                Element e = els.poll();
                NodeList nl = e.getElementsByTagName(tag);
                for (int j=0; j<nl.getLength(); j++) {
                    els.add((Element)nl.item(j));
                }
            }
        }
        
        List<String> ret = new ArrayList<String>();
        for (Element e : els) {
            ret.add(e.getChildNodes().item(0).getNodeValue());
        }
        
        return ret;
    }
    
    private static String replaceUTFcode(String str) {
        return str.replaceAll("\\u2265", ">=").replaceAll("\\u2264", ">=");
    }
}
