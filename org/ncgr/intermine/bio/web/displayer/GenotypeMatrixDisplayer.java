package org.ncgr.intermine.bio.web.displayer;

import java.util.List;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.intermine.api.InterMineAPI;
import org.intermine.api.profile.Profile;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;
import org.intermine.web.logic.session.SessionMethods;

import org.apache.log4j.Logger;

import org.json.JSONObject;

/**
 * Provides a minimal amount of starting data for the Javascript displayer.
 *
 * @author Sam Hokin
 */
public class GenotypeMatrixDisplayer extends ReportDisplayer {

    protected static final Logger LOG = Logger.getLogger(GenotypeMatrixDisplayer.class);

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GenotypeMatrixDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    /**
     * Query the available chromosomes for the given GenotypingStudy and pass them on.
     * ReportObject = GenotypingStudy
     */
    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {
        String studyPrimaryIdentifier = (String) reportObject.getAttributes().get("primaryIdentifier"); 

        // query the chromsome identifiers
        List<String> chrSecondaryIdentifiers = new LinkedList<>();
        PathQueryExecutor executor = im.getPathQueryExecutor();
        PathQuery query = new PathQuery(im.getModel());
        query.addViews("VCFRecord.chromosome.secondaryIdentifier"); // 0
        query.addConstraint(Constraints.eq("VCFRecord.genotypingStudy.primaryIdentifier", studyPrimaryIdentifier));
        query.addOrderBy("VCFRecord.chromosome.secondaryIdentifier", OrderDirection.ASC);
        try {
            ExportResultsIterator iterator = executor.execute(query);
            while (iterator.hasNext()) {
                List<ResultElement> results = iterator.next();
                chrSecondaryIdentifiers.add((String) results.get(0).getField());
            }
        } catch (ObjectStoreException ex) {
            System.err.println(ex);
            return;
        }
            
        // output
        request.setAttribute("studyPrimaryIdentifier", studyPrimaryIdentifier);
        request.setAttribute("chrSecondaryIdentifiers", chrSecondaryIdentifiers);
    }
}
