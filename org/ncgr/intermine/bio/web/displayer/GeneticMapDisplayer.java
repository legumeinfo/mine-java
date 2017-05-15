package org.ncgr.intermine.bio.web.displayer;

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
 * Display a diagram with linkage groups, markers and QTLs for this genetic map.
 *
 * @author Sam Hokin
 */
public class GeneticMapDisplayer extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GeneticMapDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        InterMineObject geneticMap = reportObject.getObject();
        String gmPI;
        try {
            gmPI = (String) geneticMap.getFieldValue("primaryIdentifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting primaryIdentifier.", ex);
        }

        PathQueryExecutor executor = im.getPathQueryExecutor();

        // get the linkage groups and find the maximum length
        double maxLGLength = 0.0;
        Map<String,Double> lgMap = new LinkedHashMap<String,Double>();
        PathQuery lgQuery = queryLinkageGroup(im.getModel(), gmPI);
        ExportResultsIterator lgResult;
        try {
            lgResult = executor.execute(lgQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        while (lgResult.hasNext()) {
            List<ResultElement> row = lgResult.next();
            String primaryIdentifier = (String) row.get(0).getField();
            Double length = (Double) row.get(1).getField();
            lgMap.put(primaryIdentifier, length);
            if ((double)length > maxLGLength) maxLGLength = (double)length;
        }

        // get the genetic markers per linkage group
        Map<String, Map<String,Double>> markerMap = new LinkedHashMap<String, Map<String,Double>>();
        for (String lgPI : lgMap.keySet()) {
            PathQuery markerQuery = queryGeneticMarker(im.getModel(), lgPI);
            ExportResultsIterator markerResult;
            try {
                markerResult = executor.execute(markerQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving data.", e);
            }
            Map<String,Double> markers = new LinkedHashMap<String,Double>();
            while (markerResult.hasNext()) {
                List<ResultElement> row = markerResult.next();
                String primaryIdentifier = (String) row.get(0).getField();
                Double position = (Double) row.get(1).getField();
                markers.put(primaryIdentifier, position);
            }
            markerMap.put(lgPI, markers);
        }

        // get the QTLs per linkage group
        Map<String, Map<String,Double[]>> qtlMap = new LinkedHashMap<String, Map<String,Double[]>>();
        for (String lgPI : lgMap.keySet()) {
            PathQuery qtlQuery = queryQTL(im.getModel(), lgPI);
            ExportResultsIterator qtlResult;
            try {
                qtlResult = executor.execute(qtlQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving data.", e);
            }
            Map<String,Double[]> qtls = new LinkedHashMap<String,Double[]>();
            while (qtlResult.hasNext()) {
                List<ResultElement> row = qtlResult.next();
                String primaryIdentifier = (String) row.get(0).getField();
                Double[] span = new Double[2];
                span[0] = (Double) row.get(1).getField();
                span[1] = (Double) row.get(2).getField();
                qtls.put(primaryIdentifier, span);
            }
            qtlMap.put(lgPI, qtls);
        }

        // JSON data - non-labeled array - order matters!
        List<Object> trackData = new LinkedList<Object>();
        for (String lgPI : lgMap.keySet()) {
            
            // LINKAGE GROUP TRACK
            Map<String,Object> lgTrack = new LinkedHashMap<String,Object>();
            lgTrack.put("type", "box");
            // linkage group track data array
            List<Object> lgDataArray = new LinkedList<Object>();
            // the single data item
            Map<String,Object> lgData = new LinkedHashMap<String,Object>();
            lgData.put("id", lgPI);
            lgData.put("fill", "purple");
            lgData.put("outline", "black");
            // linkage group box positions = array of one pair
            List<Object> lgPositionsArray = new LinkedList<Object>();
            double[] length = new double[2];
            length[0] = 0.0;
            length[1] = (double) lgMap.get(lgPI);
            lgPositionsArray.add(length);
            lgData.put("data", lgPositionsArray);
            lgDataArray.add(lgData);
            lgTrack.put("data", lgDataArray);
            trackData.add(lgTrack);

            // MARKERS TRACK
            Map<String,Object> markersTrack = new LinkedHashMap<String,Object>();
            markersTrack.put("type", "triangle");
            // markers track data array
            List<Object> markersDataArray = new LinkedList<Object>();
            Map<String,Double> markers = markerMap.get(lgPI);
            for (String markerPI : markers.keySet()) {
                Map<String,Object> markerData = new LinkedHashMap<String,Object>();
                markerData.put("id", markerPI);
                markerData.put("fill", "darkred");
                markerData.put("outline", "black");
                markerData.put("offset", markers.get(markerPI));
                markersDataArray.add(markerData);
            }
            markersTrack.put("data", markersDataArray);
            trackData.add(markersTrack);
            
            // QTLS TRACK
            Map<String,Object> qtlsTrack = new LinkedHashMap<String,Object>();
            qtlsTrack.put("type", "box");
            // QTLs track data array
            List<Object> qtlsDataArray = new LinkedList<Object>();
            Map<String,Double[]> qtls = qtlMap.get(lgPI);
            for (String qtlPI : qtls.keySet()) {
                Map<String,Object> qtlData = new LinkedHashMap<String,Object>();
                qtlData.put("id", qtlPI);
                qtlData.put("fill", "yellow");
                qtlData.put("outline", "black");
                // QTL box positions = array of one pair
                List<Object> qtlPositionsArray = new LinkedList<Object>();
                Double[] span = qtls.get(qtlPI);
                double[] coords = new double[2];
                coords[0] = (double) span[0];
                coords[1] = (double) span[1];
                qtlPositionsArray.add(coords);
                qtlData.put("data", qtlPositionsArray);
                qtlsDataArray.add(qtlData);
            }
            qtlsTrack.put("data", qtlsDataArray);
            trackData.add(qtlsTrack);
                        
        }

        // output scalar vars
        request.setAttribute("tracksCount", lgMap.size());
        request.setAttribute("maxLGLength", maxLGLength);

        // entire thing is in a single tracks JSON array
        Map<String,Object> tracks = new LinkedHashMap<String,Object>();
        tracks.put("tracks", trackData);
        request.setAttribute("tracksJSON", new JSONObject(tracks).toString());

    }

    /**
     * Create a path query to retrieve linkage groups associated with a given genetic map.
     *
     * @param model the model
     * @param gmPI  the genetic map primary identifier
     * @return the path query
     */
    private PathQuery queryLinkageGroup(Model model, String gmPI) {
        PathQuery query = new PathQuery(model);
        query.addViews("LinkageGroup.primaryIdentifier",
                       "LinkageGroup.length"
                       );
        query.addConstraint(Constraints.eq("LinkageGroup.geneticMap.primaryIdentifier", gmPI));
        query.addOrderBy("LinkageGroup.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve genetic markers associated with a given linkage group.
     *
     * @param model the model
     * @param lgPI  the linkage group primaryIdentifier
     * @return the path query
     */
    private PathQuery queryGeneticMarker(Model model, String lgPI) {
        PathQuery query = new PathQuery(model);
        query.addViews("GeneticMarker.primaryIdentifier",
                       "GeneticMarker.linkageGroupPositions.position"
                       );
        query.addConstraint(Constraints.eq("GeneticMarker.linkageGroupPositions.linkageGroup.primaryIdentifier", lgPI));
        query.addOrderBy("GeneticMarker.linkageGroupPositions.position", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve QTLs associated with a given linkage group.
     *
     * @param model the model
     * @param lgPI  the linkage group primaryIdentifier
     * @return the path query
     */
    private PathQuery queryQTL(Model model, String lgPI) {
        PathQuery query = new PathQuery(model);
        query.addViews("QTL.primaryIdentifier",
                       "QTL.linkageGroupRanges.begin",
                       "QTL.linkageGroupRanges.end"
                       );
        query.addConstraint(Constraints.eq("QTL.linkageGroupRanges.linkageGroup.primaryIdentifier", lgPI));
        query.addOrderBy("QTL.linkageGroupRanges.begin", OrderDirection.ASC);
        return query;
    }


}
