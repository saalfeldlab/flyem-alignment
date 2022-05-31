package hemibrain;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.janelia.saalfeldlab.n5.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.ij.N5Factory;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.imglib2.RandomAccessibleLoader;
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5GenericSingleScaleMetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata;

import bdv.img.RenamableSource;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import ij.ImagePlus;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealImgAndInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.view.Views;
import sc.fiji.io.Dfield_Nrrd_Reader;
import util.RenderUtil;


public class DifferentHemibrainSpaces
{

//	public static final ByteType BYTE = new ByteType();
//	public static final ShortType SHORT = new ShortType();
//	public static final IntType INT = new IntType();
//	public static final LongType LONG = new LongType();
//	public static final FloatType FLOAT = new FloatType();
//	public static final DoubleType DOUBLE = new DoubleType();
	

	public static void main( String[] args ) throws Exception
	{
		final SharedQueue queue = new SharedQueue( 8 );
		/*
		 * setup hemi brain
		 */
		Source< ? > hemiPix = loadHemiN5Pix( queue );

		/*
		 * Show 
		 */
		BdvOptions options = BdvOptions.options().numRenderingThreads( 16 );
		RealPoint resultPt = new RealPoint( 3 );
		
		// show hemibrain n5 pixel 
//		BdvStackSource< ? > bdv = BdvFunctions.show( hemiPix );
		RealPoint n5PixPt = new RealPoint( 24775.0,  16580.0, 8838.0 );

		RealPoint dvidPixPt = new RealPoint(25589.0, 24775.0, 16580.0);

		RealPoint jrc18faffinePt  = new RealPoint( 258.0, 19.4, 102.0 ); // mushroom body
		RealPoint renderSpacePt = new RealPoint( 204.712, 198.2, 132.64 );

		// show hemibrain n5 microns
//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, n5PixelsToMicrons()) );

//		// show hemibrain to dvid
//		AffineTransform3D xfm = n5ToDvid();
//		System.out.println( xfm );
//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, xfm ) );
////		xfm.apply( n5PixPt, resultPt );
////		System.out.println( resultPt );


//		// show hemibrain to dvid physical
//		AffineTransform3D xfm = n5ToDvid();
//		xfm.preConcatenate( emPixelsToMicrons() );
//		System.out.println( xfm );
//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, xfm ) );


//		// show hemibrain to dvid physical
//		AffineTransform3D xfm = n5ToDvid();
//		xfm.preConcatenate( emPixelsToMicrons() );
//		// here we're in dvid micron space
//		xfm.preConcatenate( emPixelsToMicrons().inverse() );
//		xfm.preConcatenate( n5ToDvid().inverse() );
//		xfm.preConcatenate( n5ToRenderSpaceMicronsReal());
//		System.out.println( xfm );
//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, xfm ) );
//
//		AffineTransform3D dvidToRenderSpace = new AffineTransform3D();
//		dvidToRenderSpace.preConcatenate( emPixelsToMicrons().inverse() );
//		dvidToRenderSpace.preConcatenate( n5ToDvid().inverse() );
//		dvidToRenderSpace.preConcatenate( n5ToRenderSpaceMicronsReal());
//		System.out.println( "dvidToRenderSpace:" );
//		System.out.println( dvidToRenderSpace );
		
		
		// hemibrain to dvd and to (weird) render space
//		AffineTransform3D xfm = new AffineTransform3D();
//		xfm.preConcatenate( n5ToDvid() );
//		xfm.preConcatenate( preAffine() );
//		xfm.preConcatenate( renderToWeirdRender() );

		// hemibrain to dvd and to (good) render space
//		AffineTransform3D xfm = new AffineTransform3D();
//		xfm.preConcatenate( n5ToRenderSpaceMicronsReal());
//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, xfm ));
		

//
//		xfm.applyInverse( resultPt, dvidPixPt );
//		System.out.println( resultPt );

//		// show hemibrain to regspace
//		System.out.println( " hemi regspace " );
//		AffineTransform3D xfm = n5ToRenderSpaceMicrons();
//		AffineTransform3D xfm = n5ToRenderSpaceMicronsReal();
//		AffineTransform3D xfm = n5ToRenderSpaceWeirdMicrons();
//

//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, xfm ));


//		// hemibrain to dvd and to (good) render space
//		AffineTransform3D xfm = new AffineTransform3D();
//		xfm.preConcatenate( n5ToRenderSpaceMicronsReal());
//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, xfm ));
		

////		String hemiPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/tbar_render_resliceReverse_hdr_d4.nrrd";
//		//String hemiPath = "/groups/saalfeld/public/flyem_tbars/tbar_render_20190304_reslice.nrrd";
//		String hemiPath = "/groups/saalfeld/public/flyem_tbars/tbar_render_20190304.nrrd";
//
//
//		System.out.println("loadding nrrd: " + hemiPath );
//		RealImgAndInterval< FloatType > hemi = loadNrrd( hemiPath, null );
//		System.out.println("done");
//		
//		BdvFunctions.show( hemi.get(), "hemi synapses", BdvOptions.options().addTo( bdv ));


//		xfm.apply( n5PixPt, resultPt );
//		System.out.println( resultPt );


//		// affine to jrc18
//		AffineTransform3D xfm = regSpaceToJRC18Ants();
//
//		// this means that the forward model goes from 
//		// jrc18f-affineants to render-space
//		xfm.applyInverse( resultPt, renderSpacePt );
//		System.out.println( resultPt );

//		xfm.apply( jrc18faffinePt, resultPt );
//		System.out.println( resultPt );



		// show hemibrain to regspace pixel
//		System.out.println( " hemi regspace pix" );
//		BdvStackSource< ? > bdv = BdvFunctions.show( affineSource( hemiPix, n5ToFlippedRegSpacePixel() ) );


		// warped hemibrain and fafb
//		AffineTransform3D id = new AffineTransform3D();
//		WarpedSource< ? > hemiWarped = warpSource( hemiSource, loadFlyem_to_FAFB(), id );
//
//		BdvStackSource< ? > bdv = BdvFunctions.show( hemiWarped );
//		options = options.addTo( bdv ); 

		
//		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 2 ).setRange( 0, 1500 );
//		bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( 2 ).setColor( 
//				new ARGBType( ARGBType.rgba( 1, 0, 1, 1 )));
//	
//		double[] xfmparams = new double[]{ 
//				0.8412054094940138, 0.0, 0.0, -435.7652173492281,
//				0.0, 0.8412054094940138, 0.0, -234.6835520769816,
//				0.0, 0.0, 0.8412054094940138, -116.3932851575259
//		};
//
//		AffineTransform3D viewerTransform = new AffineTransform3D();
//		viewerTransform.set( xfmparams );
//		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( viewerTransform );
		
		
		AffineTransform3D n5ToRegSpace = DifferentHemibrainSpaces.n5ToRenderSpaceMicronsReal();


		Source hemiEmSrc = new RenamableSource(
				affineSource( hemiPix, n5ToRegSpace ),
				"hemibrain EM" );
		
	
		BdvStackSource bdv = BdvFunctions.show( hemiEmSrc );
	}
	
