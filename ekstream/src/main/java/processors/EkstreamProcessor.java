package processors;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.presets.opencv_objdetect;
import org.bytedeco.javacv.Frame;

import utils.Utils;

/**
 * A super-class for custom Ekstream NiFi processors.
 */
public class EkstreamProcessor extends AbstractProcessor {

    /** Processor property. */
    public static final PropertyDescriptor SAVE_IMAGES = new PropertyDescriptor.Builder()
            .name("Save images")
            .description("Specifies whether interim results should be saved.")
            .allowableValues(new HashSet<String>(Arrays.asList("true", "false")))
            .defaultValue("true")
            .required(true)
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    /** Destination folder propserty. */
    public static final PropertyDescriptor BENCHMARKING_DIR = new PropertyDescriptor.Builder()
            .name("Destination folder.")
            .description("Specified the folder where to save benchmarking results.")
            .defaultValue("/opt/nifi-1.0.1/")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    /** Relationship "Success". */
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("This is where flow files are sent if the processor execution went well.")
            .build();

    /** Relationship "Failure".*/
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("This is where flow files are sent if something went wrong.")
            .build();

    /** List of processor properties. */
    private List<PropertyDescriptor> properties;

    /** List of processor relationships. */
    private Set<Relationship> relationships;

    /** Save interim results or not. */
    private Boolean isSaveResults;

    /** File writer for benchmarking logs. */
    private static BufferedWriter bWriter;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init(final ProcessorInitializationContext context) {

        Loader.load(opencv_objdetect.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTrigger(final ProcessContext aContext, final ProcessSession aSession)
            throws ProcessException {

        if (null == isSaveResults) {
            isSaveResults = aContext.getProperty(SAVE_IMAGES).asBoolean();
            getLogger().info("Saving interim results: " + isSaveResults);
        }

        if (null == bWriter) {
            try {
                bWriter = new BufferedWriter(new FileWriter(aContext.getProperty(BENCHMARKING_DIR)
                        .getValue() + aContext.getName() + "-" + getIdentifier(), true));
                getLogger().info("Saving benchmarking results to: " + bWriter.toString());
            } catch (IOException e) {
                getLogger().error("Could not open the file for writing!", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    /**
     * Setter.
     *
     * @param aRelationships relationships
     */
    public void setRelationships(final Set<Relationship> aRelationships) {
        relationships = aRelationships;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    /**
     * Getter.
     *
     * @return properties
     */
    public List<PropertyDescriptor> getProperties() {
        return properties;
    }


    /**
     * Setter.
     *
     * @param aProperties properties
     */
    public void setProperties(final List<PropertyDescriptor> aProperties) {
        properties = aProperties;
    }

    /**
     * Returns the buffered writer to log benchmarking results.
     *
     * @return buffered writer
     */
    public BufferedWriter getWriter() {
        return bWriter;
    }

    /**
     * Saves interim image processing results.
     *
     * @param aImage image file to be saved
     * @param aPath destination path
     */
    public void saveInterimResults(final String aPath, final IplImage aImage) {
        if (isSaveResults) {
            opencv_imgcodecs.cvSaveImage(aPath, aImage);
        }
    }



    /**
     * Saves interim image processing results.
     *
     * @param aFrame frame to be saved
     * @param aPath destination path
     */
    public void saveInterimResults(final String aPath, final Frame aFrame) {
        if (isSaveResults) {
            opencv_imgcodecs.cvSaveImage(aPath, Utils.getInstance().convertToImage(aFrame));
        }
    }

    /**
     * Logs a single benchmarking line to file.
     *
     * @param aId Flow file ID
     */
    public void benchmark(final String aId) {
        try {
            bWriter.write(aId + ";"
                    + System.currentTimeMillis());
            bWriter.write("\n");
            bWriter.flush();
        } catch (IOException e) {
            getLogger().error("Could not write to file!", e);
        }

    }





}
