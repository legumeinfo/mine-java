package org.ncgr.intermine.bio.web.displayer;

import java.util.Map;
import java.util.LinkedHashMap;
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
                throw new RuntimeException("Null row or row element retrieving conditions.");
            } else {
                Map<String,Object> jsonMap = new LinkedHashMap<String,Object>();
                jsonMap.put("id", (Integer)row.get(0).getField());
                jsonMap.put("identifier", (String)row.get(1).getField());
                jsonMap.put("description",(String)row.get(2).getField());
                jsonMap.put("unit",(String)row.get(3).getField());
                sources.add((String)row.get(1).getField());
                sourcesJSON.add(new JSONObject(jsonMap).toString());
            }
        }

        // now loop over the sources to get conditions and expression
        // we'll store the JSON blocks in a string list, as well as a list of condition counts
        List<String> jsonList = new LinkedList<String>();
        List<Integer> countsList = new LinkedList<Integer>();

        for (String source : sources) {
            
            // query the conditions for this source, put them in a list
            List<String> conditions = new LinkedList<String>();
            PathQuery conditionsQuery = queryConditions(model, source);
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
            // if no conditions return an empty JSON string and bail
            if (conditions.size()==0) {
                request.setAttribute("expressionValueJSON", "{}");
                return;
            }

            // query the expression values for this source and gene, put them in a list
            PathQuery valuesQuery = queryExpressionValuesForGene(model, source, geneID);
            List<ExprValue> exprValues = new LinkedList<ExprValue>();
            ExportResultsIterator valuesResult;
            try {
                valuesResult = executor.execute(valuesQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving expression values.", e);
            }
            while (valuesResult.hasNext()) {
                List<ResultElement> row = valuesResult.next();
                Integer num = (Integer) row.get(0).getField();        // 0 Gene.expressionValues.sample.num
                String condition = (String) row.get(1).getField();    // 1 Gene.expressionValues.sample.primaryIdentifier
                Double value = (Double) row.get(2).getField();        // 2 Gene.expressionValues.value
                ExprValue eval = new ExprValue(condition, num, value, geneID);
                exprValues.add(eval);
            }

            // canvasXpress "vars" = conditions
            // canvasXpress "smps" = genes (just one)
            List<String> smps =  new LinkedList<String>();
            smps.add(geneID);
            double[][] data = new double[1][conditions.size()];
            for (int j=0; j<conditions.size(); j++) {
                if (exprValues.get(j)!=null) {
                    data[0][j] = (double) exprValues.get(j).value;
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

            // add this JSON to the list of JSONs
            jsonList.add(jo.toString());

            // add the condition count to the list of counts
            countsList.add(conditions.size());
            
        }

        // set the return attributes
        request.setAttribute("sources", sources);
        request.setAttribute("sourcesJSON", sourcesJSON);
        request.setAttribute("jsonList", jsonList);
        request.setAttribute("countsList", countsList);

    }

    /**
     * Create a path query to retrieve expression sources alphabetically by ExpressionSource.primaryIdentifier.
     *
     * @param model the model
     * @return the path query
     */
    private PathQuery querySources(Model model) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionSource.id");          // 0
        query.addView("ExpressionSource.primaryIdentifier");  // 1  
        query.addView("ExpressionSource.description"); // 2
        query.addView("ExpressionSource.unit");        // 3
        query.addOrderBy("ExpressionSource.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve the conditions = ExpressionSample.primaryIdentifier.
     *
     * @param model the model
     * @param source the identifier of the expression source
     * @return the path query
     */
    private PathQuery queryConditions(Model model, String source) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionSample.primaryIdentifier");
        query.addConstraint(Constraints.eq("ExpressionSample.source.identifier", source));
        query.addOrderBy("ExpressionSample.num", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve gene expression values from the given source for the given gene 
     *
     * @param model the model
     * @param source the identifier of the expression source
     * @param geneID the gene primaryIdentifier
     * @return the path query
     */
    private PathQuery queryExpressionValuesForGene(Model model, String source, String geneID) {
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
        query.addConstraint(Constraints.isNotNull("Gene.expressionValues.value"));
        query.addConstraint(Constraints.eq("Gene.expressionValues.sample.source.primaryIdentifier", source));
        query.addConstraint(Constraints.eq("Gene.primaryIdentifier", geneID));
        return query;
    }

    /**
     * Expression value container.
     */
    private class ExprValue {
        String sample;
        int num;
        double value;
        String featureId;
        ExprValue(String sample, int num, double value, String featureId) {
            this.sample = sample;
            this.num = num;
            this.value = value;
            this.featureId = featureId;
        }
    }
}