	protected static final String scalesKey = "scales";
	protected static final String downsamplingFactorsKey = "downsamplingFactors";

	public static double[][] getScales( N5Reader n5Reader ) throws IOException
	{
		// check the root scales attribute
		// if it is not there, try the downsamplingFactors attribute for every dataset under the channel group
		double[][] scales = n5Reader.getAttribute( "/", scalesKey, double[][].class );

		if ( scales == null )
		{
			final int numScales = n5Reader.list( "" ).length;
			scales = new double[ numScales ][];
			for ( int scale = 0; scale < numScales; ++scale )
			{
				String scaleStringPath = String.format("s%d", scale );
				double[] downsamplingFactors = n5Reader.getAttribute( scaleStringPath, downsamplingFactorsKey, double[].class );
				if ( downsamplingFactors == null )
				{
					if ( scale == 0 )
					{
						downsamplingFactors = new double[ n5Reader.getDatasetAttributes( scaleStringPath ).getNumDimensions() ];
						Arrays.fill( downsamplingFactors, 1 );
					}
					else
					{
						throw new IllegalArgumentException( "downsamplingFactors are not specified for some datasets" );
					}
				}
				scales[ scale ] = downsamplingFactors;
			}
		}

		return scales;
	}

	public static String getScaleGroupPath( final int scale )
	{
		return String.format( "s%d", scale );
	} 


	public static Source<?> getRandomAccessibleIntervalMipmapSourceV(
			final N5Reader n5, final String dataset, final String name, final SharedQueue queue ) throws IOException
	{
		return getRandomAccessibleIntervalMipmapSourceV( n5, dataset, name, queue, true );
	}

