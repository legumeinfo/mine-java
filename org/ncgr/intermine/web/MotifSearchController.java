package org.ncgr.intermine.web;

/*
 * Copyright (C) 2002-2016 FlyMine, NCGR
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.text.DecimalFormat;

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
import org.intermine.api.profile.InterMineBag;
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

import org.globus.gram.GramJob;

import org.biojava.nbio.alignment.Alignments;
import org.biojava.nbio.alignment.FractionalIdentityScorer;
import org.biojava.nbio.alignment.FractionalIdentityInProfileScorer;
import org.biojava.nbio.alignment.SimpleGapPenalty;
import org.biojava.nbio.alignment.NeedlemanWunsch;
import org.biojava.nbio.alignment.SmithWaterman;
import org.biojava.nbio.alignment.routines.AnchoredPairwiseSequenceAligner;
import org.biojava.nbio.alignment.routines.GuanUberbacher;
import org.biojava.nbio.alignment.template.AbstractMatrixAligner;
import org.biojava.nbio.alignment.template.GapPenalty;
import org.biojava.nbio.core.alignment.matrices.SubstitutionMatrixHelper;
import org.biojava.nbio.core.alignment.template.AlignedSequence;
import org.biojava.nbio.core.alignment.template.SequencePair;
import org.biojava.nbio.core.alignment.template.SubstitutionMatrix;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.io.FastaWriterHelper;
import org.biojava.nbio.core.sequence.template.Sequence;
import org.biojava.nbio.core.util.ConcurrencyTools;

import edu.sdsc.nbcr.opal.AppServiceLocator;
import edu.sdsc.nbcr.opal.AppServicePortType;
import edu.sdsc.nbcr.opal.types.AppMetadataType;
import edu.sdsc.nbcr.opal.types.AppMetadataInputType;
import edu.sdsc.nbcr.opal.types.InputFileType;
import edu.sdsc.nbcr.opal.types.OutputFileType;
import edu.sdsc.nbcr.opal.types.JobInputType;
import edu.sdsc.nbcr.opal.types.JobOutputType;
import edu.sdsc.nbcr.opal.types.JobStatisticsType;
import edu.sdsc.nbcr.opal.types.JobSubOutputType;
import edu.sdsc.nbcr.opal.types.StatusOutputType;
import edu.sdsc.nbcr.opal.types.SystemInfoType;
import edu.sdsc.nbcr.opal.types.SystemInfoInputType;

import org.ncgr.opal.BlastClient;
import org.ncgr.opal.SeqlogoClient;

import org.ncgr.blast.BlastOutput;
import org.ncgr.blast.BlastOutputIterations;
import org.ncgr.blast.BlastUtils;
import org.ncgr.blast.Hit;
import org.ncgr.blast.HitHsps;
import org.ncgr.blast.Hsp;
import org.ncgr.blast.Iteration;
import org.ncgr.blast.SequenceHit;
import org.ncgr.blast.SequenceHits;

/**
 * Run NCBI blastn on a bag of sequences, typically upstream intergenic regions, using methods provided in org.ncgr.blast and org.ncgr.opal, to find shared motifs.
 * Motifs will be displayed along with participating sequences on the calling widget.
 *
 * @author Sam Hokin
 */
public class MotifSearchController extends TilesAction {

    static final Logger LOG = Logger.getLogger(MotifSearchController.class);
    
    // input restrictions
    static final int MIN_SEQUENCES = 3;     // the minimum number of input sequences needed to run this analysis
    static final int MAX_DATA = 1000000;    // the maximum total amount of data allowed for this analysis

    // job control parameters
    static final int MAX_WAIT_SECONDS = 60; // max time to wait for blast jobs to finish
    static final int WAIT_SECONDS = 1;      // number of seconds to wait between each job status check

    // output filtering
    static int MAX_MOTIF_LENGTH = 27;       // toss motifs greater than this length
    static int MAX_MOTIF_COUNT = 100;       // return no more than this many motifs

    // BioJava alignment parameters
    static final double MAX_DISTANCE = 0.2;  // maximum distance of a motif from top-scoring motif to be used in sequence logo
    static final int GOP = 10;               // gap open penalty for alignments
    static final int GEP = 1;                // gap extension penalty for alignments
    static String ALIGNER = "SmithWaterman"; // AnchoredPairwiseSequenceAligner, GuanUberbacher, NeedlemanWunsch, SmithWaterman

    // Opal2 stuff
    static final String BLAST_SERVICE_URL = "http://intermine.ncgr.org/opal2/services/blast";
    static final String SEQLOGO_SERVICE_URL = "http://intermine.ncgr.org/opal2/services/seqlogo";
    static final String LOGO_FILE = "alignment.png";

    static DecimalFormat dec = new DecimalFormat("0.0000");
    static DecimalFormat rnd = new DecimalFormat("+00;-00");

