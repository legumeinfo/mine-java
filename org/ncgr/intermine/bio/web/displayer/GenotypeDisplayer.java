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
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;


import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

import org.apache.log4j.Logger;

import org.json.JSONObject;

/**
 * Generate the genotyping markers used by the genotype displayer. The rest of the data is acquired via Ajax calls to genotypeJSON.jsp.
 *
 * @author Sam Hokin
 */
public class GenotypeDisplayer extends ReportDisplayer {

    protected static final Logger LOG = Logger.getLogger(GenotypeDisplayer.class);

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GenotypeDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    /**
     * Query the markers associated with this mapping population and return them in a list.
     */
    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        int mpID = reportObject.getId();
        String mappingPopulation = (String) reportObject.getFieldValue("primaryIdentifier");

        PathQueryExecutor executor = im.getPathQueryExecutor();
        
        // query the linkage groups for navigation
        // NOTE: this breaks if we have more than one genetic map!!
        PathQuery lgQuery = new PathQuery(im.getModel());
        lgQuery.addViews("LinkageGroup.number");
        lgQuery.addOrderBy("LinkageGroup.number", OrderDirection.ASC);
        ExportResultsIterator lgResult = getResults(executor, lgQuery);
        List<Integer> linkageGroups = new ArrayList<Integer>();
        while (lgResult.hasNext()) {
            List<ResultElement> row = lgResult.next();
            Integer lg = (Integer) row.get(0).getField();
            linkageGroups.add(lg);
        }

        // query the QTLs for this mapping population
        PathQuery qtlQuery = new PathQuery(im.getModel());
        qtlQuery.addViews("GeneticMarker.QTLs.primaryIdentifier");
        qtlQuery.addConstraint(Constraints.eq("GeneticMarker.mappingPopulations.primaryIdentifier", mappingPopulation));
        qtlQuery.addOrderBy("GeneticMarker.QTLs.primaryIdentifier", OrderDirection.ASC);
        ExportResultsIterator qtlResult = getResults(executor, qtlQuery);
        List<String> qtls = new ArrayList<String>();
        while (qtlResult.hasNext()) {
            List<ResultElement> row = qtlResult.next();
            String qtl = (String) row.get(0).getField();
            qtls.add(qtl);
        }

        // query the number of markers on each linkage group
        Map<Integer,Integer> linkageGroupCounts = new LinkedHashMap<Integer,Integer>();
        for (Integer lg : linkageGroups) {
            PathQuery countQuery = new PathQuery(im.getModel());
            countQuery.addViews(
                                "GeneticMarker.id",
                                "GeneticMarker.linkageGroupPositions.linkageGroup.number"
                                );
            countQuery.addConstraint(Constraints.eq("GeneticMarker.mappingPopulations.primaryIdentifier", mappingPopulation));
            countQuery.addConstraint(Constraints.eq("GeneticMarker.linkageGroupPositions.linkageGroup.number", String.valueOf(lg)));
            try {
                int count = executor.count(countQuery);
                linkageGroupCounts.put(lg, new Integer(count));
            } catch (Exception ex) {
                System.err.println(ex.toString());
            }
        }

        // output data to calling JSP
        request.setAttribute("mappingPopulation", mappingPopulation);
        request.setAttribute("linkageGroups", linkageGroups);
        request.setAttribute("linkageGroupCounts", linkageGroupCounts);
        request.setAttribute("qtls", qtls);

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

    /**
     * Execute a query, returning an ExportResultsIterator over the given range
     *
     * @param executor the PathQueryExecutor
     * @param query the PathQuery
     * @param start the starting index for the results
     * @param limit the maximum number of results
     * @return the ExportResultsIterator
     */
    ExportResultsIterator getResults(PathQueryExecutor executor, PathQuery query, int start, int limit) {
        ExportResultsIterator result;
        try {
            result = executor.execute(query, start, limit);
        } catch (ObjectStoreException e) {
            LOG.error("Error retrieving query results: "+e.getMessage());
            throw new RuntimeException("Error retrieving query results.", e);
        }
        return result;
    }

}
