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
 * Generate the genotyping lines and values for each genotyping study containing the given marker.
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
     * Query the genotypingStudies, lines and genotypes associated with this marker and return them in lists.
     */
    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        PathQueryExecutor executor = im.getPathQueryExecutor();
        int markerId = reportObject.getId();
        
        // objects put into response
        InterMineObject marker = reportObject.getObject();
        Map<String,Map<String,String>> genotypingStudyMap = new LinkedHashMap<String,Map<String,String>>();
        Map<String,String> genotypingStudyDescriptions = new LinkedHashMap<String,String>();
        Map<String,String> genotypingStudyMatrixNotes = new LinkedHashMap<String,String>();
        
        // query the genotypingStudies that contain this marker
        PathQuery gsQuery = new PathQuery(im.getModel());
        gsQuery.addViews("GeneticMarker.genotypingStudies.primaryIdentifier");
        gsQuery.addViews("GeneticMarker.genotypingStudies.description");
        gsQuery.addViews("GeneticMarker.genotypingStudies.matrixNotes");
        gsQuery.addConstraint(Constraints.eq("GeneticMarker.id", String.valueOf(markerId)));
        gsQuery.addOrderBy("GeneticMarker.genotypingStudies.primaryIdentifier", OrderDirection.ASC);
        ExportResultsIterator gsResult = getResults(executor, gsQuery);
        List<String> genotypingStudies = new ArrayList<String>();
        while (gsResult.hasNext()) {
            List<ResultElement> row = gsResult.next();
            String primaryIdentifier = (String) row.get(0).getField();
            String description = (String) row.get(1).getField();
            String matrixNotes = (String) row.get(2).getField();
            genotypingStudies.add(primaryIdentifier);
            genotypingStudyDescriptions.put(primaryIdentifier, description);
            genotypingStudyMatrixNotes.put(primaryIdentifier, matrixNotes);
        }
        
        // query lines and values for each genotypingStudy, load results in a big map of maps
        for (String genotypingStudy : genotypingStudies) {
            PathQuery gvQuery = new PathQuery(im.getModel());
            gvQuery.addViews(
                             "GenotypeValue.value",
                             "GenotypeValue.line.primaryIdentifier"
                             );
            gvQuery.addConstraint(Constraints.eq("GenotypeValue.marker.id", String.valueOf(markerId)));
            gvQuery.addConstraint(Constraints.eq("GenotypeValue.marker.genotypingStudies.primaryIdentifier", genotypingStudy));
            gvQuery.addOrderBy("GenotypeValue.line.primaryIdentifier", OrderDirection.ASC);
            ExportResultsIterator gvResult = getResults(executor, gvQuery);
            Map<String,String> valuesMap = new LinkedHashMap<String,String>();
            while (gvResult.hasNext()) {
                List<ResultElement> row = gvResult.next();
                String value = (String) row.get(0).getField();
                String line = (String) row.get(1).getField();
                valuesMap.put(line, value);
            }
            genotypingStudyMap.put(genotypingStudy, valuesMap);
        }
        
        // output results to HTTP request
        request.setAttribute("marker", marker);
        request.setAttribute("genotypingStudyMap", genotypingStudyMap);
        request.setAttribute("genotypingStudyDescriptions", genotypingStudyDescriptions);
        request.setAttribute("genotypingStudyMatrixNotes", genotypingStudyMatrixNotes);
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
