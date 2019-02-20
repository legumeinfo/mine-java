package org.ncgr.intermine.bio.web.displayer;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;

import org.intermine.api.InterMineAPI;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;

import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;

import org.intermine.objectstore.ObjectStoreException;

import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;

import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

import org.json.JSONObject;

/**
 * Generates JSON for CanvasXpress plot of p-values versus marker position.
 *
 * @author Sam Hokin
 */
public class GWASDisplayer extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GWASDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        InterMineObject gwas = reportObject.getObject();
        String gwasIdentifier;
        try {
            gwasIdentifier = (String) gwas.getFieldValue("primaryIdentifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting primaryIdentifier.", ex);
        }

        PathQueryExecutor executor = im.getPathQueryExecutor();

        // get the markers and phenotypes from the GWASResults
        Map<String,String> markerChromosomeMap = new LinkedHashMap<String,String>();
        Map<String,Integer> chromosomeLengthMap = new LinkedHashMap<String,Integer>();
        Map<String,Integer> markerStartMap = new LinkedHashMap<String,Integer>();
        Map<String,Integer> markerEndMap = new LinkedHashMap<String,Integer>();
        Map<String,String> markerPhenotypeMap = new LinkedHashMap<String,String>();
        Map<String,Double> markerPvalueMap = new LinkedHashMap<String,Double>();
        // GWASResult.marker.primaryIdentifier,            // 0
        // GWASResult.marker.chromosome.primaryIdentifier, // 1
        // GWASResult.marker.chromosome.length,            // 2
        // GWASResult.marker.chromosomeLocation.start,     // 3
        // GWASResult.marker.chromosomeLocation.end,       // 4
        // GWASResult.phenotype.primaryIdentifier,         // 5
        // GWASResult.pValue                               // 6
        PathQuery query = new PathQuery(im.getModel());
        query.addViews(
                       "GWASResult.marker.primaryIdentifier",            // 0
                       "GWASResult.marker.chromosome.primaryIdentifier", // 1
                       "GWASResult.marker.chromosome.length",            // 2
                       "GWASResult.marker.chromosomeLocation.start",     // 3
                       "GWASResult.marker.chromosomeLocation.end",       // 4
                       "GWASResult.phenotype.primaryIdentifier",         // 5
                       "GWASResult.pValue"                               // 6
                       );
        query.addConstraint(Constraints.eq("GWASResult.study.primaryIdentifier", gwasIdentifier));
        query.addOrderBy("GWASResult.marker.chromosome.primaryIdentifier", OrderDirection.ASC);
        query.addOrderBy("GWASResult.marker.primaryIdentifier", OrderDirection.ASC);
        ExportResultsIterator results;
        try {
            results = executor.execute(query);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        while (results.hasNext()) {
            List<ResultElement> row = results.next();
            String markerIdentifier = (String) row.get(0).getField();
            String chromosomeIdentifier = (String) row.get(1).getField();
            Integer chromosomeLength = (Integer) row.get(2).getField();
            Integer markerStart = (Integer) row.get(3).getField();
            Integer markerEnd = (Integer) row.get(4).getField();
            String phenotypeIdentifier = (String) row.get(5).getField();
            Double pValue = (Double) row.get(6).getField();
            markerChromosomeMap.put(markerIdentifier, chromosomeIdentifier);
            chromosomeLengthMap.put(chromosomeIdentifier, chromosomeLength);
            markerStartMap.put(markerIdentifier, markerStart);
            markerEndMap.put(markerIdentifier, markerEnd);
            markerPhenotypeMap.put(markerIdentifier, phenotypeIdentifier);
            markerPvalueMap.put(markerIdentifier, pValue);
        }

        // form the JSON for the displayer
        String[] smps = { "markerPosition", "log10pValue" };
        Map<String,Object> smpsMap = new LinkedHashMap<String,Object>();
        smpsMap.put("smps", smps);

        List<String> vars = new LinkedList<String>();
        for (String markerIdentifier : markerChromosomeMap.keySet()) {
            vars.add(markerIdentifier);
        }
        Map<String,Object> varsMap = new LinkedHashMap<String,Object>();
        varsMap.put("vars", vars);

        List<Object> data = new LinkedList<Object>();
        for (String markerIdentifier : markerChromosomeMap.keySet()) {
            String chromosomeIdentifier = markerChromosomeMap.get(markerIdentifier);
            int chromosomeLength = chromosomeLengthMap.get(chromosomeIdentifier);
            // assume chromosome name ends in NN so we can get a number
            int len = chromosomeIdentifier.length();
            int chromosomeNumber = Integer.parseInt(chromosomeIdentifier.substring(len-2,len));
            int markerStart = markerStartMap.get(markerIdentifier);
            double frac = (double) markerStart / (double) chromosomeLength;
            double markerPosition = (double) (chromosomeNumber-1) + frac;
            double log10pValue = -Math.log10((double) markerPvalueMap.get(markerIdentifier));
            double[] values = { markerPosition, log10pValue };
            data.add(values);
        }
        Map<String,Object> dataMap = new LinkedHashMap<String,Object>();
        dataMap.put("data", data);

        Map<String,Object> yMap = new LinkedHashMap<String,Object>();
        yMap.put("smps", smps);
        yMap.put("vars", vars);
        yMap.put("data", data);
        JSONObject y = new JSONObject(yMap);

        // decorative markers show the markers :)
        String[] samples = {"markerPosition","log10pValue"};
        List<Map<String,Object>> markerList = new LinkedList<Map<String,Object>>();
        for (String marker : markerPhenotypeMap.keySet()) {
            String phenotype = markerPhenotypeMap.get(marker);
            Map<String,Object> markerMap = new LinkedHashMap<String,Object>();
            markerMap.put("variable", marker);
            markerMap.put("text", phenotype);
            markerMap.put("type", "text");
            markerMap.put("sample", samples);
            markerList.add(markerMap);
        }
        Map<String,Object> markerMap = new LinkedHashMap<String,Object>();
        markerMap.put("marker", markerList);
        JSONObject markers = new JSONObject(markerMap);
        
        // send the data on its way
        request.setAttribute("y", y.toString());
        request.setAttribute("markers", markers.toString());
    }
}
