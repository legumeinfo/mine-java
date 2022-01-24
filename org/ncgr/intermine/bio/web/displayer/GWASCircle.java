package org.ncgr.intermine.bio.web.displayer;

import java.util.List;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;

import org.intermine.api.InterMineAPI;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;

import org.intermine.model.InterMineObject;

import org.intermine.objectstore.ObjectStoreException;

import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;

import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

/**
 * Generates data for a CanvasXpress circle plot of p-values versus marker position.
 *
 * @author Sam Hokin
 */
public class GWASCircle extends ReportDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GWASCircle(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        InterMineObject gwas = reportObject.getObject();
        String gwasIdentifier;
        try {
            gwasIdentifier = (String) gwas.getFieldValue("primaryIdentifier");
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error getting primaryIdentifier.", ex);
        }

        PathQueryExecutor executor = im.getPathQueryExecutor();

        // get the markers and traits from the GWASResults
        List<String> markerList = new LinkedList<>();
        List<String> chromosomeList = new LinkedList<>();
        List<String> traitList = new LinkedList<>();
        List<Integer> positionList = new LinkedList<>();
        List<Double> log10pList = new LinkedList<>();
        // GWASResult.marker.primaryIdentifier,            // 0
        // GWASResult.marker.chromosome.primaryIdentifier, // 1
        // GWASResult.marker.chromosome.length,            // 2
        // GWASResult.marker.chromosomeLocation.start,     // 3
        // GWASResult.marker.chromosomeLocation.end,       // 4
        // GWASResult.trait.primaryIdentifier,         // 5
        // GWASResult.pValue                               // 6
        PathQuery query = new PathQuery(im.getModel());
        query.addViews(
                       "GWASResult.marker.primaryIdentifier",            // 0
                       "GWASResult.marker.chromosome.primaryIdentifier", // 1
                       "GWASResult.marker.chromosome.length",            // 2
                       "GWASResult.marker.chromosomeLocation.start",     // 3
                       "GWASResult.marker.chromosomeLocation.end",       // 4
                       "GWASResult.trait.primaryIdentifier",         // 5
                       "GWASResult.pValue"                               // 6
                       );
        query.addConstraint(Constraints.eq("GWASResult.study.primaryIdentifier", gwasIdentifier));
        query.addOrderBy("GWASResult.marker.chromosome.primaryIdentifier", OrderDirection.ASC);
        query.addOrderBy("GWASResult.marker.primaryIdentifier", OrderDirection.ASC);
        ExportResultsIterator results;
        try {
            results = executor.execute(query);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        while (results.hasNext()) {
            List<ResultElement> row = results.next();
            String markerIdentifier = (String) row.get(0).getField();
            String chromosomeIdentifier = (String) row.get(1).getField();
            Integer chromosomeLength = (Integer) row.get(2).getField();
            Integer markerStart = (Integer) row.get(3).getField();
            Integer markerEnd = (Integer) row.get(4).getField();
            String traitIdentifier = (String) row.get(5).getField();
            Double pValue = (Double) row.get(6).getField();
            // derived quantities
            int position = (markerStart+markerEnd)/2;
            Double log10p = -Math.log10(pValue);
            // add to lists
            markerList.add(markerIdentifier);
            chromosomeList.add(chromosomeIdentifier);
            traitList.add(traitIdentifier);
            positionList.add(position);
            log10pList.add(log10p);
        }

        // send the data on its way
        request.setAttribute("markerList", markerList);
        request.setAttribute("chromosomeList", chromosomeList);
        request.setAttribute("traitList", traitList);
        request.setAttribute("positionList", positionList);
        request.setAttribute("log10pList", log10pList);
    }
}
