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
import org.intermine.model.bio.GWASResult;

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
        PathQueryExecutor executor = im.getPathQueryExecutor();

        // get GWAS.primaryIdentifier
        InterMineObject gwas = reportObject.getObject();
        String gwasIdentifier = null;
        try {
            gwasIdentifier = (String) gwas.getFieldValue("primaryIdentifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }

        // get GWASResult records for this GWAS
        Map<String,Double> markerPValues = new HashMap<>();
        Map<String,String> markerTraits = new HashMap<>();
        PathQuery gwasResultQuery = new PathQuery(im.getModel());
        gwasResultQuery.addView("GWASResult.pValue");                  // 0
        gwasResultQuery.addView("GWASResult.markerName");              // 1
        gwasResultQuery.addView("GWASResult.trait.primaryIdentifier"); // 2
        gwasResultQuery.addConstraint(Constraints.eq("GWASResult.gwas.primaryIdentifier", gwasIdentifier));
        gwasResultQuery.addOrderBy("GWASResult.trait.primaryIdentifier", OrderDirection.ASC);
        try {
            ExportResultsIterator gwasResults = executor.execute(gwasResultQuery);
            while (gwasResults.hasNext()) {
                List<ResultElement> row = gwasResults.next();
                double pValue = (double) row.get(0).getField();
                String markerName = (String) row.get(1).getField();
                String trait = (String) row.get(2).getField();
                markerPValues.put(markerName, pValue);
                markerTraits.put(markerName, trait);
            }
        } catch (ObjectStoreException ex) {
            throw new RuntimeException(ex);
        }

        // get the GeneticMarker values per genome=first three parts of primaryIdentifier
        List<String> genomes = new ArrayList<>();                 // alpha ordered
        Map<String,List<String>> genomeMarkers = new HashMap<>(); // keyed by genome = gensp.strain.gnm
        Map<String,String> markerNames = new HashMap<>();         // keyed by GeneticMarker.primaryIdentifier
        Map<String,String> markerChromosomes = new HashMap<>();   // keyed by GeneticMarker.primaryIdentifier
        Map<String,Integer> markerPositions = new HashMap<>();    // keyed by GeneticMarker.primaryIdentifier
        PathQuery markerQuery = new PathQuery(im.getModel());
        markerQuery.addView("GeneticMarker.primaryIdentifier");              // 0
        markerQuery.addView("GeneticMarker.name");                           // 1
        markerQuery.addView("GeneticMarker.chromosome.primaryIdentifier");   // 2
        markerQuery.addView("GeneticMarker.chromosome.name");                // 3
        markerQuery.addView("GeneticMarker.chromosome.length");              // 4
        markerQuery.addView("GeneticMarker.chromosomeLocation.start");       // 5
        markerQuery.addView("GeneticMarker.chromosomeLocation.end");         // 6
        markerQuery.addConstraint(Constraints.oneOfValues("GeneticMarker.name", markerTraits.keySet()));
        markerQuery.addOrderBy("GeneticMarker.primaryIdentifier", OrderDirection.ASC);
        try {
            ExportResultsIterator markerResults = executor.execute(markerQuery);
            while (markerResults.hasNext()) {
                List<ResultElement> row = markerResults.next();
                //
                String markerIdentifier = (String) row.get(0).getField();
                String markerName = (String) row.get(1).getField();
                String chromosomeIdentifier = (String) row.get(2).getField();
                String chromosomeName = (String) row.get(3).getField();
                int chromosomeLength = (int) row.get(4).getField();
                int start = (int) row.get(5).getField();
                int end = (int) row.get(6).getField();
                //
                int position = (start + end)/2;
                String[] parts = markerIdentifier.split("\\.");
                String genome = parts[0]+"."+parts[1]+"."+parts[2]; // phavu.G19833.gnm1
                if (!genomes.contains(genome)) {
                    genomes.add(genome);
                    genomeMarkers.put(genome, new ArrayList<String>());
                }
                genomeMarkers.get(genome).add(markerIdentifier);
                markerNames.put(markerIdentifier, markerName);
                markerChromosomes.put(markerIdentifier, chromosomeIdentifier);
                markerPositions.put(markerIdentifier, position);
            }
        } catch (ObjectStoreException ex) {
            throw new RuntimeException(ex);
        }

        // get chromosome lengths for each genome in order of chromosome
        Map<String,List<String>> genomeChromosomes = new HashMap<>(); // keyed by genome = gensp.strain.gnm
        Map<String,String> chromosomeNames = new HashMap<>();         // keyed by Chromosome.primaryIdentifier
        Map<String,Integer> chromosomeLengths = new HashMap<>();      // keyed by Chromosome.primaryIdentifier
        for (String genome : genomes) {
            genomeChromosomes.put(genome, new ArrayList<String>());
            PathQuery chromosomeQuery = new PathQuery(im.getModel());
            chromosomeQuery.addView("Chromosome.primaryIdentifier");              // 0
            chromosomeQuery.addView("Chromosome.name");                           // 1
            chromosomeQuery.addView("Chromosome.length");                         // 2
            chromosomeQuery.addConstraint(Constraints.contains("Chromosome.primaryIdentifier", genome));
            chromosomeQuery.addOrderBy("Chromosome.primaryIdentifier", OrderDirection.ASC);
            try {
                ExportResultsIterator chromosomeResults = executor.execute(chromosomeQuery);
                while (chromosomeResults.hasNext()) {
                    List<ResultElement> row = chromosomeResults.next();
                    //
                    String primaryIdentifier = (String) row.get(0).getField();
                    String name = (String) row.get(1).getField();
                    int length = (int) row.get(2).getField();
                    //
                    genomeChromosomes.get(genome).add(primaryIdentifier);
                    chromosomeNames.put(primaryIdentifier, name);
                    chromosomeLengths.put(primaryIdentifier, length);
                }
            } catch (ObjectStoreException ex) {
                throw new RuntimeException(ex);
            }
        }

        // Map of Lists of objects keyed by genome
        Map<Object,Object> genomesMap = new HashMap<>();
        for (String genome : genomes) {
            // Map of objects keyed by their name
            Map<Object,Object> objectsMap = new HashMap<>();
            // List of chromosome lengths for this genome
            List<Object> chromosomeLengthsList = new ArrayList<>();
            for (String chromosomeIdentifier : genomeChromosomes.get(genome)) {
                chromosomeLengthsList.add(chromosomeLengths.get(chromosomeIdentifier));
            }
            objectsMap.put("chromosomeLengths", chromosomeLengthsList);
            // vars and data for plot
            List<Object> traitsList = new ArrayList<>();
            List<Object> vars = new ArrayList<>();
            List<Object> data = new ArrayList<>();
            for (String marker : genomeMarkers.get(genome)) {
                String name = markerNames.get(marker);
                String chromosomeIdentifier = markerChromosomes.get(marker);
                String chromosomeName = chromosomeNames.get(chromosomeIdentifier);
                traitsList.add(markerTraits.get(name));
                // vars
                vars.add(name);
                // chromosomes have to be numbers for plot
                int len = chromosomeName.length();
                int chromosomeNumber = 0;
                try {
                    // two digits?
                    chromosomeNumber = Integer.parseInt(chromosomeName.substring(len-2,len));
                } catch (Exception e) {
                    // one digit?
                    chromosomeNumber = Integer.parseInt(chromosomeName.substring(len-1,len));
                }
                // data
                List<Object> record = new ArrayList<>();
                record.add(chromosomeNumber);
                record.add(markerPositions.get(marker));
                record.add(-Math.log10(markerPValues.get(name)));
                data.add(record);
            }
            objectsMap.put("traits", traitsList);
            objectsMap.put("vars", vars);
            objectsMap.put("data", data);
            genomesMap.put(genome, objectsMap);
        }

        // send JSONObject back to gwasDisplayer.jsp
        JSONObject genomesJSON = new JSONObject(genomesMap);
        request.setAttribute("genomes", genomes);
        request.setAttribute("genomesJSON", genomesJSON.toString());
    }
}