	public static Source<?> getRandomAccessibleIntervalMipmapSourceV(
			final N5Reader n5, final String dataset, final String name, final SharedQueue queue, boolean relativeScales ) throws IOException
	{
		N5GenericSingleScaleMetadataParser metaParser = new N5GenericSingleScaleMetadataParser(
				"min", "max",
				"resolution", "offset", "unit",
				"downsamplingFactors");

		N5DatasetDiscoverer disc = new N5DatasetDiscoverer( n5, Collections.singletonList(metaParser), Arrays.asList( N5Importer.GROUP_PARSERS));
		N5TreeNode node = disc.discoverAndParseRecursive("/");
		N5Metadata meta = node.getMetadata();
		MultiscaleMetadata<N5SingleScaleMetadata> ms = (MultiscaleMetadata)meta;
		
//		N5ExportMetadataReader metadata = N5ExportMetadata.openForReading( n5 );
//		final double[][] scales = getScales( n5 );
//		final RandomAccessibleInterval< ? >[] scaleLevelImgs = new RandomAccessibleInterval[ scales.length ];
//		for ( int s = 0; s < scales.length; ++s )
//			scaleLevelImgs[ s ] = N5Utils.openVolatile( n5, getScaleGroupPath( s ) );
		

		int N = ms.getPaths().length;
		double[][] scales = new double[N][];
		final RandomAccessibleInterval< ? >[] scaleLevelImgs = new RandomAccessibleInterval[ scales.length ];
		int i = 0;
		for ( N5SingleScaleMetadata scaleMeta : ms.getChildrenMetadata() )
		{
			N5Utils.openVolatile( n5, scaleMeta.getPath() );
//			scales[i] = scaleMeta.getPixelResolution();
			scales[i] = scaleMeta.getDownsamplingFactors();
			System.out.println( String.format( "s%d", i ) + " " + Arrays.toString(scales[i]));
			i++;
		}

		for ( int s = 0; s < scales.length; ++s )
		{
			RandomAccessibleInterval<?> rai = N5Utils.openVolatile( n5, getScaleGroupPath( s ) );
			scaleLevelImgs[ s ] = VolatileViews.wrapAsVolatile( rai, queue, 
					new CacheHints(LoadingStrategy.VOLATILE, 0, true));
		}
		

		final RandomAccessibleIntervalMipmapSource< ? > source = 
				new RandomAccessibleIntervalMipmapSource(
				scaleLevelImgs,
				new VolatileUnsignedByteType(),
				scales,
				new FinalVoxelDimensions("nm", 1, 1, 1 ),
				name );

		return source;	
	}
	
	public static Source< ? > loadHemiN5Pix( final SharedQueue queue ) throws IOException
	{
		return loadHemiN5Pix( "/nrs/flyem/data/tmp/Z0115-22.export.n5/22-34", queue );
	}

	public static Source< ? > loadHemiN5Pix( final String hemiN5Path, final SharedQueue queue ) throws IOException
	{
		
		N5Reader n5hemi = new N5FSReader( hemiN5Path );
		//Source<?> sourceRaw = getRandomAccessibleIntervalMipmapSource( n5hemi, "hemibrain" );
		Source<?> sourceRaw = getRandomAccessibleIntervalMipmapSourceV( n5hemi, "hemibrain", "hemibrain", queue );
		return sourceRaw;
	}
	
	public static <T> Source<T> affineSource( Source<T> src, AffineTransform3D transform )
	{
		TransformedSource< T > transformedSource = new TransformedSource<>( src );
		transformedSource.setIncrementalTransform( transform );
		return transformedSource;
	}

	public static AffineTransform3D renderToWeirdRender()
	{
		double scale = 0.256 / 0.1875;
		AffineTransform3D xfm = new AffineTransform3D();
		xfm.scale( scale );
		
		AffineTransform3D xlate = new AffineTransform3D();
		xlate.set( (51*0.1875), 2, 3 );
		xfm.preConcatenate( xlate );

		return xfm.inverse();
	}