    // alignment stuff
    static GapPenalty gapPenalty = new SimpleGapPenalty(GOP, GEP);
    static SubstitutionMatrix<NucleotideCompound> subMatrix = SubstitutionMatrixHelper.getNuc4_4();
    
    /**
     * {@inheritDoc}
     */
    @Override
    public ActionForward execute(ComponentContext context, ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        // standard stuff that's in any controller
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();
        InterMineBag bag = (InterMineBag) request.getAttribute("bag");
        Model model = im.getModel();
        Profile profile = SessionMethods.getProfile(session);
        PathQueryExecutor executor = im.getPathQueryExecutor(profile);

        // check that we've got a list of the desired feature types
        String featureType = bag.getType();
        if (!featureType.equals("GeneFlankingRegion")) {
            request.setAttribute("errorMessage", "---"); // flag to not show motif search results at all
            return null;
        }

        // get the feature count
        int featureCount = 0;
        try {
            featureCount = bag.getSize();
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error getting bag size.", ex);
        }

        // bail if less than minimum number of  sequences
        if (featureCount<MIN_SEQUENCES) {
            request.setAttribute("errorMessage", "---"); // flag to not show motif search results at all
            return null;
        }
        
        // build the map of FASTA sequences
        PathQuery bagQuery = queryBag(model, bag);
        ExportResultsIterator bagResult;
        try {
            bagResult = executor.execute(bagQuery);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
        TreeMap<String,String> sequenceMap = new TreeMap<String,String>();
        int dataSize = 0;
        while (bagResult.hasNext()) {
            List<ResultElement> row = bagResult.next();
            String primaryIdentifier = (String) row.get(0).getField();
            Integer length = (Integer) row.get(1).getField();
            String residues = (row.get(2).getField()).toString();
            sequenceMap.put(primaryIdentifier, residues);
            dataSize += length.intValue();
        }

        // bail if we've got too much data
        if (dataSize>MAX_DATA) {
            request.setAttribute("errorMessage", "There is too much sequence data for motif search. Maximum data is "+MAX_DATA+"nt total.");
            return null;
        }

        // track time for entire job
        long blastStart = System.currentTimeMillis();
        
        // load the query IDs and job IDs in a map so we can go through them to see when all are done
        Map<String,String> jobMap = new LinkedHashMap<String,String>();
        
        // we'll add the found hits to this map of SequenceHits
        TreeMap<String,SequenceHits> seqHitsMap = new TreeMap<String,SequenceHits>();
        
        try {
            
            // the BlastClient submits the BLAST jobs to the Opal2 server
            BlastClient bc = new BlastClient(BLAST_SERVICE_URL);
            
            // loop through each sequence as query against the remaining as subject and submit blast job
            for (String queryID: sequenceMap.keySet()) {
                
                // create the query data
                String queryFasta = ">"+queryID+"\n"+sequenceMap.get(queryID)+"\n";
                byte[] queryData = queryFasta.getBytes();
            
                // create the subject data = all sequences but the query sequence
                LinkedHashMap<String,String> subjectMap = new LinkedHashMap<String,String>(sequenceMap);
                subjectMap.remove(queryID);
                String subjectFasta = "";
                for (String subjectId : subjectMap.keySet()) {
                    subjectFasta +=  ">"+subjectId+"\n"+subjectMap.get(subjectId)+"\n";
                }
                byte[] subjectData = subjectFasta.getBytes();
            
                // now run blastn on the query and subject with parameters set in Opal2 caller
                JobSubOutputType jobOutput = bc.launchJob(subjectData, queryData);
                StatusOutputType status = jobOutput.getStatus();
                String jobID = jobOutput.getJobID();
                jobMap.put(jobID,queryID);
                LOG.info("Submitted BlastClient job for query sequence:"+queryID+"; jobID="+jobID);
            }
        
            // poll all the jobs until they're all finished
            int maxWaits = MAX_WAIT_SECONDS/WAIT_SECONDS;
            boolean finished = false;
            int waits = 0;
            while (!finished && waits<maxWaits) {
                TimeUnit.SECONDS.sleep(WAIT_SECONDS);
                waits++;
                finished = true; // initialize, will be false if a job not finished
                for (String jobID : jobMap.keySet()) {
                    StatusOutputType status = bc.queryStatus(jobID);
                    int code = status.getCode();
                    finished = finished && (code==GramJob.STATUS_DONE || code==GramJob.STATUS_FAILED || code==GramJob.STATUS_SUSPENDED || code==GramJob.STATUS_UNSUBMITTED);
                    if (!finished) break;
                }
            }
        
            // bail if took too long
            if (!finished) {
                request.setAttribute("errorMessage", "Submitted BLAST jobs took too long. Submit fewer sequences or increase MAX_WAIT_SECONDS in MotifSearchController.java.");
                return null;
            }
        
            // now plow through the jobs and build the SequenceHits set
            for (String jobID : jobMap.keySet()) {
                String queryID = jobMap.get(jobID);
                StatusOutputType status = bc.queryStatus(jobID);
                // get the BlastObject from the resulting XML file and add the results to the SequenceHits map
                if (status.getCode()==GramJob.STATUS_DONE) {
                    URL url = new URL(status.getBaseURL()+"/"+BlastClient.OUTPUT_FILENAME);
                    BlastOutput blastOutput = BlastUtils.getBlastOutput(url);
                    BlastOutputIterations iterations = blastOutput.getBlastOutputIterations();
                    if (iterations!=null) {
                        List<Iteration> iterationList = iterations.getIteration();
                        if (iterationList!=null) {
                            for (Iteration iteration : iterationList) {
                                if (iteration.getIterationMessage()==null) {
                                    List<Hit> hitList = iteration.getIterationHits().getHit();
                                    for (Hit hit : hitList) {
                                        String hitID = hit.getHitDef();
                                        HitHsps hsps = hit.getHitHsps();
                                        if (hsps!=null) {
                                            List<Hsp> hspList = hsps.getHsp();
                                            if (hspList!=null) {
                                                for (Hsp hsp : hspList) {
                                                    SequenceHit seqHit = new SequenceHit(queryID, hitID, hsp);
                                                    // cull motifs based on their size and content
                                                    boolean keep = true;
                                                    keep = keep && (seqHit.sequence.contains("C") || seqHit.sequence.contains("G")); // too many ATATAT common strings
                                                    keep = keep && seqHit.sequence.length()<=MAX_MOTIF_LENGTH; // long motifs aren't biologically interesting
                                                    if (keep) {
                                                        if (seqHitsMap.containsKey(seqHit.sequence)) {
                                                            SequenceHits seqHits = seqHitsMap.get(seqHit.sequence);
                                                            seqHits.addSequenceHit(seqHit);
                                                        } else {
                                                            SequenceHits seqHits = new SequenceHits(seqHit);
                                                            seqHitsMap.put(seqHit.sequence, seqHits);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // bail; something went wrong on this blast run
                    request.setAttribute("errorMessage", status.getMessage());
                    return null;
                }

            } // each query/job

            // finish timing
            long blastEnd = System.currentTimeMillis();

            // collect the SequenceHits in a set sorted by score
            TreeSet<SequenceHits> seqHitsSet = new TreeSet<SequenceHits>(seqHitsMap.values());
            
            // now process the hits
            int count = 0;
            boolean first = true;
            DNASequence topMotif = null;
            List<DNASequence> logoMotifs = new ArrayList<DNASequence>();
            List<Object> jsonList = new ArrayList<Object>();
            for (SequenceHits seqHits : seqHitsSet.descendingSet()) {

                // do analysis for sequence logo
                boolean addedToLogo = false;
                if (first) {
                    first = false;
                    // save the top motif for pairwise alignments
                    topMotif = new DNASequence(seqHits.sequence);
                    logoMotifs.add(topMotif);
                    addedToLogo = true;
                } else {
                    // do a pairwise alignment with topMotif
                    DNASequence thisMotif = new DNASequence(seqHits.sequence);
                    AbstractMatrixAligner<DNASequence,NucleotideCompound> aligner = null;
                    if (ALIGNER.equals("AnchoredPairwiseSequenceAligner")) {
                        aligner = new AnchoredPairwiseSequenceAligner<DNASequence,NucleotideCompound>(thisMotif, topMotif, gapPenalty, subMatrix);
                    } else if (ALIGNER.equals("GuanUberbacher")) {
                        aligner = new GuanUberbacher<DNASequence,NucleotideCompound>(thisMotif, topMotif, gapPenalty, subMatrix);
                    } else if (ALIGNER.equals("NeedlemanWunsch")) {
                        aligner = new NeedlemanWunsch<DNASequence,NucleotideCompound>(thisMotif, topMotif, gapPenalty, subMatrix);
                    } else if (ALIGNER.equals("SmithWaterman")) {
                        aligner = new SmithWaterman<DNASequence,NucleotideCompound>(thisMotif, topMotif, gapPenalty, subMatrix);
                    } else {
                        System.err.println("ERROR: ALIGNER must be one of AnchoredPairwiseSequenceAligner, GuanUberbacher, NeedlemanWunsch, SmithWaterman");
                        System.exit(1);
                    }
                    double score = aligner.getScore();
                    double distance = aligner.getDistance();
                    double similarity = aligner.getSimilarity();
                    if (distance<MAX_DISTANCE) {
                        logoMotifs.add(thisMotif);
                        addedToLogo = true;
                    }
                }
                
                // add this motif and its containing features to the JSON data list; logo motifs are always included regardless of position in list
                if (count++<MAX_MOTIF_COUNT || addedToLogo) {
                    Map<String,Object> seqHitsData = new LinkedHashMap<String,Object>();
                    if (addedToLogo) {
                        seqHitsData.put("sequence", seqHits.sequence+"*");
                    } else {
                        seqHitsData.put("sequence", seqHits.sequence);
                    }
                    seqHitsData.put("length", seqHits.sequence.length());
                    seqHitsData.put("score", seqHits.score);
                    seqHitsData.put("num", seqHits.uniqueIDs.size());
                    seqHitsData.put("regions", new ArrayList(seqHits.uniqueHits));
                    seqHitsData.put("ids", new ArrayList(seqHits.uniqueIDs));
                    jsonList.add(seqHitsData);
                }

            }
            LOG.info(logoMotifs.size()+" motifs collected for sequence logo.");
            
            // do multi-sequence alignment of the logo list and submit seqlogo job
            String logoURL = null;
            if (logoMotifs.size()>1) {
                Object[] settings = new Object[3];
                settings[0] = gapPenalty;
                settings[1] = Alignments.PairwiseSequenceScorerType.GLOBAL_IDENTITIES;
                settings[2] = Alignments.ProfileProfileAlignerType.GLOBAL;
                org.biojava.nbio.core.alignment.template.Profile<DNASequence,NucleotideCompound> alignmentProfile = Alignments.getMultipleSequenceAlignment(logoMotifs, settings);
                String logoFasta = "";
                for (AlignedSequence aseq : alignmentProfile) {
                    logoFasta += ">"+aseq.getOriginalSequence().getSequenceAsString()+"\n";
                    logoFasta += aseq.getSequenceAsString()+"\n";
                }
                // submit seqlogo job and get image URL
                byte[] fastaData = logoFasta.getBytes();
                SeqlogoClient sc = new SeqlogoClient(SEQLOGO_SERVICE_URL);
                JobSubOutputType subOut = sc.launchJob(fastaData, null);
                String jobID = subOut.getJobID();
                LOG.info("Submitted SeqlogoClient job; jobID="+jobID);
                // wait for completion
                finished = false;
                waits = 0;
                while (!finished && waits<maxWaits) {
                    TimeUnit.SECONDS.sleep(WAIT_SECONDS);
                    waits++;
                    StatusOutputType status = sc.queryStatus(jobID);
                    int code = status.getCode();
                    finished = code==GramJob.STATUS_DONE || code==GramJob.STATUS_FAILED || code==GramJob.STATUS_SUSPENDED || code==GramJob.STATUS_UNSUBMITTED;
                }
                if (finished) logoURL = sc.queryStatus(jobID).getBaseURL()+"/"+LOGO_FILE;
            }
            
            // create the JSON
            Map<String,Object> jsonMap = new LinkedHashMap<String,Object>();
            jsonMap.put("data", jsonList);
            JSONObject jo = new JSONObject(jsonMap);
            
            // ---------------------------
            // SET HTTP REQUEST ATTRIBUTES
            // ---------------------------

            request.setAttribute("MAX_MOTIF_LENGTH", MAX_MOTIF_LENGTH);
            request.setAttribute("MAX_MOTIF_COUNT", MAX_MOTIF_COUNT);
            request.setAttribute("MAX_DISTANCE", MAX_DISTANCE);
            request.setAttribute("GOP", GOP);
            request.setAttribute("GEP", GEP);
            request.setAttribute("ALIGNER", ALIGNER);
            request.setAttribute("featureType", featureType);
            request.setAttribute("featureCount", featureCount);
            request.setAttribute("blastTime", blastEnd-blastStart);
            request.setAttribute("logoMotifsCount", logoMotifs.size());
            if (logoURL!=null) request.setAttribute("logoURL", logoURL);

            request.setAttribute("seqHitsJSON", jo.toString());
            
            // DONE!
            return null;

        } catch (Exception ex) {
            request.setAttribute("errorMessage", ex.toString());
            return null;
        } finally {
            ConcurrencyTools.shutdown();  
        }            

    }

    /**
     * Create a path query to retrieve identifiers, lengths and sequences from a bag
     *
     * @param model the model
     * @param bag   the bag of query linkage groups
     * @return the path query
     */
    private PathQuery queryBag(Model model, InterMineBag bag) {
        PathQuery query = new PathQuery(model);
        query.addViews("SequenceFeature.primaryIdentifier",
                       "SequenceFeature.length",
                       "SequenceFeature.sequence.residues"
                       );
        query.addConstraint(Constraints.in("SequenceFeature", bag.getName()));
        query.addOrderBy("SequenceFeature.primaryIdentifier", OrderDirection.ASC);
        return query;
    }

}
