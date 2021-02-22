package org.ncgr.intermine.bio.web.displayer;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;

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
 * Pass attributes and samples on to genotypeMatrixDisplayer. This is really just a stub; the marker data is acquired via Ajax calls to genotypeMatrixJSON.jsp.
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
     * Query the available samples and pass them on.
     * ReportObject = GenotypingStudy
     */
    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        // GenotypingStudy attributes
        String studyPrimaryIdentifier = (String) reportObject.getAttributes().get("primaryIdentifier"); 
        String studyDescription = (String) reportObject.getAttributes().get("description");

        // initialize
        HttpSession session = request.getSession();
        Profile user = SessionMethods.getProfile(session);
        PathQueryExecutor executor = im.getPathQueryExecutor(user);
        Model model = im.getModel();

        // query sample identifiers
        PathQuery sampleQuery = new PathQuery(model);
        sampleQuery.addView("GenotypingSample.primaryIdentifier");
        sampleQuery.addOrderBy("GenotypingSample.primaryIdentifier", OrderDirection.ASC);
        sampleQuery.addConstraint(Constraints.eq("GenotypingSample.study.primaryIdentifier", studyPrimaryIdentifier));
        List<String> samples = new LinkedList<>();
        try {
            ExportResultsIterator iterator = executor.execute(sampleQuery);
            while (iterator.hasNext()) {
                List<ResultElement> results = iterator.next();
                String sample = (String) results.get(0).getField();
                samples.add(sample);
            }
        } catch (ObjectStoreException e) {
            // do nothing
        }

        // return attributes
        request.setAttribute("studyPrimaryIdentifier", studyPrimaryIdentifier);
        request.setAttribute("studyDescription", studyDescription);
        request.setAttribute("samples", samples);
    }
}
