package org.ncgr.intermine.bio.web.displayer;

import java.io.BufferedReader;
import java.io.FileReader;

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
 * Generates data for the phylotree displayer by grabbing the Newick file from the LIS datastore.
 *
 * @author Sam Hokin
 */
public class PhylotreeDisplayer extends ReportDisplayer {

    final static String NEWICKDIR_PROPERTY = "genefamily.newick.location";

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
        // get the GeneFamily object
        InterMineObject obj = reportObject.getObject();
        String objIdentifier;
        try {
            objIdentifier = (String) obj.getFieldValue("identifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting identifier from reportObject.", ex);
        }

        // get the location of Newick files
        final Properties webProperties = SessionMethods.getWebProperties(request.getServletContext());
        String newickDir = (String) webProperties.get(NEWICKDIR_PROPERTY);
        if (newickDir==null) {
            throw new RuntimeException("web.properties is missing location of Newick files:"+NEWICKDIR_PROPERTY);
        }
        String filename = newickDir+"/"+objIdentifier;
        
        try {
            String newick = "";
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            String line = null;
            while((line=reader.readLine())!=null) {
                newick += line;
            }
            request.setAttribute("newick", newick);
        } catch (Exception ex) {
            System.err.println(ex);
            System.exit(1);
        }
    }
}
