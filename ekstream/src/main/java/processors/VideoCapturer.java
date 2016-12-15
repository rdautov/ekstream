package processors;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.TriggerWhenEmpty;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameGrabber.Exception;

import utils.Utils;

/**
 * A NiFi processor which accesses the default video camera, captures the video stream,
 * samples it into separate frames, and transfers forward for face recognition.
 */
@TriggerWhenEmpty
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@Tags({"ekstream", "video", "stream", "capturing", "sampling"})
@CapabilityDescription("Testing JavaCV api")
public class VideoCapturer extends EkstreamProcessor {

    /** Processor property. */
    public static final PropertyDescriptor FRAME_INTERVAL = new PropertyDescriptor.Builder()
            .name("Time interval between frames")
            .description("Specified the time interval between two captured video frames, in ms")
            .defaultValue("1000")
            .required(true)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    /** Processor property. */
    public static final PropertyDescriptor SAVE_IMAGES = new PropertyDescriptor.Builder()
            .name("Save images")
            .description("Specifies whether interim results should be saved.")
            .allowableValues(new HashSet<String>(Arrays.asList("true", "false")))
            .defaultValue("true")
            .required(true)
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    /** JavaCV frame grabber. */
    private static FrameGrabber grabber;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init(final ProcessorInitializationContext aContext) {

        super.init(aContext);

        final Set<Relationship> procRels = new HashSet<>();
        procRels.add(REL_SUCCESS);
        relationships = Collections.unmodifiableSet(procRels);

        final List<PropertyDescriptor> supDescriptors = new ArrayList<>();
        supDescriptors.add(FRAME_INTERVAL);
        supDescriptors.add(SAVE_IMAGES);
        properties = Collections.unmodifiableList(supDescriptors);

        try {
            grabber = FrameGrabber.createDefault(0);
        } catch (Exception e) {
            logger.error("Something went wrong with the video capture!", e);
        }

        logger.info("Initialision complete!");
    }

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

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTrigger(final ProcessContext aContext, final ProcessSession aSession)
            throws ProcessException {

        try {

            grabber.start();

            Frame frame = grabber.grab();

            byte[] result = Utils.getInstance().toByteArray(frame);

            if (aContext.getProperty(SAVE_IMAGES).asBoolean()) {
                opencv_imgcodecs.cvSaveImage(System.currentTimeMillis() + "-captured.png",
                        Utils.getInstance().convert(frame));
            }

            //transfer the image
            FlowFile flowFile = aSession.create();
            flowFile = aSession.write(flowFile, new OutputStreamCallback() {

                @Override
                public void process(final OutputStream aStream) throws IOException {

                    aStream.write(result);
                }
            });
            aSession.transfer(flowFile, REL_SUCCESS);
            aSession.commit();

            Thread.currentThread();
            Thread.sleep(Integer.parseInt(aContext.getProperty(FRAME_INTERVAL).getValue()));

            grabber.stop();

        } catch (Exception e) {
            logger.error("Something went wrong with the video capture!", e);
        } catch (InterruptedException e) {
            logger.error("Something went wrong with the threads!", e);
        } catch (IOException e) {
            logger.error("Something went wrong with saving the file!", e);
        }

    }

}
