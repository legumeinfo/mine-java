package org.ncgr.intermine.bio.web.displayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.intermine.api.InterMineAPI;
import org.intermine.api.profile.Profile;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.BioEntity;
import org.intermine.model.bio.OntologyTerm;
import org.intermine.model.bio.Organism;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;
import org.intermine.web.logic.session.SessionMethods;

/**
 * Builds datastructure from go parent id to go term id.  Includes evidence codes.
 * @author julie
 * @author sam
 */
public class GeneOntologyDisplayer extends ReportDisplayer
{
    /**
     * The names of ontology root terms.
     */
    public static final Set<String> NAMESPACES = new HashSet<String>();
    private static final Map<String, String> EVIDENCE_CODES = new HashMap<String, String>();
    private Map<String, Boolean> organismCache = new HashMap<String, Boolean>();

    /**
     * Construct with config and the InterMineAPI.
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public GeneOntologyDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    static {
        NAMESPACES.add("biological_process");
        NAMESPACES.add("molecular_function");
        NAMESPACES.add("cellular_component");

        EVIDENCE_CODES.put("EXP", "Inferred from Experiment");
        EVIDENCE_CODES.put("IDA", "Inferred from Direct Assay");
        EVIDENCE_CODES.put("IPI", "Inferred from Physical Interaction");
        EVIDENCE_CODES.put("IMP", "Inferred from Mutant Phenotype");
        EVIDENCE_CODES.put("IGI", "Inferred from Genetic Interaction");
        EVIDENCE_CODES.put("IEP", "Inferred from Expression Pattern");
        EVIDENCE_CODES.put("ISS", "Inferred from Sequence or Structural Similarity");
        EVIDENCE_CODES.put("ISO", "Inferred from Sequence Orthology");
        EVIDENCE_CODES.put("ISA", "Inferred from Sequence Alignment");
        EVIDENCE_CODES.put("ISM", "Inferred from Sequence Model");
        EVIDENCE_CODES.put("IGC", "Inferred from Genomic Context");
        EVIDENCE_CODES.put("RCA", "Inferred from Reviewed Computational Analysis");
        EVIDENCE_CODES.put("TAS", "Traceable Author Statement");
        EVIDENCE_CODES.put("NAS", "Non-traceable Author Statement");
        EVIDENCE_CODES.put("IC", "Inferred by Curator");
        EVIDENCE_CODES.put("ND", "No biological Data available");
        EVIDENCE_CODES.put("IEA", "Inferred from Electronic Annotation");
        EVIDENCE_CODES.put("NR", "Not Recorded ");
    }


    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {
        Profile profile = SessionMethods.getProfile(request.getSession());

        // noGoMessage
        boolean goLoadedForOrganism = true;

        // check whether GO annotation is loaded for this organism
        // if we can't work out organism just proceed with display
        String organismName = getOrganismName(reportObject);

        if (organismName != null) {
            goLoadedForOrganism = isGoLoadedForOrganism(organismName, profile);
        }

        if (!goLoadedForOrganism) {
            String noGoMessage = "No Gene Ontology annotation loaded for " + organismName;
            request.setAttribute("noGoMessage", noGoMessage);
        } else {
            Model model = im.getModel();
            PathQueryExecutor executor = im.getPathQueryExecutor(profile);

            InterMineObject object = reportObject.getObject();
            String primaryIdentifier = null;
            try {
                primaryIdentifier = (String) object.getFieldValue("primaryIdentifier");
            } catch (IllegalAccessException e) {
                return;
            }
            if (StringUtils.isEmpty(primaryIdentifier)) {
                return;
            }

            PathQuery query = buildQuery(model, new Integer(reportObject.getId()));
            // DEBUG
            System.out.println(query.toXml());
            //
            ExportResultsIterator result;
            try {
                result = executor.execute(query);
            } catch (ObjectStoreException e) {
                throw new RuntimeException(e);
            }

            Map<String, Map<OntologyTerm, Set<String>>> goTermsByNamespace = new HashMap<String, Map<OntologyTerm, Set<String>>>();
            while (result.hasNext()) {
                List<ResultElement> row = result.next();
                OntologyTerm term = (OntologyTerm) row.get(0).getObject();
                // skip evidence code
                // String code = (String) row.get(2).getField();
                addToNamespaceMap(goTermsByNamespace, term, "NR");
            }

            // If no terms in a particular category add the namespace only to put heading in JSP
            for (String namespace : NAMESPACES) {
                if (!goTermsByNamespace.containsKey(namespace)) {
                    goTermsByNamespace.put(namespace, null);
                }
            }
            request.setAttribute("goTerms", goTermsByNamespace);
            request.setAttribute("codes", EVIDENCE_CODES);
        }
    }

    private static void addToNamespaceMap(Map<String,Map<OntologyTerm,Set<String>>> goTermsByNamespace, OntologyTerm term, String evidenceCode) {
        String namespace = term.getNamespace();
        Map<OntologyTerm,Set<String>> termToEvidence = goTermsByNamespace.get(namespace);
        if (termToEvidence == null) {
            termToEvidence = new HashMap<OntologyTerm, Set<String>>();
            goTermsByNamespace.put(namespace, termToEvidence);
        }
        Set<String> codes = termToEvidence.get(term);
        if (codes == null) {
            codes = new HashSet<String>();
            termToEvidence.put(term, codes);
        }
        codes.add(evidenceCode);
    }

    private static PathQuery buildQuery(Model model, Integer geneId) {
        PathQuery q = new PathQuery(model);
        q.addViews("Gene.ontologyAnnotations.ontologyTerm.name");
        q.addOrderBy("Gene.ontologyAnnotations.ontologyTerm.name", OrderDirection.ASC);
        // terms have to be main ontology namespaces
        q.addConstraint(Constraints.oneOfValues("Gene.ontologyAnnotations.ontologyTerm.namespace", NAMESPACES));
        // gene from report page
        q.addConstraint(Constraints.eq("Gene.id", "" + geneId));
        return q;
    }

    private static String getOrganismName(ReportObject reportObject) {
        Organism organism = ((BioEntity) reportObject.getObject()).getOrganism();
        if (organism != null) {
            if (!StringUtils.isBlank(organism.getName())) {
                return organism.getName();
            } else if (organism.getTaxonId() != null) {
                return "" + organism.getTaxonId();
            }
        }
        return null;
    }

    private boolean isGoLoadedForOrganism(String organismField, Profile profile) {
        if (!organismCache.containsKey(organismField)) {
            PathQuery q = new PathQuery(im.getModel());
            q.addViews("Gene.ontologyAnnotations.ontologyTerm.name");
            if (StringUtils.isNumeric(organismField)) {
                q.addConstraint(Constraints.eq("Gene.organism.taxonId", organismField));
            } else {
                q.addConstraint(Constraints.eq("Gene.organism.name", organismField));
            }
            PathQueryExecutor executor = im.getPathQueryExecutor(profile);
            ExportResultsIterator result;
            try {
                result = executor.execute(q, 0, 1);
            } catch (ObjectStoreException e) {
                throw new RuntimeException(e);
            }
            organismCache.put(organismField, Boolean.valueOf(result.hasNext()));
        }
        return organismCache.get(organismField).booleanValue();
    }
}