	public static AffineTransform3D preAffine()
	{
		final AffineTransform3D xfm = new AffineTransform3D();
		xfm.set(
				0.005859, 0.000000, -0.000000, -0.000000,
				0.000000, 0.000000, 0.005859, -0.000000,
				0.000000, -0.005859, 0.000000, 232.763558 );
		return xfm;
	}
	
//	public static AffineTransform3D dvidPhysicalToReslice()
//	{
//		final AffineTransform3D xfm = new AffineTransform3D();
//		
//		// dvid - > n5 -> renderSpaceMicronsReal
//		xfm.preConcatenate( emPixelsToMicrons() ); // dv0d
//
//		return xfm;
//	}

	public static AffineTransform3D emPixelsToMicrons()
	{
		final AffineTransform3D toMicrons = new AffineTransform3D();
		toMicrons.set(
				0.008, 0.0, 0.0, 0.0, 
				0.0, 0.008, 0.0, 0.0,
				0.0, 0.0, 0.008, 0.0 );
		return toMicrons;
	}
	
	public static AffineTransform3D regSpaceToJRC18Ants()
	{
		final AffineTransform3D toJRC18AntsAffine = new AffineTransform3D();
//		toJRC18AntsAffine.set(
//				1.16506, -0.0346056, -0.0380523, 144.4,
//				-0.0752483, 0.896258, 0.273381, -21.0038,
//				-0.0309502, -0.246563, 0.839065, 51.1882);
		
		toJRC18AntsAffine.set(
			1.176400, 0.055155, 0.035380, -170.524751,
			-0.066460, -0.413941, -1.495354, 395.246954,
			0.078497, 1.401740, -0.453149, 41.302815 );
		return toJRC18AntsAffine;
	}

	public static AffineTransform3D n5ToDvid()
	{
		final AffineTransform3D toDvid = new AffineTransform3D();
		toDvid.set(
				0.0, 0.0, -1.0, 34427, 
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );
		return toDvid;
	}

	/**
	 * 
	 * Need to add a scaling transform that goes from the correct microns
	 * 
	 * to the wrong scaling that this volume has:
	 * /groups/saalfeld/public/flyem_hemiBrainAlign/tbar_render_resliceReverse_hdr.nrrd
	 * 
	 * because that wrong volume was used in registration...boo
	 * 
	 */
	public static AffineTransform3D n5ToRenderSpaceWeirdMicrons()
	{

		double scale = 0.256 / 0.1875;
//		Scale3D scaleXfm = new Scale3D( scale, scale, scale );

		AffineTransform3D xfm = new AffineTransform3D();
		xfm.scale( scale );
		//xfm.set( -(51*0.1875), 2, 3 );

		AffineTransform3D result = n5ToRenderSpaceMicronsReal();
		result.preConcatenate( xfm );

		System.out.println( "transform: " + result );
		return result;

	}

	public static AffineTransform3D n5ToRenderSpaceMicronsReal()
	{
		final AffineTransform3D toMicrons = new AffineTransform3D();
		toMicrons.set(
				0.008, 0.0, 0.0, 0.0, 
				0.0, 0.008, 0.0, 0.0,
				0.0, 0.0, 0.008, 0.0 );

		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D toreg = new AffineTransform3D();
		toreg.set(
				 0.0, 0.0, -1.0, nz, 
				 0.0, 1.0, 0.0, 0.0,
				-1.0, 0.0, 0.0, nx );
		
		AffineTransform3D result = toreg.inverse().preConcatenate( toMicrons );
		System.out.println( "transform: " + result );
		return result;

	}

	public static AffineTransform3D n5ToRenderSpaceMicrons()
	{
		final AffineTransform3D toMicrons = new AffineTransform3D();
		toMicrons.set(
				0.008, 0.0, 0.0, 0.0, 
				0.0, 0.008, 0.0, 0.0,
				0.0, 0.0, 0.008, 0.0 );

		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D toreg = new AffineTransform3D();
		toreg.set(
				 0.0, 1.0, 0.0, 0.0,
				 0.0, 0.0, 1.0, 0.0,
				-1.0, 0.0, 0.0, nx );
		
		AffineTransform3D result = toreg.inverse().preConcatenate( toMicrons );
		System.out.println( "transform: " + result );
		return result;
	}

	public static AffineTransform3D n5ToFlippedRegSpacePixel()
	{
		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D toreg = new AffineTransform3D();
		toreg.set(
				 0.0, 1.0, 0.0, 0.0,
				 0.0, 0.0, 1.0, 0.0,
				-1.0, 0.0, 0.0, nx );

		return toreg.inverse();
	}

