package org.ncgr.intermine.web;

/*
 * Copyright (C) 2021 NCGR
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;
import org.apache.struts.tiles.actions.TilesAction;

import org.intermine.api.InterMineAPI;
import org.intermine.api.profile.BagValue;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.query.ClobAccess;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.logic.session.SessionMethods;

import org.json.JSONObject;

/**
 * Class that generates ANNOTATE button data on a list of CDSes, Transcripts/MRNAs, or Proteins.
 *
 * @author Sam Hokin
 */
public class AnnotationController extends TilesAction {
    
    /**
     * {@inheritDoc}
     */
    @Override
    public ActionForward execute(ComponentContext context, ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        // standard stuff in every TilesAction
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();
        InterMineBag bag = (InterMineBag) request.getAttribute("bag");
        Model model = im.getModel();
        Profile profile = SessionMethods.getProfile(session);
        PathQueryExecutor executor = im.getPathQueryExecutor(profile);

        // check that we've got a list of CDSes, Transcripts, MRNAs, or Proteins
        String bagType = bag.getType();
        if (!bagType.equals("CDS") && !bagType.equals("Transcript") && !bagType.equals("MRNA") && !bagType.equals("Protein")) {
            String errorMessage = "ERROR: AnnotationController called on a bag of type "+bagType+".";
            request.setAttribute("errorMessage", errorMessage);
            throw new RuntimeException(errorMessage);
        }

        // for the bagType-dependent query
        PathQuery query = new PathQuery(model);
        String sequenceType = null;
        if (bagType.equals("CDS")) {
            // CDS
            sequenceType = "n";
            query.addView("CDS.primaryIdentifier");           // 0
            query.addView("CDS.sequence.residues");           // 1
            query.addView("CDS.gene.geneFamilyAssignments.geneFamily.identifier");  // 2
            query.addView("CDS.gene.geneFamilyAssignments.geneFamily.description"); // 3
            query.addOrderBy("CDS.gene.geneFamilyAssignments.geneFamily.identifier", OrderDirection.ASC);
            query.addOrderBy("CDS.primaryIdentifier", OrderDirection.ASC);
            query.addConstraint(Constraints.in("CDS", bag.getName()));
        } else if (bagType.equals("MRNA")) {
            // MRNA
            sequenceType = "n";
            query.addView("MRNA.primaryIdentifier");           // 0
            query.addView("MRNA.sequence.residues");           // 1
            query.addView("MRNA.gene.geneFamily.identifier");  // 2
            query.addView("MRNA.gene.geneFamily.description"); // 3
            query.addOrderBy("MRNA.gene.geneFamily.identifier", OrderDirection.ASC);
            query.addOrderBy("MRNA.primaryIdentifier", OrderDirection.ASC);
            query.addConstraint(Constraints.in("MRNA", bag.getName()));
        } else if (bagType.equals("Transcript")) {
            // Transcript
            sequenceType = "n";
            query.addView("Transcript.primaryIdentifier");           // 0
            query.addView("Transcript.sequence.residues");           // 1
            query.addView("Transcript.gene.geneFamily.identifier");  // 2
            query.addView("Transcript.gene.geneFamily.description"); // 3
            query.addOrderBy("Transcript.gene.geneFamily.identifier", OrderDirection.ASC);
            query.addOrderBy("Transcript.primaryIdentifier", OrderDirection.ASC);
            query.addConstraint(Constraints.in("Transcript", bag.getName()));
        } else if (bagType.equals("Protein")) {
            // Protein
            sequenceType = "p";
            query.addView("Protein.primaryIdentifier");      // 0
            query.addView("Protein.sequence.residues");      // 1
            query.addView("Protein.geneFamilyAssignments.geneFamily.identifier");  // 2
            query.addView("Protein.geneFamilyAssignments.geneFamily.description"); // 3
            query.addOrderBy("Protein.geneFamilyAssignments.geneFamily.identifier", OrderDirection.ASC);
            query.addOrderBy("Protein.primaryIdentifier", OrderDirection.ASC);
            query.addConstraint(Constraints.in("Protein", bag.getName()));
        }
        // verify the query
        List<String> verifyList = query.verifyQuery();
        if (!verifyList.isEmpty()) {
            String errorMessage = "ERROR: AnnotationController query invalid: "+verifyList;
            request.setAttribute("errorMessage", errorMessage);
            throw new RuntimeException(errorMessage);
        }

        // load results into a map of multi-FASTAs keyed by GeneFamily.identifier as well as a map of sequence count
        Map<String,String> multiFastaMap = new HashMap<>();
        Map<String,Integer> countMap = new HashMap<>();
        Map<String,String> geneFamilyDescriptionMap = new HashMap<>();
        String currentGeneFamilyIdentifier = "";
        String currentGeneFamilyDescription = "";
        String currentMultiFasta = "";
        int count = 0;
        ExportResultsIterator resultsIterator;
        try {
            resultsIterator = executor.execute(query);
        } catch (ObjectStoreException e) {
            String errorMessage = "Error retrieving query results:"+e.toString();
            request.setAttribute("errorMessage", errorMessage);
            throw new RuntimeException(errorMessage);
        }
        while (resultsIterator.hasNext()) {
            List<ResultElement> valueRow = resultsIterator.next();
            String primaryIdentifier = (String) valueRow.get(0).getField();
            ClobAccess clob = (ClobAccess) valueRow.get(1).getField();
            String geneFamilyIdentifier = (String) valueRow.get(2).getField();
            String geneFamilyDescription = (String) valueRow.get(3).getField();
            String residues = clob.toString();
            String fastaHeader = ">"+primaryIdentifier+" type="+bagType+";gene_family="+geneFamilyIdentifier;
            String fasta = fastaHeader+"\n"+residues+"\n";
            if (geneFamilyIdentifier.equals(currentGeneFamilyIdentifier)) {
                count++;
                currentMultiFasta += fasta;
            } else {
                if (currentGeneFamilyIdentifier.length()>0) {
                    countMap.put(currentGeneFamilyIdentifier, count);
                    multiFastaMap.put(currentGeneFamilyIdentifier, currentMultiFasta);
                    geneFamilyDescriptionMap.put(currentGeneFamilyIdentifier, currentGeneFamilyDescription);
                }
                count = 1;
                currentGeneFamilyIdentifier = geneFamilyIdentifier;
                currentGeneFamilyDescription = geneFamilyDescription;
                currentMultiFasta = fasta;
            }
        }
        // last one
        countMap.put(currentGeneFamilyIdentifier, count);
        multiFastaMap.put(currentGeneFamilyIdentifier, currentMultiFasta);
        geneFamilyDescriptionMap.put(currentGeneFamilyIdentifier, currentGeneFamilyDescription);

        // set return attributes
        request.setAttribute("sequenceType", sequenceType);
        request.setAttribute("countMap", countMap);
        request.setAttribute("multiFastaMap", multiFastaMap);
        request.setAttribute("geneFamilyDescriptionMap", geneFamilyDescriptionMap);

        // have to have a return
        return null;
    }
}
