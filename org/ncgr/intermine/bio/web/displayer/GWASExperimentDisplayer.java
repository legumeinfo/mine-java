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
 * Generates JSON for CanvasXpress plot of QTL p-values versus marker position.
 *
 * @author Sam Hokin
 */
public class GWASExperimentDisplayer extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GWASExperimentDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        InterMineObject gwasExperiment = reportObject.getObject();
        String gwasIdentifier;
        try {
            gwasIdentifier = (String) gwasExperiment.getFieldValue("primaryIdentifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting primaryIdentifier.", ex);
        }

        PathQueryExecutor executor = im.getPathQueryExecutor();

        // get the QTLs associated with this GWAS experiment
        // QTL.primaryIdentifier,                     // 0
        // QTL.pValue,                                // 1
        // QTL.markers.primaryIdentifier,             // 2
        // QTL.markers.chromosome.primaryIdentifier,  // 3
        // QTL.markers.chromosome.length,             // 4
        // QTL.markers.chromosomeLocation.start,      // 5
        // QTL.markers.chromosomeLocation.end         // 6
        Map<String,String> qtlChromosomeMap = new LinkedHashMap<String,String>();
        Map<String,String> qtlMarkerMap = new LinkedHashMap<String,String>();
        Map<String,Integer> qtlStartMap = new LinkedHashMap<String,Integer>();
        Map<String,Integer> qtlEndMap = new LinkedHashMap<String,Integer>();
        Map<String,Double> qtlPvalueMap = new LinkedHashMap<String,Double>();
        Map<String,Integer> chromosomeLengthMap = new LinkedHashMap<String,Integer>();
        PathQuery qtlQuery = queryQTLs(im.getModel(), gwasIdentifier);
        ExportResultsIterator qtlResult;
        try {
            qtlResult = executor.execute(qtlQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        while (qtlResult.hasNext()) {
            List<ResultElement> row = qtlResult.next();
            String qtlIdentifier = (String) row.get(0).getField();
            Double pValue = (Double) row.get(1).getField();
            String markerIdentifier = (String) row.get(2).getField();
            String chromosomeIdentifier = (String) row.get(3).getField();
            Integer chromosomeLength = (Integer) row.get(4).getField();
            Integer markerStart = (Integer) row.get(5).getField();
            Integer markerEnd = (Integer) row.get(6).getField();
            qtlChromosomeMap.put(qtlIdentifier, chromosomeIdentifier);
            qtlMarkerMap.put(qtlIdentifier, markerIdentifier);
            qtlStartMap.put(qtlIdentifier, markerStart);
            qtlEndMap.put(qtlIdentifier, markerEnd);
            qtlPvalueMap.put(qtlIdentifier, pValue);
            chromosomeLengthMap.put(chromosomeIdentifier, chromosomeLength);
        }

        // form the JSON for the displayer

        String[] smps = { "qtlPosition", "log10pValue" };
        Map<String,Object> smpsMap = new LinkedHashMap<String,Object>();
        smpsMap.put("smps", smps);

        List<String> vars = new LinkedList<String>();
        for (String qtlIdentifier : qtlChromosomeMap.keySet()) {
            vars.add(qtlIdentifier);
        }
        Map<String,Object> varsMap = new LinkedHashMap<String,Object>();
        varsMap.put("vars", vars);

        List<Object> data = new LinkedList<Object>();
        for (String qtlIdentifier : qtlChromosomeMap.keySet()) {
            String chromosomeIdentifier = qtlChromosomeMap.get(qtlIdentifier);
            int chromosomeLength = chromosomeLengthMap.get(chromosomeIdentifier);
            // assume chromosome name ends in NN so we can get a number
            int len = chromosomeIdentifier.length();
            int chromosomeNumber = Integer.parseInt(chromosomeIdentifier.substring(len-2,len));
            int qtlStart = qtlStartMap.get(qtlIdentifier);
            double frac = (double) qtlStart / (double) chromosomeLength;
            double qtlPosition = (double) (chromosomeNumber-1) + frac;
            double log10pValue = -Math.log10((double) qtlPvalueMap.get(qtlIdentifier));
            double[] values = { qtlPosition, log10pValue };
            data.add(values);
        }
        Map<String,Object> dataMap = new LinkedHashMap<String,Object>();
        dataMap.put("data", data);

        Map<String,Object> yMap = new LinkedHashMap<String,Object>();
        yMap.put("smps", smps);
        yMap.put("vars", vars);
        yMap.put("data", data);
        JSONObject y = new JSONObject(yMap);

        // decorative markers show the QTLs :)
        String[] samples = {"qtlPosition","log10pValue"};
        List<Map<String,Object>> qtlList = new LinkedList<Map<String,Object>>();
        for (String qtl : qtlMarkerMap.keySet()) {
            String marker = qtlMarkerMap.get(qtl);
            Map<String,Object> markerMap = new LinkedHashMap<String,Object>();
            markerMap.put("variable", qtl);
            markerMap.put("text", qtl);
            markerMap.put("type", "text");
            markerMap.put("sample", samples);
            qtlList.add(markerMap);
        }
        Map<String,Object> markerMap = new LinkedHashMap<String,Object>();
        markerMap.put("marker", qtlList);
        JSONObject markers = new JSONObject(markerMap);
        
        // send the data on its way
        request.setAttribute("y", y.toString());
        request.setAttribute("markers", markers.toString());
    }

    /**
     * Create a path query to retrieve linkage groups associated with a given genetic map.
     *
     * @param model the model
     * @param gmID  the genetic map primaryIdentifier
     * @return the path query
     */
    private PathQuery queryLinkageGroups(Model model, String gwasIdentifier) {
        PathQuery query = new PathQuery(model);
        query.addViews(
                       "LinkageGroup.id",
                       "LinkageGroup.primaryIdentifier",
                       "LinkageGroup.length"
                       );
        query.addConstraint(Constraints.eq("LinkageGroup.gwasExperiment.primaryIdentifier", gwasIdentifier));
        query.addOrderBy("LinkageGroup.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve genetic markers associated with a given linkage group.
     *
     * @param model the model
     * @param lgID  the linkage group id
     * @return the path query
     */
    private PathQuery queryGeneticMarkers(Model model, int lgID) {
        PathQuery query = new PathQuery(model);
        query.addViews(
                       "GeneticMarker.id",
                       "GeneticMarker.primaryIdentifier",
                       "GeneticMarker.linkageGroupPositions.position"
                       );
        query.addConstraint(Constraints.eq("GeneticMarker.linkageGroupPositions.linkageGroup.id", String.valueOf(lgID)));
        query.addOrderBy("GeneticMarker.linkageGroupPositions.position", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve QTLs associated with the GWAS experiment.
     *
     * @param model the model
     * @param gwasIdentifier the GWAS experiment primaryIdentifier
     * @return the path query
     */
    private PathQuery queryQTLs(Model model, String gwasIdentifier) {
        PathQuery query = new PathQuery(model);
        query.addViews( 
                       "QTL.primaryIdentifier",                     // 0
                       "QTL.pValue",                                // 1
                       "QTL.markers.primaryIdentifier",             // 2
                       "QTL.markers.chromosome.primaryIdentifier",  // 3
                       "QTL.markers.chromosome.length",             // 4
                       "QTL.markers.chromosomeLocation.start",      // 5
                       "QTL.markers.chromosomeLocation.end"         // 6
                      );
        query.addConstraint(Constraints.eq("QTL.gwasExperiment.primaryIdentifier", gwasIdentifier));
        query.addOrderBy("QTL.markers.chromosome.primaryIdentifier", OrderDirection.ASC);
        query.addOrderBy("QTL.markers.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

    /**
     * A class to hold linkage group fields
     */
    public class LinkageGroup {
        public int id;
        public String primaryIdentifier;
        public double length;
        public LinkageGroup(int id, String primaryIdentifier, double length) {
            this.id = id;
            this.primaryIdentifier = primaryIdentifier;
            this.length = length;
        }
    }

    /**
     * A class to hold genetic marker fields
     */
    public class GeneticMarker {
        public int id;
        public String primaryIdentifier;
        public double position;
        public GeneticMarker(int id, String primaryIdentifier, double position) {
            this.id = id;
            this.primaryIdentifier = primaryIdentifier;
            this.position = position;
        }
    }

    /**
     * A class to hold QTL fields
     */
    public class QTL {
        public int id;
        public String primaryIdentifier;
        public double[] span;
        public QTL(int id, String primaryIdentifier, double[] span) {
            this.id = id;
            this.primaryIdentifier = primaryIdentifier;
            this.span = span;
        }
    }

}
