package org.ncgr.intermine.bio.web.displayer;

import org.ncgr.intermine.web.ExpressionValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;

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

import org.json.JSONObject;

/**
 * Display a single-row heatmap of expression for a single gene.
 *
 * @author Sam Hokin
 */
public class GeneHeatmapDisplayer extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GeneHeatmapDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        // get the model and path query executor
        Model model = im.getModel();
        PathQueryExecutor executor = im.getPathQueryExecutor();

        // get the gene attributes (that we care about)
        InterMineObject gene = reportObject.getObject();
        String geneID;
        try {
            geneID = (String) gene.getFieldValue("primaryIdentifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting primaryIdentifier or length.", ex);
        }

        // query the conditions, put them in a list
        List<String> conditions = new LinkedList<String>();
        PathQuery conditionsQuery = queryConditions(model);
        ExportResultsIterator conditionsResult;
        try {
            conditionsResult = executor.execute(conditionsQuery);
        } catch (ObjectStoreException e) {
            throw new RuntimeException("Error retrieving conditions.", e);
        }
        while (conditionsResult.hasNext()) {
            List<ResultElement> row = conditionsResult.next();
            if (row==null || row.get(0)==null || row.get(0).getField()==null) {
                throw new RuntimeException("Null row or row element retrieving conditions.");
            } else {
                conditions.add((String) row.get(0).getField());
            }
        }
        // // if no conditions return an empty JSON string and bail
        // if (conditions.size()==0) {
        //     request.setAttribute("expressionValueJSON", "{}");
        //     LOG.error("No expression conditions retrieved.");
        //     return null;
        // }


        // query the expression values for this gene, put them in a list
        PathQuery valuesQuery = queryExpressionValuesForGene(model, geneID);
        List<ExpressionValue> expressionValues = new LinkedList<ExpressionValue>();
        ExportResultsIterator valuesResult;
        try {
            valuesResult = executor.execute(valuesQuery);
        } catch (ObjectStoreException e) {
            throw new RuntimeException("Error retrieving expression values.", e);
        }
        while (valuesResult.hasNext()) {
            // 0 "Gene.expressionValues.sample.num",
            // 1 "Gene.expressionValues.sample.primaryIdentifier",
            // 2 "Gene.expressionValues.value"
            List<ResultElement> row = valuesResult.next();
            Integer num = (Integer) row.get(0).getField();
            String condition = (String) row.get(1).getField();
            Double value = (Double) row.get(2).getField();
            ExpressionValue eval = new ExpressionValue(condition, num, value, geneID);
            expressionValues.add(eval);
        }
        // // if no expression values return an empty JSON string
        // if (expressionValueMap.size()==0) {
        //     request.setAttribute("expressionValueJSON", "{}");
        //     LOG.error("No expression values retrieved.");
        //     return null;
        // }

        // canvasXpress "smps" = genes (just one)
        List<String> smps =  new LinkedList<String>();
        smps.add(geneID);
        double[][] data = new double[1][conditions.size()];
        for (int j=0; j<conditions.size(); j++) {
            if (expressionValues.get(j)!=null) {
                data[0][j] = (double) expressionValues.get(j).getValue();
            } else {
                data[0][j] = 0.0;
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
        request.setAttribute("ConditionCount", conditions.size());

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
     * Create a path query to retrieve gene expression values for a gene.
     *
     * @param model the model
     * @param geneID the gene primaryIdentifier
     * @return the path query
     */
    private PathQuery queryExpressionValuesForGene(Model model, String geneID) {
        PathQuery query = new PathQuery(model);
        // Add views
        query.addViews(
                       "Gene.expressionValues.sample.num",
                       "Gene.expressionValues.sample.primaryIdentifier",
                       "Gene.expressionValues.value"
                       );
        // Add orderby
        query.addOrderBy("Gene.expressionValues.sample.num", OrderDirection.ASC);
        // Add constraints and you can edit the constraint values below
        query.addConstraint(Constraints.eq("Gene.primaryIdentifier", geneID));
        query.addConstraint(Constraints.isNotNull("Gene.expressionValues.value"));
        return query;
    }

}