	public static Source< ? > loadHemi( final SharedQueue queue ) throws IOException
	{

//		final AffineTransform3D toFlyEm = new AffineTransform3D();
//		toFlyEm.set(
//				0.0, 0.0, -1.0, 34427, 
//				1.0, 0.0, 0.0, 0.0,
//				0.0, 1.0, 0.0, 0.0 );
//

		//double res = 0.008;
		double res = 0.005859375; // incorrect but what was used for the bridge :(

		AffineTransform3D hemi_toMicrons = new AffineTransform3D();
		hemi_toMicrons.set( res, 0, 0 );
		hemi_toMicrons.set( res, 1, 1 );
		hemi_toMicrons.set( res, 2, 2 );

		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D flip = new AffineTransform3D();
		flip.set(
				0.0, 0.0, -1.0, nx * res,
				0.0, 1.0, 0.0, 0.0,
				-1.0, 0.0, 0.0, ny * res);


//		String flipPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/cmtk_a/toFlyem/toflyem.mat";
//		AffineTransform3D flip = ANTSLoadAffine.loadAffine( flipPath );
//		System.out.println("flipXfm: " + flip );
		
		N5Reader n5hemi = new N5FSReader( "/nrs/flyem/data/tmp/Z0115-22.export.n5/22-34" );
		//Source<?> sourceRaw = getRandomAccessibleIntervalMipmapSource( n5hemi, "hemibrain" );
		Source<?> sourceRaw = getRandomAccessibleIntervalMipmapSourceV( n5hemi, "hemibrain", "hemibrain", queue );

		AffineTransform3D total = hemi_toMicrons.copy();
		total.preConcatenate( flip );
		//total.concatenate( flip.inverse() );

		TransformedSource< ? > hemiSourceXfm = new TransformedSource<>( sourceRaw );
		hemiSourceXfm.setIncrementalTransform( total );
		
		return hemiSourceXfm;
	}
	
	public static Source< UnsignedByteType > loadHemiOLD( boolean flyEmSpace ) throws IOException
	{
		final AffineTransform3D toFlyEm = new AffineTransform3D();
		toFlyEm.set(
				0.0, 0.0, -1.0, 34427,
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );


		AffineTransform3D hemi_tonm = new AffineTransform3D();
		hemi_tonm.set( 8, 0, 0 );
		hemi_tonm.set( 8, 1, 1 );
		hemi_tonm.set( 8, 2, 2 );

		
		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D flip = new AffineTransform3D();
		flip.set(
				0.0, 0.0, -1.0, nx,
				0.0, 1.0, 0.0, 0.0,
				0.0, 0.0, -1.0, nz );


//		String flipPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/cmtk_a/toFlyem/toflyem.mat";
//		AffineTransform3D flip = ANTSLoadAffine.loadAffine( flipPath );
//		System.out.println("flipXfm: " + flip );


		/* 
		 * setup hemi brain
		 */
		N5Reader n5hemi = new N5FSReader( "/nrs/flyem/data/tmp/Z0115-22.export.n5" );
		
		return null;

	}
	
	public static RealImgAndInterval< FloatType > loadNrrd( String path, InvertibleRealTransform xfm )
	{
		Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
		File hFile = new File( path );
		ImagePlus ip = nr.load( hFile.getParent(), hFile.getName());

		
		double rx = ip.getCalibration().pixelWidth;
		double ry = ip.getCalibration().pixelHeight;
		double rz = ip.getCalibration().pixelDepth;
		
		AffineTransform3D resInXfm = new AffineTransform3D();
		resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
				  		0.0, ry, 0.0, 0.0, 
				  		0.0, 0.0, rz, 0.0 );


		Img< FloatType > a = ImageJFunctions.wrapFloat( ip );
		RealRandomAccessible< FloatType > rra = null;
		if( xfm == null )
		{
			rra = RealViews.affine( 
					Views.interpolate( 
						Views.extendZero( a ), 
						new NLinearInterpolatorFactory<FloatType>() ),
					resInXfm );
		}
		else
		{
			InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
			totalXfm.add( resInXfm );
			totalXfm.add( xfm );

			rra = RealViews.transform( 
					Views.interpolate( 
						Views.extendZero( a ), 
						new NLinearInterpolatorFactory<FloatType>() ),
					totalXfm );
		}
		
		FinalInterval interval = RenderUtil.transformInterval( resInXfm, a );
		return new RealImgAndInterval<FloatType>( interval, rra );
	}

}
