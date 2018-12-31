package beanmine;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

import org.intermine.metadata.Model;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.webservice.client.core.ServiceFactory;
import org.intermine.webservice.client.services.QueryService;

/**
 * This is a Java program to run a query from BeanMine.
 * It was automatically generated at Thu Dec 13 13:04:28 MST 2018
 *
 * @author BeanMine
 *
 */
public class QueryClient
{
    private static final String ROOT = "http://shokin-webapps/beanmine/service";

    /**
     * Perform the query and print the rows of results.
     * @param args command line arguments
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        ServiceFactory factory = new ServiceFactory(ROOT);
        Model model = factory.getModel();
        PathQuery query = new PathQuery(model);

        // Select the output columns:
        query.addView("Gene.primaryIdentifier");

        // Add orderby
        query.addOrderBy("Gene.primaryIdentifier", OrderDirection.ASC);

        // Filter the results with the following constraints:
        query.addConstraint(Constraints.eq("Gene.primaryIdentifier", "Phvul.001G000100"));

        QueryService service = factory.getQueryService();
        PrintStream out = System.out;
        out.println("Gene.primaryIdentifier");
        Iterator<List<Object>> rows = service.getRowListIterator(query);
        while (rows.hasNext()) {
            out.println(rows.next().get(0));
        }
        out.printf("%d rows\n", service.getCount(query));
    }

}

