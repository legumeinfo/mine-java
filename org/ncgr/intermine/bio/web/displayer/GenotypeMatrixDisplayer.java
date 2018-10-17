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
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;
import org.intermine.web.logic.session.SessionMethods;

import org.apache.log4j.Logger;

import org.json.JSONObject;

/**
 * Pass attributes and phenotypes on to genotypeMatrixDisplayer. This is really just a stub; the marker data is acquired via Ajax calls to genotypeMatrixJSON.jsp.
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
     * Query the available phenotypes and pass them on.
     */
    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        Map<String,Object> attributes = reportObject.getAttributes();
        String genotypingStudy = (String) attributes.get("primaryIdentifier"); 
        String matrixNotes = (String) attributes.get("matrixNotes");

        // initialize
        HttpSession session = request.getSession();
        Profile user = SessionMethods.getProfile(session);
        PathQueryExecutor executor = im.getPathQueryExecutor(user);
        Model model = im.getModel();

        // query phenotype names
        List<String> phenotypes = new LinkedList<>();
        PathQuery phenotypeQuery = new PathQuery(model);
        phenotypeQuery.addView("Phenotype.primaryIdentifier");
        phenotypeQuery.addOrderBy("Phenotype.primaryIdentifier", OrderDirection.ASC);
        try {
            ExportResultsIterator iterator = executor.execute(phenotypeQuery);
            while (iterator.hasNext()) {
                List<ResultElement> results = iterator.next();
                String phenotype = (String) results.get(0).getField();
                phenotypes.add(phenotype);
            }
        } catch (ObjectStoreException e) {
            // do nothing
        }

        // return attributes
        request.setAttribute("genotypingStudy", genotypingStudy);
        request.setAttribute("matrixNotes", matrixNotes);
        request.setAttribute("phenotypes", phenotypes);
    }

}
