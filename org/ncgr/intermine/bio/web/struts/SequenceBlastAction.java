package org.ncgr.intermine.bio.web.struts;

import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
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
import org.intermine.model.bio.Protein;
import org.intermine.model.bio.Sequence;
import org.intermine.model.bio.SequenceFeature;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.metadata.TypeUtil;
import org.intermine.web.logic.Constants;
import org.intermine.web.logic.session.SessionMethods;
import org.intermine.web.struts.InterMineAction;

/**
 * Creates a form to export sequence to BLAST search.
 *
 * @author Kim Rutherford
 * @author Sam Hokin
 */
public class SequenceBlastAction extends InterMineAction {
    // private static final Logger LOG = Logger.getLogger(SequenceBlastAction.class);
    private static final String PROPERTY_DESCRIPTIONLINE = "description_line";

    /**
     * This action is invoked directly to export SequenceFeatures.
     * @param mapping The ActionMapping used to select this instance
     * @param form The optional ActionForm bean for this request (if any)
     * @param request The HTTP request we are processing
     * @param response The HTTP response we are creating
     * @return an ActionForward object defining where control goes next
     * @exception Exception if the application business logic throws an exception
     */
    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        ObjectStore os = im.getObjectStore();
        BioSequence bioSequence = null;

        Properties webProps = (Properties) session.getServletContext().getAttribute(Constants.WEB_PROPERTIES);
        Integer objectId = new Integer(request.getParameter("object"));
        InterMineObject obj = getObject(os, webProps, objectId);

        if (obj instanceof SequenceFeature || obj instanceof Protein) {
            String method = "blastn";
            if (obj instanceof Protein) method = "blastp";
            bioSequence = createBioSequence(obj);
            response.setContentType("text/html");
            // bail if we don't have a sequence
            if (bioSequence==null) {
                PrintWriter pw = response.getWriter();
                pw.write("<html>" +
                         "<head>" +
                         "<title>Sequence info not available</title>" +
                         "</head>" +
                         "<body>" +
                         "Sequence information not available for this object." +
                         "</body>" +
                         "</html>");
                pw.flush();
                return null;
            }
            OutputStream out = response.getOutputStream();
            PrintWriter pw  = new PrintWriter(out);
            bioSequence.setAccession(new AccessionID((String) obj.getFieldValue("primaryIdentifier")));
            pw.write("<html>" +
                     "<head>" +
                     "<title>BLAST this sequence on SequenceServer</title>" +
                     "</head>");
            pw.write("<body>" +
                     "<form id=\"blast\" method=\"post\" action=\"https://legumeinfo.org/sequenceserver/\">");
            pw.write("<div style=\"margin:10px\">" +
                     "<h2>BLAST this sequence on SequenceServer</h2>" +
                     "<textarea name=\"sequence\" style=\"width:470px;height:500px\">");
            pw.flush();
            //
            FastaWriterHelper.writeSequence(out, bioSequence);
            //
            pw.write("</textarea>" +
                     "</div>");
            pw.write("<div style=\"margin:10px\">" +
                     "<h2>Advanced Parameters:</h2>" +
                     "<input type=\"text\" size=\"100\" name=\"advanced\" value=\"\" placeholder=\"eg: -evalue 1.0e-5 -num_alignments 100\"/>" +
                     "</div>");
            pw.flush();
            pw.write("<div style=\"margin:10px\">");
            pw.write("<h2>Nucleotide databases (uncheck those you don't want)</h2>");
            pw.write("<input type=\"checkbox\" name=\"databases[]\" value=\"3a453cd5d59742ea295ff3ee32834be9\" checked> Arachis duranensis - genome<br/>" +
                     "<input type=\"checkbox\" name=\"databases[]\" value=\"5f7c8ef461e69707283f7368e5f83a11\" checked> Arachis duranensis - CDS<br/>");
            pw.write("</div>");
            pw.flush();
            pw.write("<div style=\"margin:10px\">");
            pw.write("<button type=\"submit\" name=\"method\" value=\""+method+"\">BLAST</button>");
            pw.write("</div>");
            pw.write("</form>");
            pw.write("</body>");
            pw.write("</html>");
            pw.flush();
        }
        return null;
    }

    private BioSequence createBioSequence(InterMineObject obj) throws IllegalAccessException, CompoundNotFoundException {
        BioSequence bioSequence;
        BioEntity bioEntity = (BioEntity) obj;
        bioSequence = BioSequenceFactory.make(bioEntity, SequenceType.DNA);
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
}
