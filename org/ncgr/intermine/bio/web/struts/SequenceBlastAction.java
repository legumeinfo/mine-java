package org.ncgr.intermine.bio.web.struts;

/*
 * Copyright (C) 2002-2016 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.AccessionID;
import org.biojava.nbio.core.sequence.io.FastaWriterHelper;
import org.biojava.nbio.ontology.utils.SmallAnnotation;

import org.intermine.api.InterMineAPI;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.BioEntity;
import org.intermine.model.bio.MRNA;
import org.intermine.model.bio.Protein;
import org.intermine.model.bio.Sequence;
import org.intermine.model.bio.SequenceFeature;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.metadata.TypeUtil;
import org.intermine.web.logic.Constants;
import org.intermine.web.logic.session.SessionMethods;
import org.intermine.web.struts.InterMineAction;

import org.intermine.bio.web.biojava.BioSequence;
import org.intermine.bio.web.biojava.BioSequenceFactory;
import org.intermine.bio.web.biojava.BioSequenceFactory.SequenceType;
import org.intermine.bio.web.export.ResidueFieldExporter;

/**
 * Exports sequence to be BLASTed by external service.
 *
 * @author Kim Rutherford
 * @author Sam Hokin
 */
public class SequenceBlastAction extends InterMineAction {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(SequenceBlastAction.class);

    private static final String PROPERTY_DESCRIPTIONLINE = "description_line";

    /**
     * This action is invoked directly to export SequenceFeatures.
     * @param mapping The ActionMapping used to select this instance
     * @param form The optional ActionForm bean for this request (if any)
     * @param request The HTTP request we are processing
     * @param response The HTTP response we are creating
     * @return an ActionForward object defining where control goes next
     * @exception Exception if the application business logic throws
     *  an exception
     */
    @Override
    public ActionForward execute(ActionMapping mapping,
            ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();
        BioSequence bioSequence = null;

        //SequenceHttpExporter.setSequenceBlastHeader(response);

        Properties webProps = (Properties) session.getServletContext(). getAttribute(Constants.WEB_PROPERTIES);
        Integer objectId = new Integer(request.getParameter("object"));
        InterMineObject obj = getObject(os, webProps, objectId);

        if (obj instanceof SequenceFeature || obj instanceof Protein) {
            
            bioSequence = createBioSequence(obj);
            response.setContentType("text/html");
            if (bioSequence!=null) {
                bioSequence.setAccession(new AccessionID((String)obj.getFieldValue("primaryIdentifier")));
                OutputStream stream = response.getOutputStream();
                PrintWriter out = new PrintWriter(stream);
                out.write("<html>");
                out.write("<head>");
                out.write("<title>Send sequence to LIS BLAST service</title>");
                out.write("</head>");
                out.write("<body>");
                out.write("<div style=\"width:550px; background-color:lightgray; margin:auto; padding:10px;\">");
                out.write("<div style=\"width:450px; margin:auto; text-align:center;\">");
                // the form
                if (obj instanceof Protein) {
                    out.write("<p>Send protein FASTA sequence to LIS BLAST</p>");
                    out.write("<form class=\"blast-choice-form\" enctype=\"multipart/form-data\" action=\"https://legumeinfo.org/blast/protein/protein\" method=\"post\" id=\"blast-ui-per-blast-program-form\" accept-charset=\"UTF-8\">");
                    out.write("<input name=\"blast_program\" value=\"blastp\" type=\"hidden\"/>");
                    out.write("<input name=\"query_type\" value=\"protein\" type=\"hidden\"/>");
                    out.write("<input name=\"db_type\" value=\"protein\" type=\"hidden\"/>");
                    out.write("<input name=\"form_build_id\" value=\"form-nbYZdNNzVdqbxBpGlg-fAvtXbYAMBSBcwOB60dmuZvc\" type=\"hidden\"/>");
                } else {
                    out.write("<p>Send nucleotide FASTA sequence to LIS BLAST</p>");
                    out.write("<form class=\"blast-choice-form\" enctype=\"multipart/form-data\" action=\"https://legumeinfo.org/blast/nucleotide/nucleotide\" method=\"post\" id=\"blast-ui-per-blast-program-form\" accept-charset=\"UTF-8\">");
                    out.write("<input name=\"blast_program\" value=\"blastn\" type=\"hidden\"/>");
                    out.write("<input name=\"query_type\" value=\"nucleotide\" type=\"hidden\"/>");
                    out.write("<input name=\"db_type\" value=\"nucleotide\" type=\"hidden\"/>");
                    out.write("<input name=\"form_build_id\" value=\"form-_msVVwjZPwuh1T2b1Jfd6T38yo7RGxy-P-RLsCKhLec\" type=\"hidden\"/>");
                }
                out.write("<textarea id=\"edit-fasta\" name=\"FASTA\" cols=\"60\" rows=\"50\">");
                out.flush();
                FastaWriterHelper.writeSequence(stream, bioSequence);
                stream.flush();
                out.write("</textarea>");
                out.write("<div>");
                out.write("<input id=\"edit-submit\" name=\"op\" value=\" BLAST \" class=\"form-submit\" type=\"submit\"/>");
                out.write("</div>");
                out.write("<input name=\"form_id\" value=\"blast_ui_per_blast_program_form\" type=\"hidden\"/>");
                out.write("</form>");
                out.write("</div>");
                out.write("</div>");
                out.write("</body>");
                out.write("</html>");
                out.flush();
            } else {
                PrintWriter out = response.getWriter();
                out.write("Sequence information not available for this sequence feature...");
                out.flush();
            }

	} else {
            
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.write("<html>");
            out.write("<head>");
            out.write("<title>Cannot send sequence to LIS BLAST service</title>");
            out.write("</head>");
            out.write("<body>");
            out.write("<h1>SequenceBlastAction called for object which is neither SequenceFeature nor Protein.</h1>");
            out.write("</body>");
            out.write("</html>");
            out.flush();
            
        }

	return null;

    }

