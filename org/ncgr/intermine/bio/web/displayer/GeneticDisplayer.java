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
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

import org.json.JSONObject;

/**
 * Generate data sent to a JSP to display a diagram with linkage groups, markers and QTLs.
 *
 * Extend this class for the particular report class by overriding the query methods.
 *
 * NOTE: markers are shown by name from their LinkageGroupPosition. Many of them are not in the mine as GeneticMarker.
 *
 * This displayer does not query ANY genomic data.
 *
 * @author Sam Hokin
 */
public abstract class GeneticDisplayer extends ReportDisplayer {

    // instance variable so methods can access it
    PathQueryExecutor executor;

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GeneticDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {
        // can be used in methods
        executor = im.getPathQueryExecutor();
        
        // getLinkageGroupQuery(): get the desired LinkageGroup records preserving number order
        Map<Integer,List<ResultElement>> lgMap = new LinkedHashMap<Integer,List<ResultElement>>();
        PathQuery lgQuery = getLinkageGroupQuery(im.getModel(), reportObject.getId());
        try {
            ExportResultsIterator lgResult = executor.execute(lgQuery);
            while (lgResult.hasNext()) {
                List<ResultElement> row = lgResult.next();
                Integer id = (Integer) row.get(0).getField(); // 0:LinkageGroup.id
                lgMap.put(id, row);
            }
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data with lgQuery:", ex);
        }

        // bail if we have no linkage groups
        if (lgMap.size()==0) {
            throw new RuntimeException("No linkage groups returned for id="+reportObject.getId());
        }

        // getQTLQuery(): get the QTL records per linkage group
        Map<Integer, Map<Integer,List<ResultElement>>> lgQTLMap = new LinkedHashMap<Integer,Map<Integer,List<ResultElement>>>();
        for (Integer lgId : lgMap.keySet()) {
            Map<Integer,List<ResultElement>> qtls = new LinkedHashMap<Integer,List<ResultElement>>();
            PathQuery qtlQuery = getQTLQuery(im.getModel(), lgId, reportObject.getId());
            try {
                ExportResultsIterator qtlResult = executor.execute(qtlQuery);
                while (qtlResult.hasNext()) {
                    List<ResultElement> row = qtlResult.next();
                    Integer id = (Integer) row.get(0).getField(); // 0:QTL.id
                    qtls.put(id, row);
                }
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error retrieving data with qtlQuery:", ex);
            }
            if (qtls.size()>0) {
                lgQTLMap.put(lgId, qtls);
            }
        }

        // getLinkageGroupPositionQuery(): get LinkageGroupPosition records per marker per linkage group
        Map<Integer,Map<Integer,List<ResultElement>>> lgLinkageGroupPositionMap = new LinkedHashMap<Integer,Map<Integer,List<ResultElement>>>();
        for (Integer lgId : lgMap.keySet()) {
            Map<Integer,List<ResultElement>> linkageGroupPositions = new LinkedHashMap<Integer,List<ResultElement>>();
            PathQuery lgpQuery = getLinkageGroupPositionQuery(im.getModel(), lgId, reportObject.getId());
            try {
                ExportResultsIterator lgpResult = executor.execute(lgpQuery);
                while (lgpResult.hasNext()) {
                    List<ResultElement> row = lgpResult.next();
                    Integer id = (Integer) row.get(0).getField(); // 0:LinkageGroupPosition.id
                    linkageGroupPositions.put(id, row);
                }
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error retrieving data with lgpQuery:", ex);
            }
            if (linkageGroupPositions.size()>0) {
                lgLinkageGroupPositionMap.put(lgId, linkageGroupPositions);
            }
        }

        // END OF QUERIES; now form JSON data
        // NOTE: non-labeled array - order matters!!
        double maxLGLength = 0.0;
        List<Object> trackData = new LinkedList<Object>();
        for (Integer lgId : lgMap.keySet()) {
            // LINKAGE GROUP TRACK
            // the data
            double[] length = new double[2];
            length[0] = 0.0;
            List<ResultElement> lgRow = lgMap.get(lgId);
            String lgIdentifier = (String) lgRow.get(1).getField();  // 1:LinkageGroup.primaryIdentifier
            length[1] = (double) (Double) lgRow.get(2).getField();   // 2:LinkageGroup.length
            // determine max LG length for plotting purposes
            if (length[1]>maxLGLength) maxLGLength = length[1];
            // the track
            Map<String,Object> lgTrack = new LinkedHashMap<String,Object>();
            lgTrack.put("type", "box");
            // linkage group track data array
            List<Object> lgDataArray = new LinkedList<Object>();
            // the single data item
            Map<String,Object> lgData = new LinkedHashMap<String,Object>();
            lgData.put("id", lgIdentifier);
            lgData.put("key", lgId); // for linking
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
            if (lgLinkageGroupPositionMap.containsKey(lgId)) {
                Map<String,Object> markersTrack = new LinkedHashMap<String,Object>();
                markersTrack.put("type", "triangle");
                // markers track data array
                List<Object> markersDataArray = new LinkedList<Object>();
                Map<Integer,List<ResultElement>> linkageGroupPositions = lgLinkageGroupPositionMap.get(lgId);
                for (int lgpId : linkageGroupPositions.keySet()) {
                    List<ResultElement> lgpRow = linkageGroupPositions.get(lgpId);
                    String markerName = (String) lgpRow.get(1).getField(); // 1:LinkageGroupPosition.markerName
                    double position = (double) lgpRow.get(2).getField();   // 2:LinkageGroupPosition.position
                    // the track data
                    Map<String,Object> markerData = new LinkedHashMap<String,Object>();
                    markerData.put("id", markerName);
                    markerData.put("fill", "darkred");
                    markerData.put("outline", "black");
                    markerData.put("offset", position);
                    markersDataArray.add(markerData);
                }
                markersTrack.put("data", markersDataArray);
                trackData.add(markersTrack);
            }

            // QTLS TRACK
            if (lgQTLMap.containsKey(lgId)) {
                Map<String,Object> qtlsTrack = new LinkedHashMap<String,Object>();
                // QTLs track data array
                List<Object> qtlsDataArray = new LinkedList<Object>();
                Map<Integer,List<ResultElement>> qtls = lgQTLMap.get(lgId);
                for (Integer qtlId : qtls.keySet()) {
                    // the data
                    double[] span = new double[2];
                    List<ResultElement> qtlRow = qtls.get(qtlId);
                    String qtlIdentifier = (String) qtlRow.get(1).getField(); // 1:QTL.primaryIdentifier
                    span[0] = (double) (Double) qtlRow.get(2).getField();     // 2:QTL.start
                    span[1] = (double) (Double) qtlRow.get(3).getField();     // 3:QTL.end
                    // the track data
                    Map<String,Object> qtlData = new LinkedHashMap<String,Object>();
                    qtlData.put("id", qtlIdentifier); // canvasXpress needs it to be called "id"
                    qtlData.put("key", qtlId); // for linking
                    qtlData.put("fill", "yellow");
                    qtlData.put("outline", "black");
                    // QTL box positions = array of one pair
                    List<Object> qtlPositionsArray = new LinkedList<Object>();
                    qtlPositionsArray.add(span);
                    qtlData.put("data", qtlPositionsArray);
                    qtlsDataArray.add(qtlData);
                }
                qtlsTrack.put("type", "box");
                qtlsTrack.put("data", qtlsDataArray);
                trackData.add(qtlsTrack);
            }
        }

        // JSON array with all tracks
        Map<String,Object> tracks = new LinkedHashMap<String,Object>();
        tracks.put("tracks", trackData);

        // scalar var for plot x-axis
        request.setAttribute("maxLGLength", maxLGLength);

        // pass the JSON back to the request
        request.setAttribute("tracksJSON", new JSONObject(tracks).toString());
    }

    /**
     * Return a path query to retrieve linkage groups associated with this report.
     *
     * 0:LinkageGroup.id
     * 1:LinkageGroup.primaryIdentifier
     * 2:LinkageGroup.length
     *
     * @param model the model
     * @param reportId the id of the report object
     * @return the path query
     */
    abstract PathQuery getLinkageGroupQuery(Model model, int reportId);

    /**
     * Return a path query to retrieve markers associated with the given linkage group.
     *
     * 0:LinkageGroupPosition.id
     * 1:LinkageGroupPosition.markerName
     * 2:LinkageGroupPosition.position
     *
     * @param model the model
     * @param lgId  the linkage group id
     * @param reportId the id of the report object
     * @return the path query
     */
    abstract PathQuery getLinkageGroupPositionQuery(Model model, int lgId, int reportId);

    /**
     * Return a path query to retrieve QTLs associated with a given linkage group.
     *
     * 0:QTL.id
     * 1:QTL.primaryIdentifier
     * 2:QTL.start
     * 3:QTL.end
     *
     * @param model the model
     * @param lgId  the linkage group id
     * @param reportId the id of the report object
     * @return the path query
     */
    abstract PathQuery getQTLQuery(Model model, int lgId, int reportId);
}
