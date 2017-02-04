package processors;

import static org.bytedeco.javacpp.opencv_core.CV_32SC1;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.bytedeco.javacpp.opencv_face;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import org.bytedeco.javacv.Frame;

import utils.Utils;

/**
 * A NiFi processor, which takes as input video frames coming from a video camera,
 * detects human faces in each frames and crops these detected faces.
 */
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"ekstream", "face", "recognition"})
@CapabilityDescription("This processor takes as input video frames with detected human faces,"
        + "and recognises these faces.")
public class RecogniseFaces extends EkstreamProcessor {

    /** Allowable value. */
    public static final AllowableValue FISHER = new AllowableValue("Fisher",
            "Fisher Face Recognition", "Face recognition using the Fisher algorithm.");

    /** Allowable value. */
    public static final AllowableValue EIGEN = new AllowableValue("Eigen",
            "Eigen Face Recognition", "Face recognition using the Eigen algorithm.");

    /** Allowable value. */
    public static final AllowableValue LBPH = new AllowableValue("LBPH",
            "LBPH Face Recognition", "Face recognition using the LBPH algorithm.");

    /** Processor property. */
    public static final PropertyDescriptor TRAINING_SET = new PropertyDescriptor.Builder()
            .name("Folder with training images.")
            .description("Specifies the folder where trainaing images are located.")
            .defaultValue("/opt/nifi-1.0.1/training/")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    /** Processor property. */
    public static final PropertyDescriptor FACE_RECOGNIZER = new PropertyDescriptor.Builder()
            .name("Face recognition algorithm.")
            .description("Specified the Face recognition algorithm to be applied.")
            .allowableValues(FISHER, EIGEN, LBPH)
            .defaultValue(FISHER.getValue())
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    /** Face recognizer. */
    private static FaceRecognizer faceRecognizer;

    /** Integer 10000. */
    private static final int INT_10000 = 10000;

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
        supDescriptors.add(TRAINING_SET);
        supDescriptors.add(BENCHMARKING_DIR);
        supDescriptors.add(FACE_RECOGNIZER);
        supDescriptors.add(SAVE_IMAGES);
        setProperties(Collections.unmodifiableList(supDescriptors));

        getLogger().info("Initialision complete!");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTrigger(final ProcessContext aContext, final ProcessSession aSession)
            throws ProcessException {

        super.onTrigger(aContext, aSession);

        if (null == faceRecognizer) {
            train(aContext.getProperty(TRAINING_SET).getValue(),
                    aContext.getProperty(FACE_RECOGNIZER).getValue());
        }

        FlowFile flowFile = aSession.get();
        if (flowFile == null) {
            return;
        }

        aSession.read(flowFile, new InputStreamCallback() {

            @Override
            public void process(final InputStream aStream) throws IOException {

                BufferedImage bufferedImage = ImageIO.read(aStream);
                Frame frame = Utils.getInstance().convertToFrame(bufferedImage);

                saveInterimResults(System.currentTimeMillis() + "-received_face.png",
                        frame);

                Mat face = Utils.getInstance().convertToMat(frame);
                face = Utils.getInstance().convertToGrayscale(face);

                int[] plabel = new int[1];
                double[] pconfidence = new double[1];

                faceRecognizer.predict(face, plabel, pconfidence);

                //benchmarking====================================
                benchmark(flowFile.getAttribute(CoreAttributes.UUID.key()));
                //=================================================

                getLogger().info("Predicted label: " + plabel[0]
                        + " , confidence: " + pconfidence[0]);
                if (pconfidence[0] > INT_10000) {
                    getLogger().warn("ONE OF THE FACES HAS BEEN RECOGNISED!");
                }
            }
        });

        aSession.transfer(flowFile, REL_SUCCESS);

    }

    /**
     * Trains the face recognizer.
     *
     * @param aTrainingDir directory with training images
     * @param aAlgorithm face recognition algorithm
     */
    public static void train(final String aTrainingDir, final String aAlgorithm) {

        File root = new File(aTrainingDir);

        FilenameFilter imgFilter = new FilenameFilter() {
            @Override
            public boolean accept(final File aDir, final String aName) {
                String name = aName.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };

        File[] imageFiles = root.listFiles(imgFilter);

        MatVector images = new MatVector(imageFiles.length);

        Mat labels = new Mat(imageFiles.length, 1, CV_32SC1);
        IntBuffer labelsBuf = labels.createBuffer();

        for (int i = 0; i < imageFiles.length; i++) {

            Mat img = opencv_imgcodecs.imread(imageFiles[i].getAbsolutePath(),
                    opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);

            int label = Integer.parseInt(imageFiles[i].getName().split("\\-")[0]);
            images.put(i, img);
            labelsBuf.put(i, label);
        }

        switch (aAlgorithm) {
        case "Fisher":
            faceRecognizer = opencv_face.createFisherFaceRecognizer();
        case "Eigen":
            faceRecognizer = opencv_face.createEigenFaceRecognizer();
        case "LBPH":
            faceRecognizer = opencv_face.createLBPHFaceRecognizer();
        default:
            faceRecognizer = opencv_face.createFisherFaceRecognizer();
        }

        faceRecognizer.train(images, labels);
    }
}