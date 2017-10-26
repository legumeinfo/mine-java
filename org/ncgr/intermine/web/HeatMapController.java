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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
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
 * Class that generates CanvasXpress heat map data for a list of genes.
 *
 * Originally based on the modmine HeatMapController written by Sergio and Fengyuan Hu, but heavily modified.
 *
 * @author Sam Hokin
 *
 */
public class HeatMapController extends TilesAction {
    
    protected static final Logger LOG = Logger.getLogger(HeatMapController.class);

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

        // check that we've got a list of genes
        String expressionType = bag.getType();
        LOG.info("called on a bag of type:"+expressionType);
        if (!expressionType.toLowerCase().equals("gene")) {
            // it ain't genes, bail
            return null;
        }

        // query the sources, since we may have more than one, put them in a list of JSONs
        List<String> sources = new LinkedList<String>();
        List<String> sourcesJSON = new LinkedList<String>();
        PathQuery sourcesQuery = querySources(model);
        ExportResultsIterator sourcesResult;
        try {
            sourcesResult = executor.execute(sourcesQuery);
        } catch (ObjectStoreException e) {
            throw new RuntimeException("Error retrieving sources.", e);
        }
        while (sourcesResult.hasNext()) {
            List<ResultElement> row = sourcesResult.next();
            if (row==null || row.get(0)==null || row.get(0).getField()==null) {
                throw new RuntimeException("Null row or row element retrieving samples.");
            } else {
                // grab the fields
                Integer id = (Integer)row.get(0).getField();
                String primaryIdentifier = (String)row.get(1).getField();
                String description = (String)row.get(2).getField();
                String unit = (String)row.get(3).getField();
                // load out stuff
                Map<String,Object> jsonMap = new LinkedHashMap<String,Object>();
                jsonMap.put("id", id);
                jsonMap.put("primaryIdentifier", primaryIdentifier);
                jsonMap.put("description", description);
                jsonMap.put("unit", unit);
                sources.add(primaryIdentifier);
                sourcesJSON.add(new JSONObject(jsonMap).toString());
            }
        }

        // we'll store the JSON blocks in a string list, as well as a list of sample counts and description maps
        List<String> expressionJSON = new LinkedList<String>();
        List<Integer> geneCounts = new LinkedList<Integer>();
        List<Integer> sampleCounts = new LinkedList<Integer>();

        // this is a list of maps
        List<Map<String,String>> descriptionsList = new LinkedList<Map<String,String>>();

