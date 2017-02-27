package processors;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
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
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
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
public class CaptureVideo extends EkstreamProcessor {

    /** Processor property. */
    public static final PropertyDescriptor FRAME_WIDTH = new PropertyDescriptor.Builder()
            .name("Image width")
            .description("Specifies the width of of captured frames")
            .defaultValue("640")
            .required(true)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    /** Processor property. */
    public static final PropertyDescriptor FRAME_HEIGHT = new PropertyDescriptor.Builder()
            .name("Image height")
            .description("Specifies the height of captured frames")
            .defaultValue("480")
            .required(true)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    /** Processor property. */
    public static final PropertyDescriptor FRAME_INTERVAL = new PropertyDescriptor.Builder()
            .name("Time interval between frames")
            .description("Specified the time interval between two captured video frames, in ms")
            .defaultValue("1000")
            .required(true)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
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
        setRelationships(Collections.unmodifiableSet(procRels));

        final List<PropertyDescriptor> supDescriptors = new ArrayList<>();
        supDescriptors.add(FRAME_INTERVAL);
        supDescriptors.add(FRAME_WIDTH);
        supDescriptors.add(FRAME_HEIGHT);
        supDescriptors.add(SAVE_IMAGES);
        supDescriptors.add(BENCHMARKING_DIR);
        setProperties(Collections.unmodifiableList(supDescriptors));

        try {
            grabber = FrameGrabber.createDefault(0);
        } catch (Exception e) {
            getLogger().error("Something went wrong with the video grabber initialisation!", e);
        }

        getLogger().info("Initialision complete!");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTrigger(final ProcessContext aContext, final ProcessSession aSession)
            throws ProcessException {

        super.onTrigger(aContext, aSession);

        try {

            grabber.start();

            Frame frame = grabber.grab();

            //TODO maybe send iplimage??
            byte[] result = Utils.getInstance().convertToByteArray(frame);

            saveInterimResults(System.currentTimeMillis() + "-captured.png", frame);

            //transfer the image
            FlowFile flowFile = aSession.create();
            flowFile = aSession.write(flowFile, new OutputStreamCallback() {

                @Override
                public void process(final OutputStream aStream) throws IOException {

                    aStream.write(result);
                }
            });

            //benchmarking=====================================
            flowFile = aSession.putAttribute(flowFile, "capture",
                    String.valueOf(System.currentTimeMillis()));
            benchmark(flowFile.getAttribute(CoreAttributes.UUID.key()));
            //=================================================

            aSession.transfer(flowFile, REL_SUCCESS);
            aSession.commit();

            Thread.currentThread();
            Thread.sleep(Integer.parseInt(aContext.getProperty(FRAME_INTERVAL).getValue()));

            grabber.stop();

        } catch (Exception e) {
            getLogger().error("Something went wrong with the video capture!", e);
        } catch (InterruptedException e) {
            getLogger().error("Something went wrong with the threads!", e);
        } catch (IOException e) {
            getLogger().error("Something went wrong with saving the file!", e);
        }

    }

}
