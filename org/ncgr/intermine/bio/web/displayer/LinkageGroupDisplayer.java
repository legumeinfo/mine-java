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
 * Display a diagram for a linkage group with markers and QTLs on a LinkageGroup report page.
 *
 * NOTE: markers are shown by name from their LinkageGroupPosition. Many of them are not in the mine as GeneticMarker.
 * This displayer does not query ANY genomic data.
 *
 * @author Sam Hokin
 */
public class LinkageGroupDisplayer extends GeneticDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public LinkageGroupDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    /**
     * Create a path query to retrieve linkage groups associated with a given genetic map, QTL or even a single linkage group Id.
     *
     * 0:LinkageGroup.id
     * 1:LinkageGroup.primaryIdentifier
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
                       "LinkageGroup.primaryIdentifier",
                       "LinkageGroup.length");
        query.addConstraint(Constraints.eq("LinkageGroup.id", String.valueOf(reportId)));
        query.addOrderBy("LinkageGroup.number", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve markers associated with this LG/QTLStudy/QTL for a given linkage group.
     * If querying markers for a QTL or QTLStudy, provide a Set of marker names; for a full LG leave it empty.
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
     * Create a path query to retrieve QTLs associated with a given linkage group.
     *
     * 0:QTL.id
     * 1:QTL.primaryIdentifier
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
                       "QTL.primaryIdentifier",   // 1
                       "QTL.start",               // 2
                       "QTL.end");                // 3
        query.addConstraint(Constraints.eq("QTL.linkageGroup.id", String.valueOf(lgId)));
        query.addOrderBy("QTL.start", OrderDirection.ASC);
        return query;
    }
}
