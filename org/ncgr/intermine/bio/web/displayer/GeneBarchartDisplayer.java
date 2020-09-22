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
 * Display expression bar charts for a single gene.
 *
 * @author Sam Hokin
 */
public class GeneBarchartDisplayer extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GeneBarchartDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
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
        PathQuery sourcesQuery = querySources(model, geneID);
        ExportResultsIterator sourcesResult;
        try {
            sourcesResult = executor.execute(sourcesQuery);
        } catch (ObjectStoreException e) {
            // likely we don't have expression for this particular gene, so return empty values
            request.setAttribute("sources", "");
            request.setAttribute("sourcesJSON", "");
            request.setAttribute("jsonList", "");
            request.setAttribute("descriptionsList", "");
            return;
        }
        while (sourcesResult.hasNext()) {
            List<ResultElement> row = sourcesResult.next();
            if (row==null || row.get(0)==null || row.get(0).getField()==null) {
                throw new RuntimeException("Null row or row element retrieving samples.");
            } else {
                Integer id = (Integer)row.get(0).getField();       // 0 ExpressionValue.sample.source.id
                String identifier = (String)row.get(1).getField(); // 1 ExpressionValue.sample.source.identifier
                // load out stuff
                Map<String,Object> jsonMap = new LinkedHashMap<String,Object>();
                jsonMap.put("id", id);
                jsonMap.put("identifier", identifier);
                sources.add(identifier);
                sourcesJSON.add(new JSONObject(jsonMap).toString());
            }
        }

        // now loop over the sources to get samples and expression
        // we'll store the JSON blocks in a string list
        List<String> jsonList = new LinkedList<String>();
        // and the sample descriptions in another list
        List<String> descriptionsList = new LinkedList<String>();

        for (String source : sources) {

            // query the samples for this source, put them in a list
            List<String> samples = new LinkedList<String>();
            Map<String,String> sampleDescriptions = new LinkedHashMap<String,String>();
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
                    String sample = (String) row.get(0).getField();      // 0 ExpressionSample.primaryIdentifier
                    String description = (String) row.get(1).getField(); // 1 ExpressionSample.description
                    samples.add(sample);
                    sampleDescriptions.put(sample, description);
                }
            }

            // if no samples return an empty JSON string and bail
            if (samples.size()==0) {
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
                String sample = (String) row.get(1).getField();       // 1 Gene.expressionValues.sample.primaryIdentifier
                Double value = (Double) row.get(2).getField();        // 2 Gene.expressionValues.value
                ExprValue eval = new ExprValue(sample, num, value, geneID);
                exprValues.add(eval);
            }

            // if no expression values for this gene return an empty JSON string and bail
            if (exprValues.size()==0) {
                request.setAttribute("expressionValueJSON", "{}");
                return;
            }
            
            // canvasXpress "vars" = gene (just one)
            List<String> vars = new LinkedList<String>();
            vars.add(geneID);
            
            // canvasXpress "data"
            double[][] data = new double[1][samples.size()];
            for (int j=0; j<samples.size(); j++) {
                if (exprValues.get(j)!=null) {
                    data[0][j] = (double) exprValues.get(j).value;
                } else {
                    data[0][j] = 0.0;
                }
            }
            
            // put the canvasXpress data into the JSONObject
            Map<String, Object> yInBarchartData =  new LinkedHashMap<String, Object>();
            yInBarchartData.put("vars", vars);
            yInBarchartData.put("smps", samples);
            yInBarchartData.put("data", data);
            
            Map<String, Object> barchartData = new LinkedHashMap<String, Object>();
            barchartData.put("y", yInBarchartData);
            
            // the JSON data
            JSONObject jo = new JSONObject(barchartData);

            // add this JSON to the list of JSONs
            jsonList.add(jo.toString());

            // add the the sample descriptions to the list
            JSONObject descriptionsJSON = new JSONObject(sampleDescriptions);
            descriptionsList.add(descriptionsJSON.toString());

        }

        // set the return attributes
        request.setAttribute("sources", sources);
        request.setAttribute("sourcesJSON", sourcesJSON);
        request.setAttribute("jsonList", jsonList);
        request.setAttribute("descriptionsList", descriptionsList);
    }

    /**
     * Create a path query to retrieve expression sources associated with the given gene, alphabetically by ExpressionSource.identifier.
     *
     * @param model the model
     * @param geneID the gene for which sources are queried
     * @return the path query
     */
    private PathQuery querySources(Model model, String geneID) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionValue.sample.source.id");                // 0
        query.addView("ExpressionValue.sample.source.identifier");        // 1
        query.addConstraint(Constraints.eq("ExpressionValue.feature.primaryIdentifier", geneID));
        query.addOrderBy("ExpressionValue.sample.source.identifier", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve the samples = ExpressionSample.primaryIdentifier.
     *
     * @param model the model
     * @param source the primaryIdentifier of the expression source
     * @return the path query
     */
    private PathQuery querySamples(Model model, String source) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionSample.primaryIdentifier"); // 0
        query.addView("ExpressionSample.description");       // 1
        query.addConstraint(Constraints.eq("ExpressionSample.source.identifier", source));
        query.addOrderBy("ExpressionSample.num", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve expression values from the given source for the given gene 
     *
     * @param model the model
     * @param source the primaryIdentifier of the expression source
     * @param geneID the gene primaryIdentifier
     * @return the path query
     */
    private PathQuery queryExpressionValuesForGene(Model model, String source, String geneID) {
        PathQuery query = new PathQuery(model);
        // Add views
        query.addViews(
                       "ExpressionValue.sample.num",                  // 0
                       "ExpressionValue.sample.primaryIdentifier",    // 1
                       "ExpressionValue.value"                        // 2
                       );
        // Add orderby
        query.addOrderBy("ExpressionValue.sample.num", OrderDirection.ASC);
        // Add constraints and you can edit the constraint values below
        query.addConstraint(Constraints.eq("ExpressionValue.sample.source.identifier", source));
        query.addConstraint(Constraints.eq("ExpressionValue.feature.primaryIdentifier", geneID));
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
