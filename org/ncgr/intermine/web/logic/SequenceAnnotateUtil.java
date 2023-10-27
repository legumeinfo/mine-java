package org.ncgr.intermine.web.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.AccessionID;
import org.biojava.nbio.core.sequence.io.FastaWriterHelper;
import org.biojava.nbio.ontology.utils.SmallAnnotation;
import org.intermine.api.InterMineAPI;
import org.intermine.bio.web.biojava.BioSequence;
import org.intermine.bio.web.biojava.BioSequenceFactory;
import org.intermine.bio.web.biojava.BioSequenceFactory.SequenceType;
import org.intermine.bio.web.export.ResidueFieldExporter;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.BioEntity;
import org.intermine.model.bio.CDS;
import org.intermine.model.bio.GeneFamilyAssignment;
import org.intermine.model.bio.Protein;
import org.intermine.model.bio.Sequence;
import org.intermine.model.bio.SequenceFeature;
import org.intermine.model.bio.Transcript;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.metadata.TypeUtil;
import org.intermine.web.logic.Constants;
import org.intermine.web.logic.session.SessionMethods;
import org.intermine.web.struts.InterMineAction;

/**
 * Methods to provide a sequence, sequence type, and gene family for an annotation webapp.
 *
 * @author Sam Hokin
 */
public class SequenceAnnotateUtil {

    private static final String PROPERTY_DESCRIPTIONLINE = "description_line";

    private BioSequence bioSequence;
    private String method;
    private String identifier;
    private String objClass;
    private String sequenceType;
    private List<String> geneFamilyIdentifiers = new ArrayList<>();
    
    /**
     * Construct from an HttpServletRequest
     * 
     * @param request the HttpServletRequest
     * @param objectId the ID of the IntermineObject
     */
    public SequenceAnnotateUtil(HttpServletRequest request, Integer objectId) throws ObjectStoreException, IllegalAccessException, CompoundNotFoundException {
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();

        Properties webProps = (Properties) session.getServletContext().getAttribute(Constants.WEB_PROPERTIES);
        InterMineObject obj = getObject(os, webProps, objectId);

        // HACK: find the object class
        List<String> objStrings = new LinkedList<>(obj.getoBJECT().getStrings());
        objClass = objStrings.get(0).substring(27);

        // limit to CDS, Transcript/MRNA, Protein
        if (objClass.equals("CDS") || objClass.equals("Transcript") || objClass.equals("MRNA") || objClass.equals("Protein")) {
            if (objClass.equals("Protein")) {
                Protein protein = (Protein) obj;
                Set <GeneFamilyAssignment> gfas = protein.getGeneFamilyAssignments();
                for (GeneFamilyAssignment gfa : gfas) geneFamilyIdentifiers.add(gfa.getGeneFamily().getPrimaryIdentifier());
            } else if (objClass.equals("Transcript") || objClass.equals("MRNA")) {
                Transcript transcript = (Transcript) obj;
                Set <GeneFamilyAssignment> gfas = transcript.getGene().getGeneFamilyAssignments();
                for (GeneFamilyAssignment gfa : gfas) geneFamilyIdentifiers.add(gfa.getGeneFamily().getPrimaryIdentifier());
            } else if (objClass.equals("CDS")) {
                CDS cds = (CDS) obj;
                Set <GeneFamilyAssignment> gfas = cds.getGene().getGeneFamilyAssignments();
                for (GeneFamilyAssignment gfa : gfas) geneFamilyIdentifiers.add(gfa.getGeneFamily().getPrimaryIdentifier());
            }
            if (geneFamilyIdentifiers.size()>0) {
                bioSequence = createBioSequence(obj);
                if (bioSequence!=null) {
                    if (obj instanceof SequenceFeature) {
                        sequenceType = "n";
                    } else if (obj instanceof Protein) {
                        sequenceType = "p";
                    }
                    bioSequence.setAccession(new AccessionID((String) obj.getFieldValue("primaryIdentifier")));
                } else {
                    System.err.println("## bioSequence is null for object.");
                }
            }
        }
    }

    /**
     * Return the BioSequence (which may be null)
     */
    public BioSequence getBioSequence() {
        return bioSequence;
    }

    /**
     * Return the appropriate sequence type (which may be null)
     */
    public String getSequenceType() {
        return sequenceType;
    }

    /**
     * Return the sequence identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Return the gene family identifier for this object
     */
    public List<String> getGeneFamilyIdentifiers() {
        return geneFamilyIdentifiers;
    }

    /**
     * Create the BioSequence associated with the InterMineObject
     * @param obj the InterMineObject
     * @return the associated BioSequence
     */
    private BioSequence createBioSequence(InterMineObject obj) throws IllegalAccessException, CompoundNotFoundException {
        BioSequence bioSequence;
        BioEntity bioEntity = (BioEntity) obj;
        bioSequence = BioSequenceFactory.make(bioEntity, SequenceType.DNA);
        if (bioSequence == null) {
            return null;
        }
        SmallAnnotation annotation = bioSequence.getAnnotation();
        // try hard to find an identifier
        identifier = bioEntity.getPrimaryIdentifier();
        if (identifier == null) {
            identifier = bioEntity.getSecondaryIdentifier();
            if (identifier == null) {
                identifier = bioEntity.getName();
                if (identifier == null) {
                    try {
                        identifier = (String) bioEntity.getFieldValue("primaryAccession");
                    } catch (RuntimeException e) {
                        // ignore
                    }
                    if (identifier == null) {
                        identifier = "[no_identifier]";
                    }
                }
            }
        }
        annotation.setProperty(PROPERTY_DESCRIPTIONLINE, identifier);
        return bioSequence;
    }

    /**
     * Get the InterMineObject associated with the given objectId
     *
     * @param os the ObjectStore
     * @param webProps the web.properties
     * @param objectId the object ID 
     * @return the InterMineObject
    */
    private InterMineObject getObject(ObjectStore os, Properties webProps, Integer objectId) throws ObjectStoreException {
        String classNames = webProps.getProperty("fasta.export.classes");
        List<Class<?>> classList = new ArrayList<Class<?>>();
        if (classNames != null && classNames.length() != 0) {
            String [] classArray = classNames.split(",");
            for (int i = 0; i < classArray.length; i++) {
                classList.add(TypeUtil.instantiate(os.getModel().getPackageName() + "." + classArray[i]));
            }
        } else {
            classList.addAll(Arrays.asList(new Class<?>[] { Protein.class, SequenceFeature.class }));
        }
        InterMineObject obj = os.getObjectById(objectId);
        if (obj instanceof Sequence) {
            Sequence sequence = (Sequence) obj;
            for (Class<?> clazz : classList) {
                obj = ResidueFieldExporter.getIMObjectForSequence(os, clazz, sequence);
                if (obj != null) {
                    break;
                }
            }
        }
        return obj;
    }

    /**
     * Return the sequence FASTA for this object
     *
     * @return the FASTA string with header
     */
    public String getFasta() {
        return ">"+getIdentifier()+"\n"+getBioSequence().toString();
    }

    /**
     * Return the class of the InterMineObject (Gene, Transcript, MRNA, Protein, etc.)
     *
     * @return the object class
     */
    public String getObjClass() {
        return objClass;
    }
}
