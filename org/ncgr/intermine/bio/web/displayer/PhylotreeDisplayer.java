package org.ncgr.intermine.bio.web.displayer;

import java.io.BufferedReader;
import java.io.FileReader;

import java.util.List;
import java.util.Properties;

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
import org.intermine.web.logic.session.SessionMethods;

/**
 * Generates data for the phylotree displayer by grabbing the Newick data and sending it on.
 *
 * @author Sam Hokin
 */
public class PhylotreeDisplayer extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public PhylotreeDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {
        // get the GeneFamily or Phylotree object
        InterMineObject obj = reportObject.getObject();
        String objIdentifier;
        try {
            objIdentifier = (String) obj.getFieldValue("identifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting identifier from reportObject.", ex);
        }

        // query the Newick string with the same identifier
        PathQueryExecutor executor = im.getPathQueryExecutor();
        PathQuery query = new PathQuery(im.getModel());
        query.addViews("Newick.contents");                     // 0
        query.addConstraint(Constraints.eq("Newick.identifier", objIdentifier));
        ExportResultsIterator results;
        try {
            results = executor.execute(query);
            String contents = null;
            if (results.hasNext()) {
                List<ResultElement> row = results.next();
                contents = (String) row.get(0).getField();     // Newick.contents
                // add to the request
                request.setAttribute("newick", contents);
            }
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
    }
}
