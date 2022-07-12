package org.janelia.maleBrain;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.janelia.render.KDTreeRendererRaw;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.export.ProgressWriterConsole;
import bdv.gui.BigWarpViewerOptions;
import bdv.tools.transformation.TransformedSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import bigwarp.BigWarpInit;
import bigwarp.BigWarpSwc;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import bigwarp.BigWarp;
import bigwarp.BigWarp.BigWarpData;
import io.IOHelper;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Util;
import picocli.CommandLine.Option;

public class BigWarpMaleBrain implements Callable<Void>
{
	
	@Option( names = { "-e", "--em-raw" }, required = false)
	String emRawN5 = "/nrs/flyem/render/n5/Z0720_07m_BR";

	String emRawDataset = "/40-06-final";

	@Option( names = { "-e", "--em" }, required = true, description = "EM N5 root path" )
	String emClaheN5 = "/nrs/flyem/render/n5/Z0720_07m_BR/40-06-dvid-coords-clahe-from-jpeg.n5";

	@Option( names = { "-d", "--em-dataset" }, required = true, description = "EM N5 dataset" )
	String emClaheDataset = "/grayscale";

	@Option( names = { "-t", "--template" }, required = true, description = "Path to JRC2018M file" )
	String templatePath = "/groups/saalfeld/public/jrc2018/JRC2018_MALE_40x.nrrd";

	@Option( names = { "-s", "--synapses" }, required = true, description = "Path to synapse cloud file" )
	String synapsesPath = "/nrs/saalfeld/john/flyem_maleBrain/tbars/render_tbars_512nm.nrrd";

	String roisPath = "/nrs/saalfeld/john/flyem_maleBrain/lopRoi_ed_xfm_RGB.tif";

	String templateClaheSpacePath = "/nrs/saalfeld/john/flyem_maleBrain/regexps/exp0029_t9_0029.sh_20220330165846/result_0029/JRC2018M_xfm_v2.nrrd";

	@Option( names = { "-l", "--landmarks" }, required = true, description = "Path to landmarks (csv)" )
	String landmarksPath = "/nrs/saalfeld/john/flyem_maleBrain/finetune2/landmarks.csv";
//	String landmarksPath = "/nrs/saalfeld/john/flyem_maleBrain/finetune/landmarks.csv";

	@Option( names = { "--settings" }, required = true, description = "Path to landmarks (csv)" )
	String settingsPath;

	String defaultsettingsPath = "/nrs/saalfeld/john/flyem_maleBrain/finetune/bigwarp.settings.xml";

	public BigWarpMaleBrain() { }

	public static void main( String[] args ) throws IOException, SpimDataException
	{
		System.out.println( "bigwarp male brain ");
		new BigWarpMaleBrain().call();
	}

	public Void call() throws IOException, SpimDataException
	{
		final SharedQueue queue = new SharedQueue( 24 );
		final Source emSrc = loadImages(
				emClaheN5, emClaheDataset,
				claheTransform(), new FinalVoxelDimensions("nm", 8, 8, 8 ), 
				true, queue );

		final Source jrc18Src = loadImgAsSource( templateClaheSpacePath, true );
		final Source roisSrc = loadImgAsSourceRGB( roisPath, true );

		final Source tbarSrc  = loadImgAsSource( synapsesPath, true );
		

		BigWarpData< ? > bigwarpdata = BigWarpInit.initData();

		int id = 0;
		BigWarpInit.add( bigwarpdata, jrc18Src, id++, 0, true );
		BigWarpInit.add( bigwarpdata, roisSrc, id++, 0, true );

		BigWarpInit.add( bigwarpdata, tbarSrc, id++, 0, false );
		BigWarpInit.add( bigwarpdata, emSrc, id++, 0, false );
		bigwarpdata.wrapUp();
		
		ImageJ ij = new ImageJ();
		BigWarpViewerOptions bwopts = BigWarpViewerOptions.options();
		bwopts.numRenderingThreads(48);
		final BigWarpSwc bw = new BigWarpSwc( bigwarpdata, "Big Warp", bwopts, new ProgressWriterConsole());

		bw.loadLandmarks(landmarksPath);

//		try {
//			bw.loadSettings( settingsPath );
//		} catch (JDOMException e) {
//			e.printStackTrace();
//		}

		return null;
	}

