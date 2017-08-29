package org.ncgr.intermine.web;

/*
 * Copyright (C) 2002-2014 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;
import org.apache.struts.tiles.actions.TilesAction;

import org.intermine.api.InterMineAPI;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.BagValue;
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

/**
 * Class that generates data for a chart of genotypes for various mapping populations for the given bag of markers.
 *
 * @author Sam Hokin
 *
 */
public class MarkerGenotypesController extends TilesAction {
    
    protected static final Logger LOG = Logger.getLogger(MarkerGenotypesController.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public ActionForward execute(ComponentContext context, ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        // TIMING
        long start = System.currentTimeMillis();
        
        // initialization, same for any controller
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();
        Model model = im.getModel();
        Profile profile = SessionMethods.getProfile(session);
        PathQueryExecutor executor = im.getPathQueryExecutor(profile);

        // our bag of markers
        InterMineBag bag = (InterMineBag) request.getAttribute("bag");

        // TODO: see if these queries can be made more efficient

        // query linkage group and position for the markers
        // NOTE: this may not in general be legit since we may have multiple genetic maps!
        Map<String,Double> markerPositions = new LinkedHashMap<String,Double>();
        Map<String,Integer> markerLinkageGroups = new LinkedHashMap<String,Integer>();
        PathQuery lgQuery = new PathQuery(model);
        lgQuery.addViews(
                         "GeneticMarker.primaryIdentifier",
                         "GeneticMarker.linkageGroupPositions.position",
                         "GeneticMarker.linkageGroupPositions.linkageGroup.number"
                         );
        lgQuery.addConstraint(Constraints.in("GeneticMarker", bag.getName()));
        ExportResultsIterator lgResults = getResults(executor, lgQuery);
        while (lgResults.hasNext()) {
            List<ResultElement> row = lgResults.next();
            String marker = (String) row.get(0).getField();
            Double position = (Double) row.get(1).getField();
            Integer lg = (Integer) row.get(2).getField();
            markerPositions.put(marker, position);
            markerLinkageGroups.put(marker, lg);
        }

        // query the MappingPopulations for the bag of markers
        Map<String,Integer> mappingPopulations = new LinkedHashMap<String,Integer>();
        PathQuery mpQuery = new PathQuery(model);
        mpQuery.addViews(
                         "GeneticMarker.mappingPopulations.primaryIdentifier",
                         "GeneticMarker.mappingPopulations.id"
                         );
        mpQuery.addConstraint(Constraints.in("GeneticMarker", bag.getName()));
        mpQuery.addOrderBy("GeneticMarker.mappingPopulations.primaryIdentifier", OrderDirection.ASC);
        ExportResultsIterator mpResults = getResults(executor, mpQuery);
        while (mpResults.hasNext()) {
            List<ResultElement> row = mpResults.next();
            String mp = (String) row.get(0).getField();
            Integer id = (Integer) row.get(1).getField();
            mappingPopulations.put(mp, id);
        }

        // query the markers that belong to each mapping population (subset of all the markers in the bag)
        Map<String, Map<String,Integer>> mappingPopulationMarkers = new LinkedHashMap<String, Map<String,Integer>>();
        for (String mp : mappingPopulations.keySet()) {
            PathQuery markerQuery = new PathQuery(model);
            markerQuery.addViews(
                                 "GeneticMarker.primaryIdentifier",
                                 "GeneticMarker.id"
                                 );
            markerQuery.addConstraint(Constraints.in("GeneticMarker", bag.getName()));
            markerQuery.addConstraint(Constraints.eq("GeneticMarker.mappingPopulations.primaryIdentifier", mp));
            markerQuery.addOrderBy("GeneticMarker.primaryIdentifier", OrderDirection.ASC);
            ExportResultsIterator markerResults = getResults(executor, markerQuery);
            Map<String,Integer> markers = new LinkedHashMap<String,Integer>();
            while (markerResults.hasNext()) {
                List<ResultElement> row = markerResults.next();
                String marker = (String) row.get(0).getField();
                Integer id = (Integer) row.get(1).getField();
                markers.put(marker, id);
            }
            mappingPopulationMarkers.put(mp, markers);
        }

        // query the lines for each MappingPopulation and put in map of lists
        Map<String, Map<String,Integer>> mappingPopulationLines = new LinkedHashMap<String, Map<String,Integer>>();
        for (String mp : mappingPopulations.keySet()) {
            PathQuery lineQuery = new PathQuery(model);
            lineQuery.addViews(
                               "MappingPopulation.lines.primaryIdentifier",
                               "MappingPopulation.lines.id"
                               );
            lineQuery.addConstraint(Constraints.eq("MappingPopulation.primaryIdentifier", mp));
            lineQuery.addOrderBy("MappingPopulation.lines.primaryIdentifier", OrderDirection.ASC);
            ExportResultsIterator lineResults = getResults(executor, lineQuery);
            Map<String,Integer> lines = new LinkedHashMap<String,Integer>();
            while (lineResults.hasNext()) {
                List<ResultElement> row = lineResults.next();
                String line = (String) row.get(0).getField();
                Integer id = (Integer) row.get(1).getField();
                lines.put(line, id);
            }
            mappingPopulationLines.put(mp, lines);
        }

        // query the values for each marker and line; be sure to order lines in same way as above.
        Map<String, Map<String,char[]>> mappingPopulationValues = new LinkedHashMap<String, Map<String,char[]>>();
        for (String mp : mappingPopulations.keySet()) {
            Map<String,Integer> markers = mappingPopulationMarkers.get(mp);
            Map<String,Integer> lines = mappingPopulationLines.get(mp);
            Map<String,char[]> markersValues = new LinkedHashMap<String,char[]>();
            for (String marker : markers.keySet()) {
                char[] values = new char[lines.size()];
                PathQuery valueQuery = new PathQuery(model);
                valueQuery.addViews(
                                    "GenotypeValue.value",
                                    "GenotypeValue.line.primaryIdentifier" // for ordering
                                    );
                valueQuery.addConstraint(Constraints.eq("GenotypeValue.marker.primaryIdentifier", marker));
                valueQuery.addConstraint(Constraints.eq("GenotypeValue.line.mappingPopulation.primaryIdentifier", mp));
                valueQuery.addOrderBy("GenotypeValue.line.primaryIdentifier", OrderDirection.ASC);
                ExportResultsIterator valueResults = getResults(executor, valueQuery);
                int i = 0;
                while (valueResults.hasNext()) {
                    List<ResultElement> row = valueResults.next();
                    Character val = new Character(((String) row.get(0).getField()).charAt(0));
                    values[i++] = val;
                }
                markersValues.put(marker, values);
            }
            mappingPopulationValues.put(mp, markersValues);
        }
        
        // output to caller
        request.setAttribute("markerLinkageGroups", markerLinkageGroups);
        request.setAttribute("markerPositions", markerPositions);
        request.setAttribute("mappingPopulations", mappingPopulations);
        request.setAttribute("mappingPopulationLines", mappingPopulationLines);
        request.setAttribute("mappingPopulationMarkers", mappingPopulationMarkers);
        request.setAttribute("mappingPopulationValues", mappingPopulationValues);

        // TIMING
        long end = System.currentTimeMillis();
        LOG.info("DURATION: "+(end-start)+" ms.");

        return null;
    }

    /**
     * Execute a query, returning an ExportResultsIterator
     *
     * @param executor the PathQueryExecutor
     * @param query the PathQuery
     * @return the ExportResultsIterator
     */
    ExportResultsIterator getResults(PathQueryExecutor executor, PathQuery query) {
        ExportResultsIterator result;
        try {
            result = executor.execute(query);
        } catch (ObjectStoreException e) {
            LOG.error("Error retrieving query results: "+e.getMessage());
            throw new RuntimeException("Error retrieving query results.", e);
        }
        return result;
    }

}
