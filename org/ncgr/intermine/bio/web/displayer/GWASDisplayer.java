package org.ncgr.intermine.bio.web.displayer;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

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
            throw new RuntimeException("Error getting GWAS.primaryIdentifier.", ex);
        }

        PathQueryExecutor executor = im.getPathQueryExecutor();

        // store the GWASResults in maps
	Map<String,Double> resultPValueMap = new LinkedHashMap<>();
	Map<String,String> resultPhenotypeMap = new LinkedHashMap<>();
	Map<String,String> resultMarkerMap = new LinkedHashMap<>();
        Map<String,String> markerChromosomeMap = new LinkedHashMap<>();
        Map<String,Integer> markerStartMap = new LinkedHashMap<>();
        Map<String,Integer> markerEndMap = new LinkedHashMap<>();
        Map<String,Integer> chromosomeLengthMap = new LinkedHashMap<>();
	PathQuery query = new PathQuery(im.getModel());
        query.addViews(
		       "GWASResult.identifier",                            // 0
                       "GWASResult.pValue",                                // 1
                       "GWASResult.phenotype.primaryIdentifier",           // 2
                       "GWASResult.marker.primaryIdentifier",            // 3
                       "GWASResult.marker.chromosome.secondaryIdentifier", // 4
                       "GWASResult.marker.chromosome.length",              // 5
                       "GWASResult.marker.chromosomeLocation.start",       // 6
                       "GWASResult.marker.chromosomeLocation.end"          // 7
                       );
        query.addConstraint(Constraints.eq("GWASResult.gwas.primaryIdentifier", gwasIdentifier));
        query.addOrderBy("GWASResult.marker.chromosome.secondaryIdentifier", OrderDirection.ASC);
        query.addOrderBy("GWASResult.marker.primaryIdentifier", OrderDirection.ASC);
        ExportResultsIterator results;
        try {
            results = executor.execute(query);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        while (results.hasNext()) {
            List<ResultElement> row = results.next();
	    String resultIdentifier = (String) row.get(0).getField();       // GWASResult.identifier
            Double pValue = (Double) row.get(1).getField();                 // GWASResult.pValue
            String phenotypeIdentifier = (String) row.get(2).getField();    // GWASResult.phenotype.primaryIdentifier
            String markerIdentifier = (String) row.get(3).getField();       // GWASResult.marker.primaryIdentifier
            String chromosomeIdentifier = (String) row.get(4).getField();   // GWASResult.marker.chromosome.secondaryIdentifier
            Integer chromosomeLength = (Integer) row.get(5).getField();     // GWASResult.marker.chromosome.length
            Integer markerStart = (Integer) row.get(6).getField();          // GWASResult.marker.chromosomeLocation.start
            Integer markerEnd = (Integer) row.get(7).getField();            // GWASResult.marker.chromosomeLocation.end
	    resultPValueMap.put(resultIdentifier, pValue);
	    resultPhenotypeMap.put(resultIdentifier, phenotypeIdentifier);
	    resultMarkerMap.put(resultIdentifier, markerIdentifier);
	    markerChromosomeMap.put(markerIdentifier, chromosomeIdentifier);
	    markerStartMap.put(markerIdentifier, markerStart);
	    markerEndMap.put(markerIdentifier, markerEnd);
	    chromosomeLengthMap.put(chromosomeIdentifier, chromosomeLength);
        }

        // form the maps for the y attribute JSON
        Map<String,Object> smpsMap = new LinkedHashMap<>();
        List<String> smps = new ArrayList<>();
	smps.add("markerPosition");
	smps.add("log10pValue");
        smpsMap.put("smps", smps);

        Map<String,Object> varsMap = new LinkedHashMap<>();
        List<String> vars = new ArrayList<>();
        for (String resultIdentifier : resultPValueMap.keySet()) {
            vars.add(resultMarkerMap.get(resultIdentifier));
        }
	varsMap.put("vars", vars);

        Map<String,Object> dataMap = new LinkedHashMap<>();
        List<Object> data = new ArrayList<>();
        for (String resultIdentifier : resultPValueMap.keySet()) {
	    double pValue = resultPValueMap.get(resultIdentifier);
	    String markerIdentifier = resultMarkerMap.get(resultIdentifier);
            String chromosomeIdentifier = markerChromosomeMap.get(markerIdentifier);
            int chromosomeLength = chromosomeLengthMap.get(chromosomeIdentifier);
            // get the chromosome number from the identifier Chr01 or Chr1
            int len = chromosomeIdentifier.length();
            int chromosomeNumber = 0;
            try {
                // two digits?
                chromosomeNumber = Integer.parseInt(chromosomeIdentifier.substring(len-2,len));
            } catch (Exception e) {
                // one digit?
                chromosomeNumber = Integer.parseInt(chromosomeIdentifier.substring(len-1,len));
            }
            int markerStart = markerStartMap.get(markerIdentifier);
            double frac = (double) markerStart / (double) chromosomeLength;
            double markerPosition = (double) (chromosomeNumber-1) + frac;
            double log10pValue = -Math.log10(pValue);
            double[] values = { markerPosition, log10pValue };
            data.add(values);
        }
        dataMap.put("data", data);

	// form the JSON for the y attribute
        Map<String,Object> yMap = new LinkedHashMap<>();
        yMap.put("smps", smps);
        yMap.put("vars", vars);
        yMap.put("data", data);
        JSONObject y = new JSONObject(yMap);

        // form the decoration markers JSON
        Map<String,Object> markerMap = new LinkedHashMap<>();
	List<String> samples = new ArrayList<>();
	samples.add("markerPosition");
	samples.add("log10pValue");
        List<Map<String,Object>> markerList = new ArrayList<>();
        for (String resultIdentifier : resultPValueMap.keySet()) {
            Map<String,Object> resultMap = new LinkedHashMap<>();
            resultMap.put("variable", resultMarkerMap.get(resultIdentifier));
            resultMap.put("text", resultIdentifier);
            resultMap.put("type", "text");
            resultMap.put("sample", samples);
            markerList.add(resultMap);
        }
	boolean hasMarkers = markerList.size()>0;
        markerMap.put("marker", markerList);
        JSONObject markers = new JSONObject(markerMap);

        // send the data on its way
	request.setAttribute("hasMarkers", String.valueOf(hasMarkers));
        request.setAttribute("y", y.toString());
        request.setAttribute("markers", markers.toString());
    }
}