	public AffineTransform3D rawTransform()
	{
		final AffineTransform3D fwd = new AffineTransform3D();
		fwd.set(0.0, 0.0, 1.0, 1772, 
				0.0, 1.0, 0.0, -2304, 
				1.0, 0.0, 0.0, 768 );

		final AffineTransform3D xfm = fwd.inverse();
		xfm.preConcatenate( new Scale3D( 8, 8, 8 ));
		return xfm;
	}

	public Scale3D umToNm() {
		return new Scale3D( 1000, 1000, 1000 );
	}
	
	public AffineTransform3D claheTransform()
	{
		final AffineTransform3D xfm = new AffineTransform3D();
		xfm.preConcatenate( new Scale3D( 8, 8, 8 ));
		return xfm;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T extends RealType<T> & NativeType<T>> Source<T> loadImgAsSource(
			String imagePath,
			boolean toNm) {

		final IOHelper io = new IOHelper();
		final RandomAccessibleInterval<T> img = io.readRai(imagePath);
		T type = Util.getTypeFromInterval(img);

		final Scale3D res = new Scale3D(io.getResolution());
		if( toNm )
			res.preConcatenate( umToNm() );

		AffineTransform3D xfm = new AffineTransform3D();
		xfm.preConcatenate(res);

		final RandomAccessibleIntervalSource src = new RandomAccessibleIntervalSource<>(img, type, xfm, new File(imagePath).getName());
		return src;
	}
	
	public Source<ARGBType> loadImgAsSourceRGB(
			String imagePath,
			boolean toNm) {

		final ImagePlus imp = IJ.openImage(imagePath);
		final Img<ARGBType> img = ImageJFunctions.wrapRGBA(imp);
		final ARGBType type = new ARGBType();

		final Scale3D res = new Scale3D( 
			imp.getCalibration().pixelWidth,
			imp.getCalibration().pixelHeight,
			imp.getCalibration().pixelDepth);

		if( toNm )
			res.preConcatenate( umToNm() );

		AffineTransform3D xfm = new AffineTransform3D();
		xfm.preConcatenate(res);

		final RandomAccessibleIntervalSource src = new RandomAccessibleIntervalSource<>(img, type, xfm, new File(imagePath).getName());
		return src;
	}
	
	public static Source<?> loadImages( 
			String n5Path,
			String datasetName,
			AffineTransform3D transform,
			VoxelDimensions voxelDimensions,
			boolean useVolatile,
			SharedQueue queue) 
					throws IOException
	{
		if( n5Path == null || n5Path.isEmpty() || datasetName == null || datasetName.isEmpty() )
		{
			System.err.println( "problem loading n5 images: " );
			System.err.println( "  n5root: " + n5Path );
			System.err.println( "  datasetName: " + datasetName );
			return null;
		}

		final N5Reader n5 = new N5FSReader(n5Path);
			
		final int numScales = n5.list(datasetName).length;

		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<UnsignedByteType>[] mipmaps = (RandomAccessibleInterval<UnsignedByteType>[])new RandomAccessibleInterval[numScales];
		final double[][] scales = new double[numScales][];

		for (int s = 0; s < numScales; ++s) {

			final int scale = 1 << s;
			final double inverseScale = 1.0 / scale;

			final RandomAccessibleInterval<UnsignedByteType> source = N5Utils.open(n5, datasetName + "/s" + s);
			
			System.out.println("s " + s );	
			System.out.println( Util.printInterval( source )  + "\n" );

			final RealTransformSequence transformSequence = new RealTransformSequence();
			final Scale3D scale3D = new Scale3D(inverseScale, inverseScale, inverseScale);
			transformSequence.add(scale3D);

			final RandomAccessibleInterval<UnsignedByteType> cachedSource = KDTreeRendererRaw.wrapAsVolatileCachedCellImg(source, new int[]{64, 64, 64});

			mipmaps[s] = cachedSource;
			scales[s] = new double[]{scale, scale, scale};
		}

		final RandomAccessibleIntervalMipmapSource<?> mipmapSource =
				new RandomAccessibleIntervalMipmapSource<>(
						mipmaps,
						new UnsignedByteType(),
						scales,
						voxelDimensions,
						datasetName);

		final Source<?> volatileMipmapSource;
		if (useVolatile)
			volatileMipmapSource = mipmapSource.asVolatile(queue);
		else
			volatileMipmapSource = mipmapSource;
		
		Source<?> source2render = volatileMipmapSource;
		if( transform != null  )
		{
			TransformedSource<?> transformedSource = new TransformedSource<>(volatileMipmapSource);
			transformedSource.setFixedTransform( transform );
			source2render = transformedSource;
		}
		return source2render;
	}
	

}