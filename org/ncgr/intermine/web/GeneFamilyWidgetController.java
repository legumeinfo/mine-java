package org.ncgr.intermine.web;

/*
 * Copyright (C) 2020 NCGR
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
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

import org.json.JSONObject;

/**
 * Class that generates CanvasXpress gene family tally data for a list of gene families.
 *
 * @author Sam Hokin
 *
 */
public class GeneFamilyWidgetController extends TilesAction {
    
    protected static final Logger LOG = Logger.getLogger(GeneFamilyWidgetController.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public ActionForward execute(ComponentContext context, ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();
        InterMineBag bag = (InterMineBag) request.getAttribute("bag");

        Model model = im.getModel();

        Profile profile = SessionMethods.getProfile(session);
        PathQueryExecutor executor = im.getPathQueryExecutor(profile);

        // check that we've got a list of gene families
        String bagType = bag.getType();
        if (!bagType.toLowerCase().equals("genefamily")) {
            LOG.error("called on a bag of type:"+bagType+", exiting.");
            return null;
        }

        // query the gene families and load the ones with a size > 0
        PathQuery gfQuery = getGeneFamilyQuery(model, bag);
        ExportResultsIterator gfResult;
        try {
            gfResult = executor.execute(gfQuery);
        } catch (ObjectStoreException e) {
            setErrorMessage(request, "Error retrieving gene families:" + e.toString());
            return null;
        }
        if (!gfResult.hasNext()) {
            return null;
        }
        List<String> gfIdentifiers = new ArrayList<>();
        List<Integer> gfSizes = new ArrayList<>();
        Map<String,String> gfDescriptionMap = new HashMap<>();
        while (gfResult.hasNext()) {
            // grab the fields
            List<ResultElement> gfRow = gfResult.next();
            String primaryIdentifier = (String) gfRow.get(0).getField(); // 0 GeneFamily.primaryIdentifier
            Integer size = (Integer) gfRow.get(1).getField();            // 1 GeneFamily.size
            String description = (String) gfRow.get(2).getField();       // 2 GeneFamily.description
            // add to maps
            if (size > 0) {
                gfIdentifiers.add(primaryIdentifier);
                gfSizes.add(size);
                gfDescriptionMap.put(primaryIdentifier, description);
            }
        }

        // query the corresponding GeneFamilyTally objects, grabbing the organisms along the way
        List<String> taxonIdList = new ArrayList<>();
        List<String> organismNames = new ArrayList<>();
        Map<String,Map<String,Double>> averageCountMapMap = new HashMap<>();    // keyed by GeneFamily.primaryIdentifier
        Map<String,Map<String,Integer>> numAnnotationsMapMap = new HashMap<>(); // keyed by GeneFamily.primaryIdentifier
        Map<String,Map<String,Integer>> totalCountMapMap = new HashMap<>();     // keyed by GeneFamily.primaryIdentifier
        for (String gfId : gfIdentifiers) {
            PathQuery gftQuery = getGeneFamilyTallyQuery(model, gfId);
            ExportResultsIterator gftResult;
            try {
                gftResult = executor.execute(gftQuery);
            } catch (ObjectStoreException e) {
                setErrorMessage(request, "Error retrieving gene family tallies:" + e.toString());
                return null;
            }
            if (!gftResult.hasNext()) {
                return null;
            }
            Map<String,Double> averageCountMap = new HashMap<>();    // keyed by taxonId
            Map<String,Integer> numAnnotationsMap = new HashMap<>(); // keyed by taxonId
            Map<String,Integer> totalCountMap = new HashMap<>();     // keyed by taxonId
            while (gftResult.hasNext()) {
                // grab the fields
                List<ResultElement> gftRow = gftResult.next();
                Double averageCount = (Double) gftRow.get(0).getField();     // 0
                Integer numAnnotations = (Integer) gftRow.get(1).getField(); // 1
                Integer totalCount = (Integer) gftRow.get(2).getField();     // 2
                String taxonId = (String) gftRow.get(3).getField();          // 3
                String genus = (String) gftRow.get(4).getField();            // 4
                String species = (String) gftRow.get(5).getField();          // 5
                averageCountMap.put(taxonId, averageCount);
                numAnnotationsMap.put(taxonId, numAnnotations);
                totalCountMap.put(taxonId, totalCount);
                if (!taxonIdList.contains(taxonId)) {
                    taxonIdList.add(taxonId);
                    organismNames.add(genus + " " + species);
                }
            }
            averageCountMapMap.put(gfId, averageCountMap);
            numAnnotationsMapMap.put(gfId, numAnnotationsMap);
            totalCountMapMap.put(gfId, totalCountMap);
        }

        // build the canvasXpress objects
        
        // canvasXpress "data" = double[organisms][gene families]
        double[][] data = new double[taxonIdList.size()][gfIdentifiers.size()];
        for (int j=0; j<gfIdentifiers.size(); j++) {
            String gfId = gfIdentifiers.get(j);
            Map<String,Double> averageCountMap = averageCountMapMap.get(gfId);
            for (int i=0; i<taxonIdList.size(); i++) {
                String taxonId = taxonIdList.get(i);
                if (averageCountMap.get(taxonId) != null) {
                    data[i][j] = (double) averageCountMap.get(taxonId);
                } else {
                    data[i][j] = 0.0;
                }
            }
        }
        
        // put the main heatmap data into a JSONObject for "y"
        Map<String, Object> yInHeatmapData = new HashMap<>();
        yInHeatmapData.put("smps", gfIdentifiers);
        yInHeatmapData.put("vars", organismNames);
        yInHeatmapData.put("data", data);
        
        // create the map that gets converted to the JSON object
        Map<String, Object> heatmapData = new HashMap<>();
        heatmapData.put("y", yInHeatmapData);
        
        // convert to JSONObject
        String heatmapJSON = new JSONObject(heatmapData).toString();
        
        // add the sample descriptions to the list
        // descriptionsJSON.add(new JSONObject(sampleDescriptions).toString());
    
        // non-JSON objects
        request.setAttribute("gfDescriptionMap", gfDescriptionMap);
        // JSON output
        request.setAttribute("heatmapJSON", heatmapJSON);
        return null;
    }

    /**
     * To encode '(' and ')', which canvasExpress uses as separator in the cluster tree building
     * also ':' that gives problem in the clustering
     * @param symbol
     * @return a fixed symbol
     */
    String fixSymbol(String symbol) {
        symbol = symbol.replace("(", "%28");
        symbol = symbol.replace(")", "%29");
        symbol = symbol.replace(":", "%3A");
        return symbol;
    }

    /**
     * Create a path query to retrieve gene families in the bag, ordered by size, description.
     *
     * @param model the model
     * @param bag   the bag o'gene families
     * @return the path query
     */
    PathQuery getGeneFamilyQuery(Model model, InterMineBag bag) {
        PathQuery query = new PathQuery(model);
        query.addView("GeneFamily.primaryIdentifier");  // 0
        query.addView("GeneFamily.size");               // 1
        query.addView("GeneFamily.description");        // 2
        query.addConstraint(Constraints.in("GeneFamily", bag.getName()));
        query.addOrderBy("GeneFamily.size", OrderDirection.ASC);
        query.addOrderBy("GeneFamily.description", OrderDirection.ASC);
        List<String> verifyList = query.verifyQuery();
        if (!verifyList.isEmpty()) throw new RuntimeException("GeneFamily query invalid: "+verifyList);
        return query;
    }

    /**
     * Create a path query to retrieve the GeneFamilyTally objects for a given gene family.
     *
     * @param model the model
     * @param gfIdentifier the GeneFamily.primaryIdentifier
     * @return the path query
     */
    PathQuery getGeneFamilyTallyQuery(Model model, String gfIdentifier) {
        PathQuery query = new PathQuery(model);
        query.addView("GeneFamilyTally.averageCount");     // 0
        query.addView("GeneFamilyTally.numAnnotations");   // 1
        query.addView("GeneFamilyTally.totalCount");       // 2
        query.addView("GeneFamilyTally.organism.taxonId"); // 3
        query.addView("GeneFamilyTally.organism.genus");   // 4
        query.addView("GeneFamilyTally.organism.species"); // 5
        query.addConstraint(Constraints.eq("GeneFamilyTally.geneFamily.primaryIdentifier", gfIdentifier));
        query.addOrderBy("GeneFamilyTally.organism.taxonId", OrderDirection.ASC);
        List<String> verifyList = query.verifyQuery();
        if (!verifyList.isEmpty()) throw new RuntimeException("GeneFamilyTally query invalid: "+verifyList);
        return query;
    }

    /**
     * Return empty request attributes as the result of an error.
     *
     * @param request the supplied HttpServletRequest object
     */
    void setEmptyRequestAttributes(HttpServletRequest request) {
        request.setAttribute("sources", null);
        request.setAttribute("sourcesJSON", null);
        request.setAttribute("expressionJSON", "{}");
        request.setAttribute("descriptionsJSON", "{}");
    }

    /**
     * Set an error message in the request.
     *
     * @param request the supplied HttpServletRequest object
     * @param errorMessage the error message
     */
    void setErrorMessage(HttpServletRequest request, String errorMessage) {
        setEmptyRequestAttributes(request);
        request.setAttribute("errorMessage", errorMessage);
    }

    /**
     * Set an error message in the request as the XML version of a failed PathQuery.
     *
     * @param request the supplied HttpServletRequest object
     * @param query the PathQuery that failed
     */
    void setErrorMessage(HttpServletRequest request, PathQuery query) {
        setErrorMessage(request, StringEscapeUtils.escapeHtml4(query.toXml()));
    }
}
