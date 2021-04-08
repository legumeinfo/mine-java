package org.ncgr.intermine.bio.web.displayer;

import java.util.Map;
import java.util.HashMap;
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

import org.json.JSONArray;
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

        // get yuck = gensp.strain.assembly for this GWASResult's chromosomes
        PathQuery yuckQuery = new PathQuery(im.getModel());
        yuckQuery.addViews(
                           "GWASResult.marker.organism.abbreviation", // 0
                           "GWASResult.marker.strain.identifier",     // 1
                           "GWASResult.marker.assemblyVersion"        // 2
                           );
        yuckQuery.addConstraint(Constraints.eq("GWASResult.gwas.primaryIdentifier", gwasIdentifier));
        ExportResultsIterator yuckResults;
        try {
            yuckResults = executor.execute(yuckQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        String gensp = null;
        String strainId = null;
        String assemblyVersion = null;
        if (yuckResults.hasNext()) {
            List<ResultElement> row = yuckResults.next();
            gensp = (String) row.get(0).getField();
            strainId = (String) row.get(1).getField();
            assemblyVersion = (String) row.get(2).getField();
        }
        String yuck = gensp+"."+strainId+"."+assemblyVersion;
        
        // query all chromosomes for this yuck
        PathQuery chrQuery = new PathQuery(im.getModel());
        chrQuery.addViews(
                          "Chromosome.secondaryIdentifier", // 0
                          "Chromosome.length"               // 1
                          );
        chrQuery.addOrderBy("Chromosome.secondaryIdentifier", OrderDirection.ASC);
        chrQuery.addConstraint(Constraints.contains("Chromosome.primaryIdentifier", yuck));
        ExportResultsIterator chrResults;
        try {
            chrResults = executor.execute(chrQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        List<Object> chromosomeLengths = new ArrayList<>();
        while (chrResults.hasNext()) {
            List<ResultElement> row = chrResults.next();
            String chrId = (String) row.get(0).getField();
            int chrLength = (Integer) row.get(1).getField();
            chromosomeLengths.add(chrLength);
        }
        
        // GWASResult query
        // NOTE: trait.primaryIdentifier is not unique! trait|marker is unique.
	PathQuery query = new PathQuery(im.getModel());
        query.addViews(
                       "GWASResult.trait.primaryIdentifier",               // 0
                       "GWASResult.pValue",                                // 1
                       "GWASResult.marker.secondaryIdentifier",            // 2
                       "GWASResult.marker.chromosome.secondaryIdentifier", // 3
                       "GWASResult.marker.chromosomeLocation.start",       // 4
                       "GWASResult.marker.chromosomeLocation.end"          // 5
                       );
        query.addConstraint(Constraints.eq("GWASResult.gwas.primaryIdentifier", gwasIdentifier));
        query.addOrderBy("GWASResult.marker.chromosome.secondaryIdentifier", OrderDirection.ASC);
        // storage, result maps are keyed by trait.primaryIdentifier (which should be unique)
        Map<String,String> traitIdentifiers = new HashMap<>();
        Map<String,Double> resultPValues = new HashMap<>();
	Map<String,String> resultMarkers = new HashMap<>();
        Map<String,String> markerChromosomes = new HashMap<>();
        Map<String,Integer> markerPositions = new HashMap<>();
        // execute the query
        ExportResultsIterator results;
        try {
            results = executor.execute(query);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        while (results.hasNext()) {
            List<ResultElement> row = results.next();
            String traitIdentifier = (String) row.get(0).getField();        // GWASResult.trait.primaryIdentifier
            Double pValue = (Double) row.get(1).getField();                 // GWASResult.pValue
            String markerIdentifier = (String) row.get(2).getField();       // GWASResult.marker.secondaryIdentifier
            String chromosomeIdentifier = (String) row.get(3).getField();   // GWASResult.marker.chromosome.secondaryIdentifier
            int markerStart = (Integer) row.get(4).getField();          // GWASResult.marker.chromosomeLocation.start
            int markerEnd = (Integer) row.get(5).getField();            // GWASResult.marker.chromosomeLocation.end
            int markerPosition = (int)((double)(markerStart+markerEnd)/2);
            String key = traitIdentifier+"|"+markerIdentifier;
            traitIdentifiers.put(key, traitIdentifier);
	    resultPValues.put(key, pValue);
	    resultMarkers.put(key, markerIdentifier);
	    markerChromosomes.put(key, chromosomeIdentifier);
	    markerPositions.put(key, markerPosition);
        }

        List<Object> vars = new ArrayList<>();
        List<Object> data = new ArrayList<>();
        for (String key : resultPValues.keySet()) {
	    double pValue = resultPValues.get(key);
	    String markerIdentifier = resultMarkers.get(key);
            int markerPosition = markerPositions.get(key);
            String chromosomeIdentifier = markerChromosomes.get(key);
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
            // vars
            vars.add(markerIdentifier);
            // data
            List<Object> dataRecord = new ArrayList<>();
            dataRecord.add(chromosomeNumber);
            dataRecord.add(markerPosition);
            dataRecord.add(-Math.log10(pValue));
            data.add(dataRecord);
        }

        // for decorations
        List<Object> traits = new ArrayList<>();
        for (String key : traitIdentifiers.keySet()) {
            traits.add(traitIdentifiers.get(key));
        }

        // JSONArrays
        JSONArray chrsJSON = new JSONArray(chromosomeLengths);
        JSONArray varsJSON = new JSONArray(vars);
        JSONArray dataJSON = new JSONArray(data);
        JSONArray traitsJSON = new JSONArray(traits);

        // send the data on its way
        request.setAttribute("chromosomeLengths", chrsJSON.toString());
        request.setAttribute("vars", varsJSON.toString());
        request.setAttribute("data", dataJSON.toString());
        request.setAttribute("traits", traitsJSON.toString());
        request.setAttribute("yuck", yuck);
    }
}