        // now loop over the sources to get samples and expression
        for (String source : sources) {

            // store sample descriptions in a map
            Map<String,String> sampleDescriptions = new LinkedHashMap<String,String>();
            
            // query the samples for this source, put them in a list
            List<String> samples = new LinkedList<String>();
            PathQuery samplesQuery = querySamples(model, source);
            ExportResultsIterator samplesResult;
            try {
                samplesResult = executor.execute(samplesQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving samples.", e);
            }
            while (samplesResult.hasNext()) {
                List<ResultElement> row = samplesResult.next();
                if (row==null || row.get(0)==null || row.get(0).getField()==null) {
                    throw new RuntimeException("Null row or row element retrieving samples.");
                } else {
                    String sample = (String) row.get(0).getField();
                    String description = (String) row.get(1).getField();
                    samples.add(sample);
                    sampleDescriptions.put(sample, description);
                }
            }

            // if no samples return an empty JSON string and bail
            if (samples.size()==0) {
                request.setAttribute("expressionValueJSON", "{}");
                return null;
            }
            
            // set up the expression values query
            PathQuery valuesQuery = queryExpressionValues(model, source, bag);
            LOG.info(valuesQuery.toXml());
            
            // load the expression values for this source into a map, keyed by gene, containing a list of ExpressionValue objects
            Map<String, List<ExpressionValue>> expressionValueMap = new LinkedHashMap<String, List<ExpressionValue>>();
            ExportResultsIterator valuesResult;
            try {
                valuesResult = executor.execute(valuesQuery);
            } catch (ObjectStoreException e) {
                LOG.error("Error retrieving expression values: "+e.getMessage());
                throw new RuntimeException("Error retrieving expression values.", e);
            }
            while (valuesResult.hasNext()) {
                List<ResultElement> row = valuesResult.next();
                String geneID = (String) row.get(0).getField();
                Integer num = (Integer) row.get(1).getField();
                String sample = (String) row.get(2).getField();
                Double value = (Double) row.get(3).getField();
                ExpressionValue expValue = new ExpressionValue(sample, num, value, geneID);
                if (!expressionValueMap.containsKey(geneID)) {
                    // Create a new list with space for n (size of samples) ExpressionValues
                    List<ExpressionValue> expressionValueList = new ArrayList<ExpressionValue>(Collections.nCopies(samples.size(), new ExpressionValue()));
                    expressionValueList.set(samples.indexOf(sample), expValue);
                    expressionValueMap.put(geneID, expressionValueList);
                } else {
                    // Gene already here, update the value of this sample
                    expressionValueMap.get(geneID).set(samples.indexOf(sample), expValue);
                }
            }

            // if no expression values return an empty JSON string
            if (expressionValueMap.size()==0) {
                request.setAttribute("expressionValueJSON", "{}");
                LOG.error("No expression values retrieved.");
                return null;
            }

            // canvasXpress "smps" = genes
            List<String> genes =  new ArrayList<String>(expressionValueMap.keySet());

            // canvasXpress "data" = double[samples][genes]
            double[][] data = new double[samples.size()][genes.size()];
            for (int j=0; j<genes.size(); j++) {
                String gene = genes.get(j);
                for (int i=0; i<samples.size(); i++) {
                    if (expressionValueMap.get(gene)!=null && expressionValueMap.get(gene).get(i)!=null && expressionValueMap.get(gene).get(i).getValue()!=null) {
                        data[i][j] = (double) expressionValueMap.get(gene).get(i).getValue();
                    } else {
                        data[i][j] = 0.0;
                    }
                }
            }
            
            // put the data into a JSONObject
            Map<String, Object> yInHeatmapData =  new LinkedHashMap<String, Object>();
            yInHeatmapData.put("vars", samples);
            yInHeatmapData.put("smps", genes);
            yInHeatmapData.put("data", data);

            // the entire JSON data is called "y" by CanvasXpress
            Map<String, Object> heatmapData = new LinkedHashMap<String, Object>();
            heatmapData.put("y", yInHeatmapData);
            JSONObject jo = new JSONObject(heatmapData);

            // add these results to the results maps
            geneCounts.add(genes.size());
            sampleCounts.add(samples.size());
            expressionJSON.add(jo.toString());

            // add the the sample descriptions to the list
            descriptionsList.add(sampleDescriptions);

        }

        // set the return attributes
        request.setAttribute("sources", sources);
        request.setAttribute("sourcesJSON", sourcesJSON);
        request.setAttribute("geneCounts", geneCounts);
        request.setAttribute("sampleCounts", sampleCounts);
        request.setAttribute("expressionJSON", expressionJSON);
        request.setAttribute("descriptionsList", descriptionsList);

        return null;
    }

    /**
     * To encode '(' and ')', which canvasExpress uses as separator in the cluster tree building
     * also ':' that gives problem in the clustering
     * @param symbol
     * @return a fixed symbol
     */
    private String fixSymbol(String symbol) {
        symbol = symbol.replace("(", "%28");
        symbol = symbol.replace(")", "%29");
        symbol = symbol.replace(":", "%3A");
        return symbol;
    }

    /**
     * Create a path query to retrieve expression sources alphabetically by ExpressionSource.primaryIdentifier.
     *
     * @param model the model
     * @return the path query
     */
    private PathQuery querySources(Model model) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionSource.id");                 // 0
        query.addView("ExpressionSource.primaryIdentifier");  // 1  
        query.addView("ExpressionSource.description");        // 2
        query.addView("ExpressionSource.unit");               // 3
        query.addOrderBy("ExpressionSource.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve the sample primaryIdentifiers and descriptions for a given source.
     *
     * @param model the model
     * @param source the primaryIdentifier of the expression source
     * @return the path query
     */
    private PathQuery querySamples(Model model, String source) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionSample.primaryIdentifier");
        query.addView("ExpressionSample.description");
        query.addConstraint(Constraints.eq("ExpressionSample.source.primaryIdentifier", source));
        query.addOrderBy("ExpressionSample.num", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve gene expression values from a bag of genes for the given expression source.
     *
     * @param model  the model
     * @param source the primaryIdentifier of the ExpressionSource
     * @param bag    the bag o'genes
     * @return the path query
     */
    private PathQuery queryExpressionValues(Model model, String source, InterMineBag bag) {
        PathQuery query = new PathQuery(model);
        // Add views
        query.addViews(
                       "Gene.primaryIdentifier",
                       "Gene.expressionValues.sample.num",
                       "Gene.expressionValues.sample.primaryIdentifier",
                       "Gene.expressionValues.value"
                       );
        // Add orderby
        query.addOrderBy("Gene.primaryIdentifier", OrderDirection.ASC);
        query.addOrderBy("Gene.expressionValues.sample.num", OrderDirection.ASC);
        // Add constraints and you can edit the constraint values below
        query.addConstraint(Constraints.eq("Gene.expressionValues.sample.source.primaryIdentifier", source));
        query.addConstraint(Constraints.in("Gene", bag.getName()));
        query.addConstraint(Constraints.isNotNull("Gene.expressionValues.value"));
        return query;
    }

}
