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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Class that generates heatMap data for a list of genes.
 * It is assumed that only one expression series is in the database - all ExpressionSample records are queried for the list of conditions.
 * Based on the modmine HeatMapController written by Sergio and Fengyuan Hu, but heavily modified.
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
        
        // query the conditions, put them in a list
        List<String> conditions = new ArrayList<String>();
        PathQuery conditionsQuery = queryConditions(model);
        ExportResultsIterator conditionsResult;
        try {
            conditionsResult = executor.execute(conditionsQuery);
        } catch (ObjectStoreException e) {
            LOG.error("Error retrieving conditions: "+e.getMessage());
            throw new RuntimeException("Error retrieving conditions.", e);
        }
        while (conditionsResult.hasNext()) {
            List<ResultElement> row = conditionsResult.next();
            if (row==null || row.get(0)==null || row.get(0).getField()==null) {
                LOG.error("Null row or row element returned while retrieving conditions.");
                throw new RuntimeException("Null row or row element retrieving conditions.");
            } else {
                conditions.add((String) row.get(0).getField());
            }
        }
        // if no conditions return an empty JSON string and bail
        if (conditions.size()==0) {
            request.setAttribute("expressionValueJSON", "{}");
            LOG.error("No expression conditions retrieved.");
            return null;
        }

        // set up the expression values query
        PathQuery valuesQuery = queryExpressionValuesFromGenes(model, bag);
        LOG.info(valuesQuery.toXml());
            
        // Key: Gene.primaryIdentifier; Value: list of ExpressionValue objs
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
            String primaryIdentifier = (String) row.get(0).getField();
            Integer num = (Integer) row.get(1).getField();
            String condition = (String) row.get(2).getField();
            Double value = (Double) row.get(3).getField();
            ExpressionValue aScore = new ExpressionValue(condition, num, value, primaryIdentifier);
            if (!expressionValueMap.containsKey(primaryIdentifier)) {
                // Create a list with space for n (size of conditions) ExpressionValue
                List<ExpressionValue> expressionValueList = new ArrayList<ExpressionValue>(Collections.nCopies(conditions.size(), new ExpressionValue()));
                expressionValueList.set(conditions.indexOf(condition), aScore);
                expressionValueMap.put(primaryIdentifier, expressionValueList);
            } else {
                expressionValueMap.get(primaryIdentifier).set(conditions.indexOf(condition), aScore);
            }
        }
        // if no expression values return an empty JSON string
        if (expressionValueMap.size()==0) {
            request.setAttribute("expressionValueJSON", "{}");
            LOG.error("No expression values retrieved.");
            return null;
        }

        // canvasXpress "smps" = genes
        List<String> smps =  new ArrayList<String>(expressionValueMap.keySet());
        double[][] data = new double[smps.size()][conditions.size()];
        for (int i=0; i<smps.size(); i++) {
            String sequenceFeature = smps.get(i);
            for (int j=0; j<conditions.size(); j++) {
                if (expressionValueMap.get(sequenceFeature)!=null && expressionValueMap.get(sequenceFeature).get(j)!=null && expressionValueMap.get(sequenceFeature).get(j).getValue()!=null) {
                    data[i][j] = (double) expressionValueMap.get(sequenceFeature).get(j).getValue();
                } else {
                    data[i][j] = 0.0;
                }
            }
        }
            
        // Rotate data
        double[][] rotatedData = new double[conditions.size()][smps.size()];
        int ii = 0;
        for (int i=0; i<conditions.size(); i++) {
            int jj = 0;
            for (int j=0; j<smps.size(); j++) {
                rotatedData[ii][jj] = data[j][i];
                jj++;
            }
            ii++;
        }

        // put the data into the JSONObject
        Map<String, Object> yInHeatmapData =  new LinkedHashMap<String, Object>();
        yInHeatmapData.put("vars", conditions);
        yInHeatmapData.put("smps", smps);
        yInHeatmapData.put("data", rotatedData);

        Map<String, Object> heatmapData = new LinkedHashMap<String, Object>();
        heatmapData.put("y", yInHeatmapData);
            
        // the JSON data
        JSONObject jo = new JSONObject(heatmapData);

        // set the attributes
        request.setAttribute("expressionValueJSON", jo.toString());
        request.setAttribute("FeatureCount", smps.size());
        request.setAttribute("ConditionCount", conditions.size());

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
     * Create a path query to retrieve the conditions = ExpressionSample.primaryIdentifier.
     *
     * @param model the model
     * @return the path query
     */
    private PathQuery queryConditions(Model model) {
        PathQuery query = new PathQuery(model);
        // query ALL samples
        query.addViews("ExpressionSample.primaryIdentifier");
        query.addOrderBy("ExpressionSample.num", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve gene expression values from a bag of genes.
     *
     * @param model the model
     * @param bag   the bag of genes from which to get the mRNA IDs, etc.
     * @return the path query
     */
    private PathQuery queryExpressionValuesFromGenes(Model model, InterMineBag bag) {
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
        query.addConstraint(Constraints.in("Gene", bag.getName()));
        query.addConstraint(Constraints.isNotNull("Gene.expressionValues.value"));
        return query;
    }

}
