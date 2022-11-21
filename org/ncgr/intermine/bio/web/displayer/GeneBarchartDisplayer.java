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
        List<String> sources = new LinkedList<>();
        List<String> sourcesJSON = new LinkedList<>();
        PathQuery sourcesQuery = getSourcesQuery(model, geneID);
        ExportResultsIterator sourcesResult;
        try {
            sourcesResult = executor.execute(sourcesQuery);
        } catch (ObjectStoreException e) {
            System.err.println(e);
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
                throw new RuntimeException("Null row or row element retrieving sources.");
            } else {
                Integer id = (Integer) row.get(0).getField();       // 0 ExpressionValue.sample.source.id
                String identifier = (String) row.get(1).getField(); // 1 ExpressionValue.sample.source.primaryIdentifier
                String synopsis = (String) row.get(2).getField();   // 2 ExpressionValue.sample.source.synopsis
                // load our stuff
                Map<String,Object> jsonMap = new LinkedHashMap<>();
                jsonMap.put("id", id);
                jsonMap.put("identifier", identifier);
                jsonMap.put("synopsis", synopsis);
                sources.add(identifier);
                sourcesJSON.add(new JSONObject(jsonMap).toString());
            }
        }

        // we'll store the JSON blocks in a string list
        List<String> jsonList = new LinkedList<>();
        // and the various sample descriptive stuff in their own descriptions in their own lists
        List<String> descriptionsList = new LinkedList<>();
        List<String> tissuesList = new LinkedList<>();
        List<String> treatmentsList = new LinkedList<>();
        List<String> genotypesList = new LinkedList<>();
        List<String> unitsList = new LinkedList<String>();
        // now loop over the sources to get unit, samples, and expression
        for (String source : sources) {
            // query the expression unit for this source
            PathQuery unitQuery = getExpressionUnitQuery(model, source);
            ExportResultsIterator unitResult;
            try {
                unitResult = executor.execute(unitQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving expression unit: ", e);
            }
            List<ResultElement> unitRow = unitResult.next();
            if (unitRow==null || unitRow.get(0)==null || unitRow.get(0).getField()==null) {
                throw new RuntimeException("Null row or row element retrieving expression unit.");
            } else {
                String unit = (String) unitRow.get(0).getField();
                unitsList.add(unit);
            }
            // query the samples for this source, put them in a list
            List<String> samples = new LinkedList<>();
            Map<String,String> sampleDescriptions = new LinkedHashMap<>();
            Map<String,String> sampleTissues = new LinkedHashMap<>();
            Map<String,String> sampleTreatments = new LinkedHashMap<>();
            Map<String,String> sampleGenotypes = new LinkedHashMap<>();
            List<String> sampleRepgroups = new LinkedList<>();
            PathQuery samplesQuery = getSamplesQuery(model, source);
            ExportResultsIterator samplesResult;
            try {
                samplesResult = executor.execute(samplesQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving samples: ", e);
            }
            while (samplesResult.hasNext()) {
                List<ResultElement> row = samplesResult.next();
                if (row==null || row.get(0)==null || row.get(0).getField()==null) {
                    throw new RuntimeException("Null row or row element retrieving samples.");
                } else {
                    String sample = (String) row.get(0).getField();      // 0 ExpressionSample.name
                    String description = (String) row.get(1).getField(); // 1 ExpressionSample.description
                    String tissue = (String) row.get(2).getField();      // 2 ExpressionSample.tissue
                    String treatment = (String) row.get(3).getField();   // 3 ExpressionSample.treatment
                    String genotype = (String) row.get(4).getField();    // 4 ExpressionSample.genotype
                    String repgroup = (String) row.get(5).getField();    // 5 ExpressionSample.repgroup
                    samples.add(sample);
                    if (repgroup!=null) {
                        sampleRepgroups.add(repgroup);
                    }
                    sampleDescriptions.put(sample, description);
                    sampleTissues.put(sample, tissue);
                    sampleTreatments.put(sample, treatment);
                    sampleGenotypes.put(sample, genotype);
                }
            }
            // if no samples return an empty JSON string and bail
            if (samples.size()==0) {
                request.setAttribute("expressionValueJSON", "{}");
                return;
            }
            // query the expression values for this source and gene, put them in a list
            PathQuery valuesQuery = getExpressionValuesQuery(model, source, geneID);
            List<ExprValue> exprValues = new LinkedList<>();
            ExportResultsIterator valuesResult;
            try {
                valuesResult = executor.execute(valuesQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving expression values.", e);
            }
            while (valuesResult.hasNext()) {
                List<ResultElement> row = valuesResult.next();
                Integer num = (Integer) row.get(0).getField();        // 0 Gene.expressionValues.sample.num
                String sample = (String) row.get(1).getField();       // 1 Gene.expressionValues.sample.identifier
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
            List<String> vars = new LinkedList<>();
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
            Map<String, Object> xInBarchartData = new LinkedHashMap<>();
            Map<String, Object> yInBarchartData =  new LinkedHashMap<>();
            Map<String, Object> barchartData = new LinkedHashMap<>();
            if (sampleRepgroups.size()>0) {
                xInBarchartData.put("repgroup", sampleRepgroups);
            }
            yInBarchartData.put("vars", vars);
            yInBarchartData.put("smps", samples);
            yInBarchartData.put("data", data);
            barchartData.put("x", xInBarchartData);
            barchartData.put("y", yInBarchartData);
            // the JSON data
            JSONObject jo = new JSONObject(barchartData);
            // add this JSON to the list of JSONs
            jsonList.add(jo.toString());
            // add the the sample descriptions, tissues, treatments, genotypes to their lists
            JSONObject descriptionsJSON = new JSONObject(sampleDescriptions);
            JSONObject tissuesJSON = new JSONObject(sampleTissues);
            JSONObject treatmentsJSON = new JSONObject(sampleTreatments);
            JSONObject genotypesJSON = new JSONObject(sampleGenotypes);
            // add the various descriptive JSONs to their lists
            descriptionsList.add(descriptionsJSON.toString());
            tissuesList.add(tissuesJSON.toString());
            treatmentsList.add(treatmentsJSON.toString());
            genotypesList.add(genotypesJSON.toString());
        }
        // set the return attributes
        request.setAttribute("sources", sources);
        request.setAttribute("sourcesJSON", sourcesJSON);
        request.setAttribute("jsonList", jsonList);
        request.setAttribute("unitsList", unitsList);
        request.setAttribute("descriptionsList", descriptionsList);
        request.setAttribute("tissuesList", tissuesList);
        request.setAttribute("treatmentsList", treatmentsList);
        request.setAttribute("genotypesList", genotypesList);
    }

    /**
     * Create a path query to retrieve expression sources associated with the given gene, alphabetically by ExpressionSource.primaryIdentifier.
     *
     * @param model the model
     * @param geneID the gene for which sources are queried
     * @return the path query
     */
    private PathQuery getSourcesQuery(Model model, String geneID) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionValue.sample.source.id");                // 0
        query.addView("ExpressionValue.sample.source.primaryIdentifier"); // 1
        query.addView("ExpressionValue.sample.source.synopsis");          // 2
        query.addConstraint(Constraints.eq("ExpressionValue.feature.primaryIdentifier", geneID));
        query.addOrderBy("ExpressionValue.sample.source.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve the sample identifier = ExpressionSample.name.
     *
     * <attribute name="name" type="java.lang.String"/>
     * <attribute name="description" type="java.lang.String"/>
     * <attribute name="tissue" type="java.lang.String"/>
     * <attribute name="treatment" type="java.lang.String"/>
     * <attribute name="genotype" type="java.lang.String"/>
     * <attribute name="replicateGroup" type="java.lang.String"/>
     *
     * <attribute name="num" type="java.lang.Integer"/>
     * <attribute name="identifier" type="java.lang.String"/>
     * <attribute name="bioSample" type="java.lang.String"/>
     * <attribute name="sraExperiment" type="java.lang.String"/>
     * <attribute name="species" type="java.lang.String"/>
     * <attribute name="developmentStage" type="java.lang.String"/>
     * <reference name="source" referenced-type="ExpressionSource" reverse-reference="samples"/>
     *
     * @param model the model
     * @param source the primaryIdentifier of the expression source
     * @return the path query
     */
    private PathQuery getSamplesQuery(Model model, String source) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionSample.name");           // 0
        query.addView("ExpressionSample.description");    // 1
        query.addView("ExpressionSample.tissue");         // 2
        query.addView("ExpressionSample.treatment");      // 3
        query.addView("ExpressionSample.genotype");       // 4
        query.addView("ExpressionSample.replicateGroup"); // 5
        query.addConstraint(Constraints.eq("ExpressionSample.source.primaryIdentifier", source));
        query.addOrderBy("ExpressionSample.num", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve the expression unit from ExpressionValue.
     *
     * @param model  the model
     * @param source the identifier of the ExpressionSource
     * @return the path query
     */
    PathQuery getExpressionUnitQuery(Model model, String source) {
        PathQuery query = new PathQuery(model);
        // Add views
        query.addView("ExpressionValue.unit"); // 0
        // Add source and bag constraints
        query.addConstraint(Constraints.eq("ExpressionValue.sample.source.primaryIdentifier", source));
        List<String> verifyList = query.verifyQuery();
        if (!verifyList.isEmpty()) throw new RuntimeException("Expression unit query invalid: "+verifyList);
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
    private PathQuery getExpressionValuesQuery(Model model, String source, String geneID) {
        PathQuery query = new PathQuery(model);
        // Add views
        query.addViews(
                       "ExpressionValue.sample.num",         // 0
                       "ExpressionValue.sample.identifier",  // 1
                       "ExpressionValue.value"               // 2
                       );
        // Add orderby
        query.addOrderBy("ExpressionValue.sample.num", OrderDirection.ASC);
        // Add constraints and you can edit the constraint values below
        query.addConstraint(Constraints.eq("ExpressionValue.sample.source.primaryIdentifier", source));
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
