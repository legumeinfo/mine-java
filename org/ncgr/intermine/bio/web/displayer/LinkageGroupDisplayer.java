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
 * Display a diagram with markers and QTLs for this linkage group.
 *
 * @author Sam Hokin
 */
public class LinkageGroupDisplayer extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public LinkageGroupDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        // get the data
        InterMineObject linkageGroup = reportObject.getObject();
        String lgPI;
        double lgLength;
        try {
            lgPI = (String) linkageGroup.getFieldValue("primaryIdentifier");
            lgLength = (double)(Double)linkageGroup.getFieldValue("length");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting primaryIdentifier or length.", ex);
        }

        PathQueryExecutor executor = im.getPathQueryExecutor();

        // use PathQuery to get the markers because of linkageGroupPosition intermediary
        PathQuery markerQuery = queryGeneticMarker(im.getModel(), lgPI);
        ExportResultsIterator markerResult;
        try {
            markerResult = executor.execute(markerQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        Map<String,Double> markers = new LinkedHashMap<String,Double>();
        while (markerResult.hasNext()) {
            List<ResultElement> row = markerResult.next();
            String primaryIdentifier = (String) row.get(0).getField();
            Double position = (Double) row.get(1).getField();
            markers.put(primaryIdentifier, position);
        }

        // use PathQuery to get the QTLs because of linkageGroupRange intermediary
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

        // JSON data - non-labeled array - order matters!
        List<Object> trackData = new LinkedList<Object>();
            
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
        length[1] = lgLength;
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

        // entire thing is in a single tracks JSON array
        Map<String,Object> tracks = new LinkedHashMap<String,Object>();
        tracks.put("tracks", trackData);

        // output to HTTP response
        request.setAttribute("maxLGLength", lgLength);
        request.setAttribute("tracksJSON", new JSONObject(tracks).toString());

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
