package org.ncgr.intermine.bio.web.displayer;

import org.ncgr.intermine.web.ExpressionValue;

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
        Map<String,String> sourcesUnit = new LinkedHashMap<String,String>();
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
                sourcesUnit.put(primaryIdentifier, unit);
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
                    String sample = (String) row.get(0).getField();
                    String description = (String) row.get(1).getField();
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
            List<ExpressionValue> expressionValues = new LinkedList<ExpressionValue>();
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
                ExpressionValue eval = new ExpressionValue(sample, num, value, geneID);
                expressionValues.add(eval);
            }

            // if no expression values for this gene return an empty JSON string and bail
            if (expressionValues.size()==0) {
                request.setAttribute("expressionValueJSON", "{}");
                return;
            }
            
            // canvasXpress "vars" = gene (just one)
            List<String> vars = new LinkedList<String>();
            vars.add(geneID);
            
            // canvasXpress "desc" = unit (just one)
            List<String> desc = new LinkedList<String>();
            desc.add(sourcesUnit.get(source));

            // canvasXpress "data"
            double[][] data = new double[1][samples.size()];
            for (int j=0; j<samples.size(); j++) {
                if (expressionValues.get(j)!=null) {
                    data[0][j] = (double) expressionValues.get(j).getValue();
                } else {
                    data[0][j] = 0.0;
                }
            }
            
            // put the canvasXpress data into the JSONObject
            Map<String, Object> yInBarchartData =  new LinkedHashMap<String, Object>();
            yInBarchartData.put("vars", vars);
            yInBarchartData.put("smps", samples);
            yInBarchartData.put("desc", desc);
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
     * Create a path query to retrieve the samples = ExpressionSample.primaryIdentifier.
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
     * Create a path query to retrieve gene expression values from the given source for the given gene 
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

}
