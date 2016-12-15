package processors;

import java.util.List;
import java.util.Set;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.presets.opencv_objdetect;

/**
 * A super-class for custom Ekstream NiFi processors.
 */
public class EkstreamProcessor extends AbstractProcessor {

    /** Relationship "Success". */
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("This is where flow files are sent if the processor execution wehnt well.")
            .build();

    /** Relationship "Failure".*/
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("This is where flow files are sent if something went wrong.")
            .build();

    /** List of processor properties. */
    protected List<PropertyDescriptor> properties;

    /** List of processor relationships. */
    protected Set<Relationship> relationships;

    /** Logger. */
    protected ComponentLog logger;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init(final ProcessorInitializationContext context) {

        Loader.load(opencv_objdetect.class);
        logger = getLogger();

        //logger.info("Initialisation complete!");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTrigger(final ProcessContext aContext, final ProcessSession aSession)
            throws ProcessException { }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

}
