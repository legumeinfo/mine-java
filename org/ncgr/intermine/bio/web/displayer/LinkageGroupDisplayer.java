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

import org.apache.log4j.Logger;

import org.json.JSONObject;

/**
 * Display a diagram with linkage groups, markers and QTLs.
 * This displayer detects the type of report page (GeneticMap, LinkageGroup, QTL) and operates accordingly.
 * The corresponding JSP is linkageGroupDisplayer.jsp.
 *
 * @author Sam Hokin
 */
public class LinkageGroupDisplayer extends ReportDisplayer {

    protected static final Logger LOG = Logger.getLogger(LinkageGroupDisplayer.class);

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

        // global since we use it in methods below
        PathQueryExecutor executor  = im.getPathQueryExecutor();
        
        int reportID = reportObject.getId();
        String objectName = reportObject.getClassDescriptor().getSimpleName();

        // get the linkage group(s) and find the maximum length
        Map<Integer,List<ResultElement>> lgMap = new LinkedHashMap<Integer,List<ResultElement>>();
        double maxLGLength = 0.0;
        PathQuery lgQuery = getLinkageGroupQuery(im.getModel(), reportID, objectName);
        ExportResultsIterator lgResult = getResults(executor, lgQuery);
        while (lgResult.hasNext()) {
            List<ResultElement> row = lgResult.next();
            Integer lgID = (Integer) row.get(0).getField();
            lgMap.put(lgID, row);
            double length = (double) (Double) row.get(2).getField();
            if (length>maxLGLength) maxLGLength = length;
        }

        // get the genetic markers per linkage group
        Map<Integer,Map<Integer,List<ResultElement>>> markerMap = new LinkedHashMap<Integer,Map<Integer,List<ResultElement>>>();
        for (Integer lgID : lgMap.keySet()) {
            PathQuery markerQuery = getGeneticMarkerQuery(im.getModel(), (int)lgID);
            ExportResultsIterator markerResult = getResults(executor, markerQuery);
            Map<Integer,List<ResultElement>> markers = new LinkedHashMap<Integer,List<ResultElement>>();
            while (markerResult.hasNext()) {
                List<ResultElement> row = markerResult.next();
                Integer markerID = (Integer) row.get(0).getField();
                markers.put(markerID, row);
            }
            markerMap.put(lgID, markers);
        }

        // get the QTLs per linkage group
        Map<Integer, Map<Integer,List<ResultElement>>> qtlMap = new LinkedHashMap<Integer,Map<Integer,List<ResultElement>>>();
        for (Integer lgID : lgMap.keySet()) {
            PathQuery qtlQuery = getQTLQuery(im.getModel(), (int)lgID);
            ExportResultsIterator qtlResult = getResults(executor, qtlQuery);
            Map<Integer,List<ResultElement>> qtls = new LinkedHashMap<Integer,List<ResultElement>>();
            while (qtlResult.hasNext()) {
                List<ResultElement> row = qtlResult.next();
                Integer qtlID = (Integer) row.get(0).getField();
                qtls.put(qtlID, row);
            }
            qtlMap.put(lgID, qtls);
        }

