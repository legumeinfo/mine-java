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
 * Display linkage groups for a QTLStudy along with its QTLs and associated markers.
 *
 * NOTE: markers are shown by name from their LinkageGroupPosition. Many of them are not in the mine as GeneticMarker.
 * This displayer does not query ANY genomic data.
 *
 * @author Sam Hokin
 */
public class QTLStudyDisplayer extends GeneticDisplayer {

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public QTLStudyDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    /**
     * Return a path query to retrieve the linkage groups associated with this QTLStudy.
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
        query.addViews("QTLStudy.qtls.linkageGroup.id",
                       "QTLStudy.qtls.linkageGroup.primaryIdentifier",
                       "QTLStudy.qtls.linkageGroup.length");
        query.addConstraint(Constraints.eq("QTLStudy.id", String.valueOf(reportId)));
        query.addOrderBy("QTLStudy.qtls.linkageGroup.number", OrderDirection.ASC);
        return query;
    }

    /**
     * Return a path query to retrieve the QTLs in this QTLStudy on the given LinkageGroup.
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
        query.addViews("QTLStudy.qtls.id",                  // 0
                       "QTLStudy.qtls.primaryIdentifier",   // 1
                       "QTLStudy.qtls.start",               // 2
                       "QTLStudy.qtls.end");                // 3
        query.addConstraint(Constraints.eq("QTLStudy.id", String.valueOf(reportId)));
        query.addConstraint(Constraints.eq("QTLStudy.qtls.linkageGroup.id", String.valueOf(lgId)));
        query.addOrderBy("QTLStudy.qtls.start", OrderDirection.ASC);
        return query;
    }

    /**
     * Return a path query to retrieve LinkageGroupPosition records for markers associated with the QTLs in this study on the given linkage group.
     * First one has to split the |-delimited QTL.markerNames into a Set and then constrain the query on that Set.
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
        // get the QTL.markerNames into a Set
        Set<String> markerNameSet = new HashSet<>();
        PathQuery qtlQuery = new PathQuery(model);
        qtlQuery.addViews("QTL.markerNames");  // 0
        qtlQuery.addConstraint(Constraints.eq("QTL.qtlStudy.id", String.valueOf(reportId)));
        qtlQuery.addConstraint(Constraints.eq("QTL.linkageGroup.id", String.valueOf(lgId)));
        try {
            ExportResultsIterator qtlResult = executor.execute(qtlQuery);
            while (qtlResult.hasNext()) {
                List<ResultElement> row = qtlResult.next();
                String markerNames = (String) row.get(0).getField(); // 0:QTL.markerNames
                for (String markerName : markerNames.split("\\|")) {
                    markerNameSet.add(markerName);
                }
            }
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving QTL.markerNames:", ex);
        }
        // constrain the query to have marker from markerNames located on the QTL's linkage group
        PathQuery query = new PathQuery(model);
        query.addViews("LinkageGroupPosition.id",         // 0
                       "LinkageGroupPosition.markerName", // 1
                       "LinkageGroupPosition.position");  // 2
        query.addConstraint(Constraints.eq("LinkageGroupPosition.linkageGroup.id", String.valueOf(lgId)));
        query.addConstraint(new PathConstraintMultiValue("LinkageGroupPosition.markerName", ConstraintOp.ONE_OF, markerNameSet));
        query.addOrderBy("LinkageGroupPosition.position", OrderDirection.ASC);
        return query;
    }
}
