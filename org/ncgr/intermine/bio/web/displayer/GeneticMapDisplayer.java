package org.ncgr.intermine.bio.web.displayer;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.intermine.api.InterMineAPI;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.pathquery.PathConstraintMultiValue;
import org.intermine.web.logic.config.ReportDisplayerConfig;

/**
 * Display a diagram for a genetic map with stacked linkage groups, markers and QTLs on a GeneticMap report page.
 *
 * NOTE: markers are shown by name from their LinkageGroupPosition. Many of them are not in the mine as GeneticMarker.
 * This displayer does not query ANY genomic data.
 *
 * @author Sam Hokin
 */
public class GeneticMapDisplayer extends GeneticDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GeneticMapDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    /**
     * Create a path query to retrieve linkage groups associated with this genetic map.
     *
     * 0:LinkageGroup.id
     * 1:LinkageGroup.identifier
     * 2:LinkageGroup.length
     *
     * @param model the model
     * @param gmPI  the genetic map IM id
     * @return the path query
     */
    @Override
    PathQuery getLinkageGroupQuery(Model model, int reportId) {
        PathQuery query = new PathQuery(model);
        query.addViews("LinkageGroup.id",
                       "LinkageGroup.identifier",
                       "LinkageGroup.length");
        query.addConstraint(Constraints.eq("LinkageGroup.geneticMap.id", String.valueOf(reportId)));
        query.addOrderBy("LinkageGroup.number", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve markers placed on linkage groups on this genetic map.
     *
     * 0:LinkageGroupPosition.id
     * 1:LinkageGroupPosition.markerName
     * 2:LinkageGroupPosition.position
     *
     * @param model the model
     * @param lgId  the linkage group id
     * @param markerNames a Set of marker names, null if querying all markers on the linkage group
     * @return the path query
     */
    @Override
    PathQuery getLinkageGroupPositionQuery(Model model, int lgId, int reportId) {
        PathQuery query = new PathQuery(model);
        query.addViews("LinkageGroupPosition.id",         // 0
                       "LinkageGroupPosition.markerName", // 1
                       "LinkageGroupPosition.position");  // 2
        query.addConstraint(Constraints.eq("LinkageGroupPosition.linkageGroup.id", String.valueOf(lgId)));
        query.addOrderBy("LinkageGroupPosition.position", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve QTLs placed on linkage groups on this genetic map.
     *
     * 0:QTL.id
     * 1:QTL.identifier
     * 2:QTL.start
     * 3:QTL.end
     *
     * @param model the model
     * @param lgId  the linkage group id
     * @param reportId the reportId for a QTL or QTLStudy query
     * @return the path query
     */
    @Override
    PathQuery getQTLQuery(Model model, int lgId, int reportId) {
        PathQuery query = new PathQuery(model);
        query.addViews("QTL.id",                  // 0
                       "QTL.identifier",          // 1
                       "QTL.start",               // 2
                       "QTL.end");                // 3
        query.addConstraint(Constraints.eq("QTL.linkageGroup.id", String.valueOf(lgId)));
        query.addOrderBy("QTL.start", OrderDirection.ASC);
        return query;
    }
}
