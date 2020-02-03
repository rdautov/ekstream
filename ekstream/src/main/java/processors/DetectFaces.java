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
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.stream.io.ByteArrayInputStream;
import org.bytedeco.javacpp.helper.opencv_core.AbstractCvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;

import utils.Utils;

/**
 * A NiFi processor, which takes as input video frames coming from a video
 * camera, detects human faces in each frames and crops these detected faces.
 */
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({ "ekstream", "video", "stream", "face", "detection", "crop" })
@CapabilityDescription("This processors takes as input video frames coming from a video camera, "
		+ "detects human faces in each frames and crops these detected faces.")
public class DetectFaces extends EkstreamProcessor {

	/** Scale factor for face detection. */
	static final double SCALE_FACTOR = 1.5;

	/** Neighbors for face detection. */
	static final int MIN_NEIGHBOURS = 3;

	/** Processor property. */
	public static final PropertyDescriptor IMAGE_WIDTH = new PropertyDescriptor.Builder().name("Image width")
			.description("Specifies the width of images with detected faces").defaultValue("92").required(true)
			.addValidator(StandardValidators.INTEGER_VALIDATOR).build();

	/** Processor property. */
	public static final PropertyDescriptor IMAGE_HEIGHT = new PropertyDescriptor.Builder().name("Image height")
			.description("Specifies the height of images with detected faces").defaultValue("112").required(true)
			.addValidator(StandardValidators.INTEGER_VALIDATOR).build();

	/** Processor property. */
	public static final PropertyDescriptor CASCADE_FILE = new PropertyDescriptor.Builder().name("Cascade file.")
			.description("Specifies the cascade file to be used for face recognition.")
			.defaultValue("/home/orkes/Desktop/haarcascade_frontalface_default.xml").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	/** Destination folder propserty. */
	public static final PropertyDescriptor DESTINATION_DIR = new PropertyDescriptor.Builder()
			.name("Destination folder.").description("Specified the folder where to save benchmarking results.")
			.defaultValue("/opt/nifi-1.0.1/").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.build();

	/** Cascade file. */
	private static CvHaarClassifierCascade cascade;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void init(final ProcessorInitializationContext aContext) {

		super.init(aContext);

		final Set<Relationship> procRels = new HashSet<Relationship>();
		procRels.add(REL_SUCCESS);
		setRelationships(Collections.unmodifiableSet(procRels));

		final List<PropertyDescriptor> supDescriptors = new ArrayList<>();
		supDescriptors.add(IMAGE_WIDTH);
		supDescriptors.add(IMAGE_HEIGHT);
		supDescriptors.add(SAVE_IMAGES);
		supDescriptors.add(DESTINATION_DIR);
		supDescriptors.add(CASCADE_FILE);
		setProperties(Collections.unmodifiableList(supDescriptors));

		getLogger().info("Initialisation complete!");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onTrigger(final ProcessContext aContext, final ProcessSession aSession) throws ProcessException {

		super.onTrigger(aContext, aSession);

		if (null == cascade) {
			cascade = new CvHaarClassifierCascade(cvLoad(aContext.getProperty(CASCADE_FILE).getValue()));
			getLogger().info("Loaded the cascade file: " + cascade.toString());
		}

		FlowFile flowFile = aSession.get();
		if (null == flowFile) {
			return;
		}

		getLogger().info("=================RECEIVED NEW FLOWFILE=================");

		aSession.read(flowFile, new InputStreamCallback() {

			@Override
			public void process(final InputStream aStream) throws IOException {

				ByteArrayInputStream inputStream = new ByteArrayInputStream(IOUtils.toByteArray(aStream));

				BufferedImage bufferedImage = ImageIO.read(inputStream);
				IplImage image = Utils.getInstance().convertToImage(bufferedImage);

				opencv_imgcodecs.cvSaveImage(System.currentTimeMillis() + "-received.png", image);

				ArrayList<IplImage> faces = detect(image);

				getLogger().info("================= DETECTED FACES: " + faces.size());

				if (!faces.isEmpty()) {
					ArrayList<IplImage> resizedFaces = Utils.getInstance().resizeImages(faces,
							Integer.parseInt(aContext.getProperty(IMAGE_WIDTH).getValue()),
							Integer.parseInt(aContext.getProperty(IMAGE_HEIGHT).getValue()));

					for (IplImage face : resizedFaces) {

						FlowFile result = aSession.create(flowFile);
						result = aSession.putAttribute(result, "parent",
								flowFile.getAttribute(CoreAttributes.UUID.key()));
						result = aSession.write(result, new OutputStreamCallback() {

							@Override
							public void process(final OutputStream aStream) throws IOException {
								aStream.write(Utils.getInstance().convertToByteArray(face));
							}
						});

						// benchmarking=====================================
						result = aSession.putAttribute(result, "detect", String.valueOf(System.currentTimeMillis()));
						benchmark(flowFile.getAttribute(CoreAttributes.UUID.key()));
						// ==
						aSession.transfer(result, REL_SUCCESS);
					}
				}
			}
		});

		aSession.remove(flowFile);
		aSession.commit();
		getLogger().info("=================SESSION COMMITED==================");

		// aSession.transfer(flowFile, REL_FAILURE);
	}

	/**
	 * Detects faces in an input image.
	 *
	 * @param aImage input image
	 * @return an array of detected faces as images
	 */
	public static ArrayList<IplImage> detect(final IplImage aImage) {

		ArrayList<IplImage> result = new ArrayList<IplImage>();

		CvMemStorage storage = AbstractCvMemStorage.create();
		CvSeq sign = cvHaarDetectObjects(aImage, cascade, storage, SCALE_FACTOR, MIN_NEIGHBOURS,
				CV_HAAR_DO_CANNY_PRUNING);

		for (int i = 0; i < sign.total(); i++) {
			CvRect r = new CvRect(cvGetSeqElem(sign, i));
			IplImage image = Utils.getInstance().cropImage(aImage, r);
			result.add(image);
		}

		cvClearMemStorage(storage);
		return result;
	}

}