    private BioSequence createBioSequence(InterMineObject obj)
	throws IllegalAccessException, CompoundNotFoundException {

        BioSequence bioSequence;
        BioEntity bioEntity = (BioEntity) obj;
        if (obj instanceof Protein) {
            bioSequence = BioSequenceFactory.make(bioEntity, SequenceType.PROTEIN);
        } else {
            bioSequence = BioSequenceFactory.make(bioEntity, SequenceType.DNA);
        }
        if (bioSequence == null) {
            return null;
        }
        SmallAnnotation annotation = bioSequence.getAnnotation();
        // try hard to find an identifier
        String identifier = bioEntity.getPrimaryIdentifier();
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

    private InterMineObject getObject(ObjectStore os, Properties webProps, Integer objectId) throws ObjectStoreException {
        String classNames = webProps.getProperty("fasta.export.classes");
        List<Class<?>> classList = new ArrayList<Class<?>>();
        if (classNames != null && classNames.length() != 0) {
            String [] classArray = classNames.split(",");
            for (int i = 0; i < classArray.length; i++) {
                classList.add(TypeUtil.getTypeByName(os.getModel().getPackageName() + "."
                                                   + classArray[i]));
            }
        } else {
            classList.addAll(Arrays.asList(new Class<?>[] {
                        Protein.class,
                            SequenceFeature.class
            }));
        }

        InterMineObject obj = os.getObjectById(objectId);
        if (obj instanceof Sequence) {
            Sequence sequence = (Sequence) obj;
            for (Class<?> clazz : classList) {
                obj = ResidueFieldExporter.getIMObjectForSequence(os, clazz,
                                                                  sequence);
                if (obj != null) {
                    break;
                }
            }
        }
        return obj;
    }
}
