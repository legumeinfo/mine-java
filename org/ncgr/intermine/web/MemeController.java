package org.ncgr.intermine.web;

/*
 * Copyright (C) 2002-2014 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;
import org.apache.struts.tiles.actions.TilesAction;

import org.intermine.api.InterMineAPI;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.logic.session.SessionMethods;

import org.ncgr.opal.MemeClient;

import org.globus.gram.GramJob;

import edu.sdsc.nbcr.opal.AppServiceLocator;
import edu.sdsc.nbcr.opal.AppServicePortType;
import edu.sdsc.nbcr.opal.types.AppMetadataType;
import edu.sdsc.nbcr.opal.types.AppMetadataInputType;
import edu.sdsc.nbcr.opal.types.InputFileType;
import edu.sdsc.nbcr.opal.types.OutputFileType;
import edu.sdsc.nbcr.opal.types.JobInputType;
import edu.sdsc.nbcr.opal.types.JobOutputType;
import edu.sdsc.nbcr.opal.types.JobStatisticsType;
import edu.sdsc.nbcr.opal.types.JobSubOutputType;
import edu.sdsc.nbcr.opal.types.StatusOutputType;
import edu.sdsc.nbcr.opal.types.SystemInfoType;
import edu.sdsc.nbcr.opal.types.SystemInfoInputType;

/**
 * Run a MEME analysis on a bag of sequences, typically upstream intergenic regions.
 *
 * In Struts2, this should implement Parameterizable.
 *
 * @author Sam Hokin
 */
public class MemeController extends TilesAction {
    
    protected static final Logger LOG = Logger.getLogger(MemeController.class);

    private Map<String,String> paramMap;            // set by calling action in Struts2 XML, but we're still on Struts1

    // these should be all be set in paramMap, if/when we switch to Struts2
    public static final String SERVICE_URL = "http://intermine.ncgr.org/opal2/services/meme";
    public static final int MAX_SIZE = 100000;      // max size of fasta file
    public static final int MAX_WAIT_SECONDS = 600; // max time to wait for Meme job to finish
    public static final int WAIT_SECONDS = 5;       // number of seconds to wait between each wait period

    /**
     * {@inheritDoc}
     */
    @Override
    public ActionForward execute(ComponentContext context, ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        // standard stuff that's in any controller
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();
        InterMineBag bag = (InterMineBag) request.getAttribute("bag");
        Model model = im.getModel();
        Profile profile = SessionMethods.getProfile(session);
        PathQueryExecutor executor = im.getPathQueryExecutor(profile);

        // check that we've got a list of sequence features and abort if not
        // list all desired sequence features here!
        String expressionType = bag.getType();
        if (!expressionType.equals("GeneFlankingRegion")) {
            request.setAttribute("errorMessage", "---"); // flag to not show MEME analysis at all
            return null;
        }

        // return the feature count
        int featureCount = 0;
        try {
            featureCount = bag.getSize();
            request.setAttribute("featureCount", featureCount);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error getting bag size.", ex);
        }
        
        // build the FASTA data 
        PathQuery bagQuery = queryBag(model, bag);
        ExportResultsIterator bagResult;
        try {
            bagResult = executor.execute(bagQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        String fasta = "";
        int dataSize = 0;
        while (bagResult.hasNext()) {
            List<ResultElement> row = bagResult.next();
            String primaryIdentifier = (String) row.get(0).getField();
            Integer length = (Integer) row.get(1).getField();
            dataSize += length.intValue();
            String residues = (row.get(2).getField()).toString();
            fasta += ">"+primaryIdentifier+"\n"+residues+"\n";
        }

        // check that we've got a small enough data set
        if (dataSize>MAX_SIZE) {
            request.setAttribute("errorMessage", "The dataset is too large ("+dataSize+") for MEME analysis. Maximum size is "+MAX_SIZE+".");
            return null;
        }

        // use static vars for now since we're on Struts 1 and can't get them (easily) from the struts XML
        String serviceURL = SERVICE_URL;
        int maxSize = MAX_SIZE;
        int maxWaitSeconds = MAX_WAIT_SECONDS; 
        int waitSeconds = WAIT_SECONDS;

        // submit the FASTA data to the MEME web service
        try {

            MemeClient mc = new MemeClient(serviceURL);
            JobSubOutputType jobOutput = mc.launchJob(fasta.getBytes());
            StatusOutputType status = jobOutput.getStatus();
            String jobID = jobOutput.getJobID();

            request.setAttribute("jobID", jobID);
            request.setAttribute("outputBaseURL", status.getBaseURL());
            request.setAttribute("launchStatusCode", status.getCode());
            request.setAttribute("launchStatusMessage", status.getMessage());

            // loop until finished or we're tired of waiting
            boolean finished = false;
            int maxWaits = maxWaitSeconds/waitSeconds;
            int waits = 0;
            while (!finished && waits<maxWaits) {
                TimeUnit.SECONDS.sleep(waitSeconds);
                status = mc.queryStatus(jobID);
                int code = status.getCode();
                finished = (code==GramJob.STATUS_DONE || code==GramJob.STATUS_FAILED || code==GramJob.STATUS_SUSPENDED || code==GramJob.STATUS_UNSUBMITTED);
            }

            // final output
            if (status.getCode()==GramJob.STATUS_DONE) {
                JobStatisticsType stats = mc.getStatistics(jobID);
                request.setAttribute("submissionTime", stats.getStartTime().getTime());
                if (stats.getActivationTime()!=null) request.setAttribute("activationTime", stats.getActivationTime().getTime());
                if (stats.getCompletionTime()!=null) request.setAttribute("completionTime", stats.getCompletionTime().getTime());
                request.setAttribute("finalStatusCode", status.getCode());
                request.setAttribute("finalStatusMessage", status.getMessage());
            } else {
                request.setAttribute("errorMessage", status.getMessage());
            }
                
        } catch (Exception ex) {
            request.setAttribute("errorMessage", ex.toString());
        }

        return null;
    }


    /**
     * Create a path query to retrieve identifiers, lengths and sequences from a bag
     *
     * @param model the model
     * @param bag   the bag of query linkage groups
     * @return the path query
     */
    private PathQuery queryBag(Model model, InterMineBag bag) {
        PathQuery query = new PathQuery(model);
        query.addViews("SequenceFeature.primaryIdentifier",
                       "SequenceFeature.length",
                       "SequenceFeature.sequence.residues"
                       );
        query.addConstraint(Constraints.in("SequenceFeature", bag.getName()));
        query.addOrderBy("SequenceFeature.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

    /**
     * Set the global params from the calling struts XML.
     * This is only used in Struts2, and IM currently is using Struts 1, so it won't be called.
     */
    public void setParams(Map<String,String> map) {
        this.paramMap = map;
    }

}
