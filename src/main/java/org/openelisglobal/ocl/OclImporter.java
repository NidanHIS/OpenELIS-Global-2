package org.openelisglobal.ocl;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * The import entry point, separated from {@link OclConfigurationHandler} so it
 * survives proxying.
 *
 * <p>
 * {@code OclConfigurationHandler} is {@code @Transactional}, so Spring wraps it
 * in a JDK dynamic proxy (it already implements
 * {@code DomainConfigurationHandler}). That proxy implements the bean's
 * interfaces but is not an instance of the concrete class, so anything
 * autowiring {@code OclConfigurationHandler} by its concrete type fails context
 * refresh with {@code BeanNotOfRequiredTypeException}. Declaring the method on
 * an interface lets collaborators inject that instead.
 */
public interface OclImporter {

    void performImport(List<JsonNode> oclNodes);
}