        // JSON data - non-labeled array - order matters!
        List<Object> trackData = new LinkedList<Object>();
        for (Integer lgID : lgMap.keySet()) {
            
            // LINKAGE GROUP TRACK
            Map<String,Object> lgTrack = new LinkedHashMap<String,Object>();
            lgTrack.put("type", "box");
            // linkage group track data array
            List<Object> lgDataArray = new LinkedList<Object>();
            // the single data item
            Map<String,Object> lgData = new LinkedHashMap<String,Object>();
            List<ResultElement> lgRow = lgMap.get(lgID);
            double[] length = new double[2];
            length[0] = 0.0;
            length[1] = (double) (Double) lgRow.get(2).getField();
            lgData.put("id", (Integer) lgRow.get(1).getField());
            lgData.put("key", (int)lgID);
            lgData.put("fill", "purple");
            lgData.put("outline", "black");
            // linkage group box positions = array of one pair
            List<Object> lgPositionsArray = new LinkedList<Object>();
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
            Map<Integer,List<ResultElement>> markers = markerMap.get(lgID);
            for (Integer markerID : markers.keySet()) {
                List<ResultElement> markerRow = markers.get(markerID);
                double position = (double) (Double) markerRow.get(2).getField();
                Map<String,Object> markerData = new LinkedHashMap<String,Object>();
                markerData.put("id", (String) markerRow.get(1).getField()); // canvasXpress needs it to be called "id"
                markerData.put("key", (int)markerID); // we'll call the id "key" for linking purposes
                markerData.put("fill", "darkred");
                markerData.put("outline", "black");
                markerData.put("offset", position);
                markersDataArray.add(markerData);
            }
            markersTrack.put("data", markersDataArray);
            trackData.add(markersTrack);
            
            // QTLS TRACK
            Map<String,Object> qtlsTrack = new LinkedHashMap<String,Object>();
            qtlsTrack.put("type", "box");
            // QTLs track data array
            List<Object> qtlsDataArray = new LinkedList<Object>();
            Map<Integer,List<ResultElement>> qtls = qtlMap.get(lgID);
            for (Integer qtlID : qtls.keySet()) {
                List<ResultElement> qtlRow = qtls.get(qtlID);
                double[] span = new double[2];
                span[0] = (double) (Double) qtlRow.get(2).getField();
                span[1] = (double) (Double) qtlRow.get(3).getField();
                Map<String,Object> qtlData = new LinkedHashMap<String,Object>();
                qtlData.put("id", (String) qtlRow.get(1).getField()); // canvasXpress needs it to be called "id"
                qtlData.put("key", (int)qtlID); // we'll call the id "key" for linking purposes
                qtlData.put("fill", "yellow");
                qtlData.put("outline", "black");
                // QTL box positions = array of one pair
                List<Object> qtlPositionsArray = new LinkedList<Object>();
                qtlPositionsArray.add(span);
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
     * Create a path query to retrieve linkage groups associated with a given genetic map, QTL or even a single linkage group ID.
     *
     * @param model the model
     * @param gmPI  the genetic map primary identifier
     * @param objectName the name of the report object, i.e. GeneticMap, QTL, LinkageGroup
     * @return the path query
     */
    PathQuery getLinkageGroupQuery(Model model, int reportID, String objectName) {
        PathQuery query = new PathQuery(model);
        query.addViews("LinkageGroup.id",
                       "LinkageGroup.number",
                       "LinkageGroup.length"
                       );
        if (objectName.equals("GeneticMap")) {
            query.addConstraint(Constraints.eq("LinkageGroup.geneticMap.id", String.valueOf(reportID)));
        } else if (objectName.equals("QTL")) {
            query.addConstraint(Constraints.eq("LinkageGroup.QTLs.id", String.valueOf(reportID)));
        } else if (objectName.equals("LinkageGroup")) {
            query.addConstraint(Constraints.eq("LinkageGroup.id", String.valueOf(reportID)));
        }
        query.addOrderBy("LinkageGroup.number", OrderDirection.ASC);
        return query;
    }


    /**
     * Create a path query to retrieve genetic markers associated with a given linkage group.
     *
     * @param model the model
     * @param lgID  the linkage group id
     * @return the path query
     */
    PathQuery getGeneticMarkerQuery(Model model, int lgID) {
        PathQuery query = new PathQuery(model);
        query.addViews("GeneticMarker.id",
                       "GeneticMarker.primaryIdentifier",
                       "GeneticMarker.linkageGroupPositions.position"
                       );
        query.addConstraint(Constraints.eq("GeneticMarker.linkageGroupPositions.linkageGroup.id", String.valueOf(lgID)));
        query.addOrderBy("GeneticMarker.linkageGroupPositions.position", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve QTLs associated with a given linkage group.
     *
     * @param model the model
     * @param lgID  the linkage group id
     * @return the path query
     */
    PathQuery getQTLQuery(Model model, int lgID) {
        PathQuery query = new PathQuery(model);
        query.addViews("QTL.id",
                       "QTL.primaryIdentifier",
                       "QTL.linkageGroupRanges.begin",
                       "QTL.linkageGroupRanges.end"
                       );
        query.addConstraint(Constraints.eq("QTL.linkageGroupRanges.linkageGroup.id", String.valueOf(lgID)));
        query.addOrderBy("QTL.linkageGroupRanges.begin", OrderDirection.ASC);
        return query;
    }

    /**
     * Execute a PathQuery. Just a wrapper to throw an exception so we don't have to do a try/catch block every time above.
     *
     * @param executor the PathQueryExecutor
     * @param query    the PathQuery
     * @return the ExportResultsIterator
     */
    ExportResultsIterator getResults(PathQueryExecutor executor, PathQuery query) {
        try {
            return executor.execute(query);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
    }

}
