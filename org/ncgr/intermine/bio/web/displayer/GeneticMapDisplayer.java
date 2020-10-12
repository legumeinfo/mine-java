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
        Map<Integer,LinkageGroup> lgMap = new LinkedHashMap<Integer,LinkageGroup>();
        PathQuery lgQuery = queryLinkageGroups(im.getModel(), gmPI);
        ExportResultsIterator lgResult;
        try {
            lgResult = executor.execute(lgQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        while (lgResult.hasNext()) {
            List<ResultElement> row = lgResult.next();
            int id = (int) (Integer) row.get(0).getField();
            String identifier = (String) row.get(1).getField();
            double length = (double) (Double) row.get(2).getField();
            int number = (int) (Integer) row.get(3).getField();
            LinkageGroup lg = new LinkageGroup(id, identifier, length, number);
            lgMap.put(id, lg);
            if ((double)length > maxLGLength) maxLGLength = (double)length;
        }

        // get the genetic markers per linkage group
        Map<Integer, Map<Integer,GeneticMarker>> markerMap = new LinkedHashMap<Integer, Map<Integer,GeneticMarker>>();
        for (Integer lgID : lgMap.keySet()) {
            PathQuery markerQuery = queryGeneticMarkers(im.getModel(), lgID);
            ExportResultsIterator markerResult;
            try {
                markerResult = executor.execute(markerQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving data.", e);
            }
            Map<Integer,GeneticMarker> markers = new LinkedHashMap<Integer,GeneticMarker>();
            while (markerResult.hasNext()) {
                List<ResultElement> row = markerResult.next();
                int id = (int) (Integer) row.get(0).getField();
                String primaryIdentifier = (String) row.get(1).getField();
                double position = (double) (Double) row.get(2).getField();
                GeneticMarker marker = new GeneticMarker(id, primaryIdentifier, position);
                markers.put(id, marker);
            }
            markerMap.put(lgID, markers);
        }

        // get the QTLs per linkage group
        Map<Integer, Map<Integer,QTL>> qtlMap = new LinkedHashMap<Integer, Map<Integer,QTL>>();
        for (Integer lgID : lgMap.keySet()) {
            PathQuery qtlQuery = queryQTLs(im.getModel(), lgID);
            ExportResultsIterator qtlResult;
            try {
                qtlResult = executor.execute(qtlQuery);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("Error retrieving data.", e);
            }
            Map<Integer,QTL> qtls = new LinkedHashMap<Integer,QTL>();
            while (qtlResult.hasNext()) {
                List<ResultElement> row = qtlResult.next();
                int id = (int) (Integer) row.get(0).getField();
                String identifier = (String) row.get(1).getField();
                double[] span = new double[2];
                span[0] = (double) (Double) row.get(2).getField();
                span[1] = (double) (Double) row.get(3).getField();
                QTL qtl = new QTL(id, identifier, span);
                qtls.put(id, qtl);
            }
            qtlMap.put(lgID, qtls);
        }

        // JSON data - non-labeled array - order matters!
        List<Object> trackData = new LinkedList<Object>();
        for (Integer lgID : lgMap.keySet()) {

            LinkageGroup lg = lgMap.get(lgID);
            
            // LINKAGE GROUP TRACK
            Map<String,Object> lgTrack = new LinkedHashMap<String,Object>();
            lgTrack.put("type", "box");
            // linkage group track data array
            List<Object> lgDataArray = new LinkedList<Object>();
            // the single data item
            Map<String,Object> lgData = new LinkedHashMap<String,Object>();
            lgData.put("id", lg.identifier);
            lgData.put("key", lg.id); // for linking
            lgData.put("fill", "purple");
            lgData.put("outline", "black");
            // linkage group box positions = array of one pair
            List<Object> lgPositionsArray = new LinkedList<Object>();
            double[] length = new double[2];
            length[0] = 0.0;
            length[1] = lg.length;
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
            Map<Integer,GeneticMarker> markers = markerMap.get(lgID);
            for (Integer markerID : markers.keySet()) {
                GeneticMarker marker = markers.get(markerID);
                Map<String,Object> markerData = new LinkedHashMap<String,Object>();
                markerData.put("id", marker.primaryIdentifier);
                markerData.put("key", marker.id); // for linking
                markerData.put("fill", "darkred");
                markerData.put("outline", "black");
                markerData.put("offset", marker.position);
                markersDataArray.add(markerData);
            }
            markersTrack.put("data", markersDataArray);
            trackData.add(markersTrack);
            
            // QTLS TRACK
            Map<String,Object> qtlsTrack = new LinkedHashMap<String,Object>();
            qtlsTrack.put("type", "box");
            // QTLs track data array
            List<Object> qtlsDataArray = new LinkedList<Object>();
            Map<Integer,QTL> qtls = qtlMap.get(lgID);
            for (Integer qtlID : qtls.keySet()) {
                QTL qtl = qtls.get(qtlID);
                Map<String,Object> qtlData = new LinkedHashMap<String,Object>();
                qtlData.put("id", qtl.identifier);
                qtlData.put("key", qtl.id); // for linking
                qtlData.put("fill", "yellow");
                qtlData.put("outline", "black");
                // QTL box positions = array of one pair
                List<Object> qtlPositionsArray = new LinkedList<Object>();
                qtlPositionsArray.add(qtl.span);
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
     * @param gmID  the genetic map primaryIdentifier
     * @return the path query
     */
    private PathQuery queryLinkageGroups(Model model, String gmPI) {
        PathQuery query = new PathQuery(model);
        query.addViews(
                       "LinkageGroup.id",
                       "LinkageGroup.identifier",
                       "LinkageGroup.length",
                       "LinkageGroup.number"
                       );
        query.addConstraint(Constraints.eq("LinkageGroup.geneticMap.primaryIdentifier", gmPI));
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
     * Create a path query to retrieve QTLs associated with a given linkage group.
     *
     * @param model the model
     * @param lgID  the linkage group id
     * @return the path query
     */
    private PathQuery queryQTLs(Model model, int lgID) {
        PathQuery query = new PathQuery(model);
        query.addViews(
                       "QTL.id",
                       "QTL.identifier",
                       "QTL.start",
                       "QTL.end"
                       );
        query.addConstraint(Constraints.eq("QTL.linkageGroup.id", String.valueOf(lgID)));
        query.addOrderBy("QTL.start", OrderDirection.ASC);
        return query;
    }

    /**
     * A class to hold linkage group fields
     */
    public class LinkageGroup {
        public int id;
        public String identifier;
        public double length;
        public int number;
        public LinkageGroup(int id, String identifier, double length, int number) {
            this.id = id;
            this.identifier = identifier;
            this.length = length;
            this.number = number;
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
        public String identifier;
        public double[] span;
        public QTL(int id, String identifier, double[] span) {
            this.id = id;
            this.identifier = identifier;
            this.span = span;
        }
    }


}
