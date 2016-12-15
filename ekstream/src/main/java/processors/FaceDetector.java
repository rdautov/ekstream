package processors;

import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_core.cvClearMemStorage;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
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
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.stream.io.ByteArrayInputStream;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.helper.opencv_core.AbstractCvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;

import utils.Utils;

/**
 * A NiFi processor, which takes as input video frames coming from a video camera,
 * detects human faces in each frames and crops these detected faces.
 */
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"ekstream", "video", "stream", "face", "detection", "crop"})
@CapabilityDescription("This processors takes as input video frames coming from a video camera, "
        + "detects human faces in each frames and crops these detected faces.")
public class FaceDetector extends EkstreamProcessor {

    /** Scale factor for face detection. */
    static final double SCALE_FACTOR = 1.5;

    /** Neighbors for face detection. */
    static final int MIN_NEIGHBOURS = 3;

    /** Processor property. */
    public static final PropertyDescriptor SAVE_IMAGES = new PropertyDescriptor.Builder()
            .name("Save images")
            .description("Specifies whether interim results should be saved.")
            .allowableValues(new HashSet<String>(Arrays.asList("true", "false")))
            .defaultValue("true")
            .required(true)
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    /** Processor property. */
    public static final PropertyDescriptor IMAGE_WIDTH = new PropertyDescriptor.Builder()
            .name("Image width")
            .description("Specifies the width of images with detected faces")
            .defaultValue("92")
            .required(true)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    /** Processor property. */
    public static final PropertyDescriptor IMAGE_HEIGHT = new PropertyDescriptor.Builder()
            .name("Image height")
            .description("Specifies the height of images with detected faces")
            .defaultValue("112")
            .required(true)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init(final ProcessorInitializationContext aContext) {

        super.init(aContext);

        final Set<Relationship> procRels = new HashSet<Relationship>();
        procRels.add(REL_SUCCESS);
        procRels.add(REL_FAILURE);
        relationships = Collections.unmodifiableSet(procRels);

        final List<PropertyDescriptor> supDescriptors = new ArrayList<>();
        supDescriptors.add(IMAGE_WIDTH);
        supDescriptors.add(IMAGE_HEIGHT);
        supDescriptors.add(SAVE_IMAGES);
        properties = Collections.unmodifiableList(supDescriptors);

        logger.info("Initialisation complete!");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTrigger(final ProcessContext aContext, final ProcessSession aSession)
            throws ProcessException {

        FlowFile flowFile = aSession.get();
        if (flowFile == null) {
            return;
        }

        aSession.read(flowFile, new InputStreamCallback() {

            @Override
            public void process(final InputStream aStream) throws IOException {

                ByteArrayInputStream inputStream = new ByteArrayInputStream(
                        IOUtils.toByteArray(aStream));

                BufferedImage bufferedImage = ImageIO.read(inputStream);
                IplImage image = Utils.getInstance().toIplImage(bufferedImage);

                opencv_imgcodecs.cvSaveImage(System.currentTimeMillis() + "-received.png", image);

                ArrayList<IplImage> faces = detect(image);
                if (!faces.isEmpty()) {
                    ArrayList<IplImage> resizedFaces = Utils.getInstance().resizeImages(faces,
                            Integer.parseInt(aContext.getProperty(IMAGE_WIDTH).getValue()),
                            Integer.parseInt(aContext.getProperty(IMAGE_HEIGHT).getValue()));

                    //now transfer the cropped images forward
                    for (IplImage face : resizedFaces) {

                        if (aContext.getProperty(SAVE_IMAGES).asBoolean()) {
                            opencv_imgcodecs.cvSaveImage(System.currentTimeMillis()
                                    + "-face.png", face);
                        }

                        FlowFile result = aSession.create(flowFile);
                        result = aSession.write(result, new OutputStreamCallback() {

                            @Override
                            public void process(final OutputStream aStream) throws IOException {
                                aStream.write(Utils.getInstance().toByteArray(face));
                            }
                        });
                        aSession.transfer(result, REL_SUCCESS);
                        //aSession.commit();
                    }
                }
            }
        });

        //TODO how to destroy the original flowfile?
        aSession.transfer(flowFile, REL_FAILURE);
        //aSession.commit();

    }

    /**
     * Detects faces in an input image.
     *
     * @param aImage input image
     * @return an array of detected faces as images
     */
    public static ArrayList<IplImage> detect(final IplImage aImage) {

        ArrayList<IplImage> result = new ArrayList<IplImage>();

        CvHaarClassifierCascade cascade =
                new CvHaarClassifierCascade(cvLoad("haarcascade_frontalface_default.xml"));
        CvMemStorage storage = AbstractCvMemStorage.create();
        CvSeq sign = cvHaarDetectObjects(aImage,
                cascade, storage, SCALE_FACTOR, MIN_NEIGHBOURS, CV_HAAR_DO_CANNY_PRUNING);

        for (int i = 0; i < sign.total(); i++) {
            CvRect r = new CvRect(cvGetSeqElem(sign, i));
            //opencv_imgproc.cvRectangle(aImage, cvPoint(r.x(), r.y()),
            //        cvPoint(r.width() + r.x(), r.height() + r.y()),
            //        AbstractCvScalar.RED, 2, LINE_AA, 0);

            IplImage image = Utils.getInstance().cropImage(aImage, r);
            result.add(image);
        }

        cvClearMemStorage(storage);
        return result;
    }

}
