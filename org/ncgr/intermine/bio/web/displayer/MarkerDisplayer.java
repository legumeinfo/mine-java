package org.ncgr.intermine.bio.web.displayer;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

import javax.servlet.http.HttpServletRequest;

import org.intermine.api.InterMineAPI;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;

import org.intermine.model.InterMineObject;

import org.intermine.objectstore.ObjectStoreException;

import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;

import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

import org.apache.log4j.Logger;

import org.json.JSONObject;

/**
 * Generate the genotyping lines and values for each mappingPopulation containing the given marker.
 *
 * @author Sam Hokin
 */
public class MarkerDisplayer extends ReportDisplayer {

    protected static final Logger LOG = Logger.getLogger(MarkerDisplayer.class);

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public MarkerDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    /**
     * Query the mappingPopulations, lines and genotypes associated with this marker and return them in lists.
     */
    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        PathQueryExecutor executor = im.getPathQueryExecutor();
        int markerID = reportObject.getId();
        
        // query the mappingPopulations that contain this marker
        PathQuery mpQuery = new PathQuery(im.getModel());
        mpQuery.addViews("GeneticMarker.mappingPopulations.primaryIdentifier");
        mpQuery.addConstraint(Constraints.eq("GeneticMarker.id", String.valueOf(markerID)));
        mpQuery.addOrderBy("GeneticMarker.mappingPopulations.primaryIdentifier", OrderDirection.ASC);
        ExportResultsIterator mpResult = getResults(executor, mpQuery);
        List<String> mappingPopulations = new ArrayList<String>();
        while (mpResult.hasNext()) {
            List<ResultElement> row = mpResult.next();
            String primaryIdentifier = (String) row.get(0).getField();
            mappingPopulations.add(primaryIdentifier);
        }
        
        // query lines and values for each mappingPopulation, load results in a big map of maps
        Map<String,Map<String,String>> mappingPopulationMap = new LinkedHashMap<String,Map<String,String>>();
        for (String mappingPopulation : mappingPopulations) {
            PathQuery gtQuery = new PathQuery(im.getModel());
            gtQuery.addViews(
                             "GenotypeValue.line.primaryIdentifier",
                             "GenotypeValue.value"
                             );
            gtQuery.addConstraint(Constraints.eq("GenotypeValue.line.mappingPopulation.primaryIdentifier", mappingPopulation));
            gtQuery.addConstraint(Constraints.eq("GenotypeValue.marker.id", String.valueOf(markerID)));
            gtQuery.addOrderBy("GenotypeValue.line.primaryIdentifier", OrderDirection.ASC);
            ExportResultsIterator gtResult = getResults(executor, gtQuery);
            Map<String,String> valuesMap = new LinkedHashMap<String,String>();
            while (gtResult.hasNext()) {
                List<ResultElement> row = gtResult.next();
                String line = (String) row.get(0).getField();
                String value = (String) row.get(1).getField();
                valuesMap.put(line, value);
            }
            mappingPopulationMap.put(mappingPopulation, valuesMap);
        }
        
        // output results to HTTP request
        request.setAttribute("mappingPopulationMap", mappingPopulationMap);

    }

    /**
     * Execute a query, returning an ExportResultsIterator
     *
     * @param executor the PathQueryExecutor
     * @param query the PathQuery
     * @return the ExportResultsIterator
     */
    ExportResultsIterator getResults(PathQueryExecutor executor, PathQuery query) {
        ExportResultsIterator result;
        try {
            result = executor.execute(query);
        } catch (ObjectStoreException e) {
            LOG.error("Error retrieving query results: "+e.getMessage());
            throw new RuntimeException("Error retrieving query results.", e);
        }
        return result;
    }

}
