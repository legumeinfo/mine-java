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
import java.util.LinkedHashMap;
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

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.moment.Mean;

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
        if (!expressionType.toLowerCase().equals("gene")) {
            // it ain't genes, bail
            LOG.error("called on a bag of type:"+expressionType+", exiting.");
            return null;
        }

        // store the source names in a list and the source data in a list of JSON
        List<String> sources = new LinkedList<>();
        List<String> sourcesJSON = new LinkedList<>();
        
        // we'll store the JSON blocks in a string list, as well as a list of sample counts and description maps
        List<String> expressionJSON = new LinkedList<>();
        List<String> descriptionsJSON = new LinkedList<>();
        List<String> namesJSON = new LinkedList<>();
        List<Integer> geneCounts = new LinkedList<>();
        List<Integer> sampleCounts = new LinkedList<>();

        // store a map of Gene.secondaryIdentifier to Gene.primaryIdentifier for utility purposes on widget
        Map<String, String> genePrimaryIDMap = new LinkedHashMap<>();

        // query ALL the sources and load the ones with enough genes (more than two)
        PathQuery sourcesQuery = querySources(model, bag);
        ExportResultsIterator sourcesResult;
        try {
            sourcesResult = executor.execute(sourcesQuery);
        } catch (ObjectStoreException e) {
            setErrorMessage(request, "Error retrieving sources:"+e.toString());
            return null;
        }
        if (!sourcesResult.hasNext()) {
            setErrorMessage(request, sourcesQuery);
            return null;
        }
        while (sourcesResult.hasNext()) {
            // grab the fields
            List<ResultElement> sourceRow = sourcesResult.next();
            if (sourceRow==null || sourceRow.get(0)==null || sourceRow.get(0).getField()==null) {
                throw new RuntimeException("Null row or row element retrieving sources.");
            }
            Integer id = (Integer)sourceRow.get(0).getField();    // 0 ExpressionValue.sample.source.id
	    String source = (String)sourceRow.get(1).getField();  // 1 ExpressionValue.sample.source.primaryIdentifier

            // query the expression unit
            PathQuery expressionUnitQuery = queryExpressionUnit(model, source);
            ExportResultsIterator unitResult;
            try {
                unitResult = executor.execute(expressionUnitQuery, 0, 1);
            } catch (ObjectStoreException e) {
                setErrorMessage(request, "Error retrieving expression unit: "+e.toString());
                return null;
            }
            List<ResultElement> unitRow = unitResult.next();
            String unit = (String) unitRow.get(0).getField();

            // query samples and sample descriptions
            List<String> samples = new LinkedList<>();
            Map<String,String> sampleDescriptions = new LinkedHashMap<>();
            Map<String,String> sampleNames = new LinkedHashMap<>();
            PathQuery samplesQuery = querySamples(model, source);
            ExportResultsIterator samplesResult;
            try {
                samplesResult = executor.execute(samplesQuery);
            } catch (ObjectStoreException e) {
                setErrorMessage(request, "Error retrieving samples: "+e.toString());
                return null;
            }
            while (samplesResult.hasNext()) {
                List<ResultElement> sampleRow = samplesResult.next();
                if (sampleRow==null || sampleRow.get(0)==null || sampleRow.get(0).getField()==null) {
                    throw new RuntimeException("Null row or row element 0 retrieving samples.");
                }
                String sample = (String) sampleRow.get(0).getField();
                String sampleDescription = (String) sampleRow.get(1).getField();
                String sampleName = (String) sampleRow.get(2).getField();
                samples.add(sample);
                sampleDescriptions.put(sample, sampleDescription);
                sampleNames.put(sample, sampleName);
            }
            
            // if no samples return an empty JSON string and bail - that's a bug!
            if (samples.size()==0) {
                setErrorMessage(request, "No samples returned for source:"+source);
                return null;
            }
            
            // query the expression values for this source and gene bag
            Map<String, List<ExprValue>> expressionValueMap = new LinkedHashMap<>();
            PathQuery valuesQuery = queryExpressionValues(model, source, bag);
            ExportResultsIterator valuesResult;
            try {
                valuesResult = executor.execute(valuesQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving expression values.", e);
            }
            while (valuesResult.hasNext()) {
                List<ResultElement> valueRow = valuesResult.next();
                String genePrimaryID = (String) valueRow.get(0).getField(); // 0 ExpressionValue.feature.primaryIdentifier
                String geneID = (String) valueRow.get(1).getField();        // 1 ExpressionValue.feature.secondaryIdentifier
		Integer num = (Integer) valueRow.get(2).getField();         // 2 ExpressionValue.sample.num
                String sample = (String) valueRow.get(3).getField();        // 3 ExpressionValue.sample.identifier
		Double value = (Double) valueRow.get(4).getField();         // 4 ExpressionValue.value
                ExprValue expValue = new ExprValue(sample, num, value, geneID);
                if (!expressionValueMap.containsKey(geneID)) {
                    // put the gene primary identifier into the map
                    genePrimaryIDMap.put(geneID, genePrimaryID);
                    // create a new list with space for n (size of samples) ExpressionValues
                    List<ExprValue> expressionValueList = new ArrayList<>(Collections.nCopies(samples.size(), new ExprValue()));
                    expressionValueList.set(samples.indexOf(sample), expValue);
                    expressionValueMap.put(geneID, expressionValueList);
                } else {
                    // gene already here, update the value of this sample
                    expressionValueMap.get(geneID).set(samples.indexOf(sample), expValue);
                }
            }
            
            // if no expression values return an empty JSON string
            if (expressionValueMap.size()==0) {
                setErrorMessage(request, "No expression values retrieved.");
                return null;
            }
            
            // canvasXpress "smps" = genes
            List<String> genes =  new ArrayList<>(expressionValueMap.keySet());
            
            // add to the source lists
            sources.add(source);
            Map<String,Object> jsonMap = new LinkedHashMap<>();
            jsonMap.put("id", id);
            jsonMap.put("primaryIdentifier", source);
            jsonMap.put("unit", unit);
            sourcesJSON.add(new JSONObject(jsonMap).toString());
                
            // canvasXpress "data" = double[samples][genes]
            double[][] data = new double[samples.size()][genes.size()];
            for (int j=0; j<genes.size(); j++) {
                String gene = genes.get(j);
                for (int i=0; i<samples.size(); i++) {
                    if (expressionValueMap.get(gene)!=null && expressionValueMap.get(gene).get(i)!=null) {
                        data[i][j] = (double) expressionValueMap.get(gene).get(i).value;
                    } else {
                        data[i][j] = 0.0;
                    }
                }
            }
                
            // analysis: calculate the mean Pearson's correlation coefficient of each gene with each other gene
            PearsonsCorrelation pCorr = new PearsonsCorrelation();
            Mean mean = new Mean();
            double[] meanCorr = new double[genes.size()];
            for (int j=0; j<genes.size(); j++) {
                String gene1 = genes.get(j);
                double[] values1 = new double[samples.size()];
                for (int i=0; i<samples.size(); i++) {
                    if (expressionValueMap.get(gene1)!=null && expressionValueMap.get(gene1).get(i)!=null) {
                        values1[i] = (double) expressionValueMap.get(gene1).get(i).value;
                    }
                }
                double totalCorr = 0.0;
                int count = 0;
                for (int k=0; k<genes.size(); k++) {
                    if (j!=k) {
                        String gene2 = genes.get(k);
                        double[] values2 = new double[samples.size()];
                        for (int i=0; i<samples.size(); i++) {
                            if (expressionValueMap.get(gene2)!=null && expressionValueMap.get(gene2).get(i)!=null) {
                                values2[i] = (double) expressionValueMap.get(gene2).get(i).value;
                            }
                        }
                        double corr = pCorr.correlation(values1, values2);
                        if (!Double.isNaN(corr)) {
                            count++;
                            totalCorr += corr;
                        }
                    }
                }
                if (count>0) meanCorr[j] = totalCorr/count;
            }             
                    
            // put the main heatmap data into a JSONObject for "y"
            Map<String, Object> yInHeatmapData = new LinkedHashMap<>();
            yInHeatmapData.put("vars", samples);
            yInHeatmapData.put("smps", genes);
            yInHeatmapData.put("data", data);
                    
            // load analysis data into "x"
            Map<String,Object> xInHeatmapData = new LinkedHashMap<>();
            xInHeatmapData.put("PCorr", meanCorr);
                    
            // create the map that gets converted to the JSON object
            Map<String, Object> heatmapData = new LinkedHashMap<>();
            heatmapData.put("x", xInHeatmapData);
            heatmapData.put("y", yInHeatmapData);
                    
            // convert to JSONObject and add to expressionJSON
            expressionJSON.add(new JSONObject(heatmapData).toString());
                    
            // add the sample descriptions to the list
            descriptionsJSON.add(new JSONObject(sampleDescriptions).toString());

            // add the sample names to the list
            namesJSON.add(new JSONObject(sampleNames).toString());
                    
            // add these results to the results maps
            geneCounts.add(genes.size());
            sampleCounts.add(samples.size());
        }
        
        // set the return attributes
        request.setAttribute("errorMessage", "");
        request.setAttribute("sources", sources);
        request.setAttribute("genePrimaryIDMap", genePrimaryIDMap);
        request.setAttribute("geneCounts", geneCounts);
        request.setAttribute("sampleCounts", sampleCounts);
        request.setAttribute("sourcesJSON", sourcesJSON);
        request.setAttribute("expressionJSON", expressionJSON);
        request.setAttribute("descriptionsJSON", descriptionsJSON);
        request.setAttribute("namesJSON", namesJSON);
        
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
     * Create a path query to retrieve expression sources for the genes in the bag, alphabetically by ExpressionSource.primaryIdentifier.
     *
     * @param model the model
     * @param bag   the bag o'genes
     * @return the path query
     */
    PathQuery querySources(Model model, InterMineBag bag) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionValue.sample.source.id");                  // 0
        query.addView("ExpressionValue.sample.source.primaryIdentifier");   // 1
        query.addConstraint(Constraints.in("ExpressionValue.feature", bag.getName()));
        query.addOrderBy("ExpressionValue.sample.source.primaryIdentifier", OrderDirection.ASC);
        List<String> verifyList = query.verifyQuery();
        if (!verifyList.isEmpty()) throw new RuntimeException("Sources query invalid: "+verifyList);
        return query;
    }

    /**
     * Create a path query to retrieve the sample identifiers and descriptions for a given source.
     *
     * @param model the model
     * @param source the primaryIdentifier of the expression source
     * @return the path query
     */
    PathQuery querySamples(Model model, String source) {
        PathQuery query = new PathQuery(model);
        query.addView("ExpressionSample.identifier");   // 0
        query.addView("ExpressionSample.description");  // 1
        query.addView("ExpressionSample.name");         // 2
        query.addConstraint(Constraints.eq("ExpressionSample.source.primaryIdentifier", source));
        query.addOrderBy("ExpressionSample.num", OrderDirection.ASC);
        List<String> verifyList = query.verifyQuery();
        if (!verifyList.isEmpty()) throw new RuntimeException("Samples query invalid: "+verifyList);
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
    PathQuery queryExpressionValues(Model model, String source, InterMineBag bag) {
        PathQuery query = new PathQuery(model);
        // Add views
        query.addView("ExpressionValue.feature.primaryIdentifier");   // 0
        query.addView("ExpressionValue.feature.secondaryIdentifier"); // 1
	query.addView("ExpressionValue.sample.num");                  // 2
	query.addView("ExpressionValue.sample.identifier");           // 3
	query.addView("ExpressionValue.value");                       // 4
        // Add orderby
	query.addOrderBy("ExpressionValue.feature.secondaryIdentifier", OrderDirection.ASC);
        query.addOrderBy("ExpressionValue.sample.num", OrderDirection.ASC);
        // Add source and bag constraints
        query.addConstraint(Constraints.eq("ExpressionValue.sample.source.primaryIdentifier", source));
        query.addConstraint(Constraints.in("ExpressionValue.feature", bag.getName()));
	query.addConstraint(Constraints.isNotNull("ExpressionValue.value"));
        List<String> verifyList = query.verifyQuery();
        if (!verifyList.isEmpty()) throw new RuntimeException("Expression values query invalid: "+verifyList);
        return query;
    }

    /**
     * Create a path query to retrieve the expression unit from ExpressionValue.
     *
     * @param model  the model
     * @param source the primaryIdentifier of the ExpressionSource
     * @return the path query
     */
    PathQuery queryExpressionUnit(Model model, String source) {
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
     * Return empty request attributes as the result of an error.
     *
     * @param request the supplied HttpServletRequest object
     */
    void setEmptyRequestAttributes(HttpServletRequest request) {
        request.setAttribute("sources", null);
        request.setAttribute("sourcesJSON", null);
        request.setAttribute("geneCounts", null);
        request.setAttribute("sampleCounts", null);
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

    /**
     * Expression value container.
     */
    private class ExprValue {
        String sample;
        int num;
        double value;
        String featureId;
        ExprValue() {
        }
        ExprValue(String sample, int num, double value, String featureId) {
            this.sample = sample;
            this.num = num;
            this.value = value;
            this.featureId = featureId;
        }
    }
}
